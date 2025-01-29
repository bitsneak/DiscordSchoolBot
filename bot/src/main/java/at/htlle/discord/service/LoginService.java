package at.htlle.discord.service;

import at.htlle.discord.jpa.entity.*;
import at.htlle.discord.jpa.repository.*;
import at.htlle.discord.model.enums.*;
import at.htlle.discord.model.VerificationClient;
import at.htlle.discord.util.DiscordUtil;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BiConsumer;

@Service
public class LoginService {
    private static final Logger logger = LogManager.getLogger(LoginService.class);

    private static final String EMAIL_STUDENT_REGEX = "^[a-zA-Z0-9._%+-]+@o365\\.htl-leoben\\.at$";

    private static final String EMAIL_TEACHER_REGEX = "^[a-z]+@o365\\.htl-leoben\\.at$";

    // for development - EMAIL_STUDENT_REGEX: gmail email; EMAIL_TEACHER_REGEX: student school email
    //private static final String EMAIL_STUDENT_REGEX = "^[a-zA-Z0-9._%+-]+@gmail\\.com$";
    //private static final String EMAIL_TEACHER_REGEX = "^[a-zA-Z0-9._%+-]+@o365\\.htl-leoben\\.at$";

    @Autowired
    private DiscordUtil discordUtil;

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private MailService mailService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private EnrolmentRepository enrolmentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private ProfessionRepository professionRepository;

    @Autowired
    private ScholarRepository scholarRepository;

    public boolean handleEmailInput(VerificationClient verificationClient, String email) {
        if (!email.matches(EMAIL_STUDENT_REGEX) && !email.matches(EMAIL_TEACHER_REGEX)) {
            discordUtil.sendPrivateMessage(verificationClient.getUser(), "Please enter a valid school email address.");
            return false;
        }

        String verificationCode = verificationService.generateVerificationCode(verificationClient);
        mailService.sendVerificationEmail(email, verificationCode);
        verificationClient.getClient().setEmail(email);

        if (email.matches(EMAIL_STUDENT_REGEX)) {
            // find or create student scholar and assign it
            scholarRepository.findByName(Scholars.STUDENT).ifPresentOrElse(
                    s -> {
                        verificationClient.getClient().setScholar(s);
                    },
                    () -> {
                        Scholar scholar = scholarRepository.save(new Scholar(Scholars.STUDENT));
                        verificationClient.getClient().setScholar(scholar);
                    }
            );
        } else {
            // find or create teacher scholar and assign it
            scholarRepository.findByName(Scholars.TEACHER).ifPresentOrElse(
                    s -> {
                        verificationClient.getClient().setScholar(s);
                    },
                    () -> {
                        Scholar scholar = scholarRepository.save(new Scholar(Scholars.TEACHER));
                        verificationClient.getClient().setScholar(scholar);
                    }
            );
        }

        discordUtil.sendPrivateMessage(verificationClient.getUser(), "A verification code has been sent to your email. Please enter the code.");
        logger.info("Verification email sent to user: {}", verificationClient.getUser().getId());

        return true;
    }

    public boolean handleCodeInput(VerificationClient verificationClient, String code) {
        Boolean isCodeValid = verificationService.verifyCode(verificationClient, code);

        if (isCodeValid == null) {
            discordUtil.sendPrivateMessage(verificationClient.getUser(), "Your verification code has expired.");
            handleEmailInput(verificationClient, verificationClient.getClient().getEmail());
            return false;
        }

        if (isCodeValid) {
            discordUtil.sendPrivateMessage(verificationClient.getUser(), "Please enter your profession. One out of " + Arrays.toString(Professions.values()));
            logger.info("Code found for user: {}. Entered email is correct.", verificationClient.getUser().getId());
            return true;
        }

        discordUtil.sendPrivateMessage(verificationClient.getUser(), "Invalid code. Try again.");
        return false;
    }

    public boolean handleProfessionInput(VerificationClient verificationClient, String profession) {
        if (Arrays.stream(Professions.values()).anyMatch(p -> p.getName().equalsIgnoreCase(profession))) {
            // get enum profession from profession name
            Professions professionEnum = Professions.valueOf(profession.toUpperCase());
            Client client = verificationClient.getClient();

            // find or create profession
            professionRepository.findByName(professionEnum).ifPresentOrElse(
                    client::setProfession,
                    () -> {
                        Profession professionEntity = new Profession(professionEnum);
                        professionRepository.save(professionEntity);
                        client.setProfession(professionEntity);
                    }
            );

            logger.info("Profession found for user: {}", verificationClient.getUser().getId());
            return true;
        }

        discordUtil.sendPrivateMessage(verificationClient.getUser(), "Invalid profession. Please enter one out of " + Arrays.toString(Professions.values()));
        return false;
    }

    public boolean handleTeacherInput(VerificationClient verificationClient, String abbreviation) {
        // check if the teacher abbreviation matches any teacher
        Optional<Teacher> teacherOptional = teacherRepository.findByAbbreviation(abbreviation.toUpperCase());

        if (teacherOptional.isPresent()) {
            // get teacher from optional
            Teacher teacher = teacherOptional.get();

            // get enrolment by teacher
            Optional<Enrolment> enrolmentOptional = enrolmentRepository.findByClassTeacher(teacher);
            // no enrolment found for this class teacher
            if (enrolmentOptional.isEmpty()) {
                discordUtil.sendPrivateMessage(verificationClient.getUser(), "No class registered for this teacher.");
                discordUtil.sendPrivateMessage(verificationClient.getUser(), "Please enter your class teacher abbreviation.");
                logger.info("No class found for teacher: {}", teacher.getAbbreviation());
                return false;
            }

            // if enrolment is found, set it on the client
            Enrolment enrolment = enrolmentOptional.get();
            verificationClient.getClient().setEnrolment(enrolment);
            logger.info("Class found for user: {}", verificationClient.getUser().getId());
            return true;
        }

        discordUtil.sendPrivateMessage(verificationClient.getUser(), "Invalid or not supported class teacher abbreviation. Try again.");
        return false;
    }

    public void assignRoles(VerificationClient verificationClient) {
        User user = verificationClient.getUser();
        Client client = verificationClient.getClient();
        Guild guild = verificationClient.getGuild();
        Member member = guild.retrieveMember(user).complete();

        // discord colors
        Color itProffesionColor = Color.decode("#f0c50f");
        Color lProffesionColor = Color.decode("#9a59b7");
        Color mProffesionColor = Color.decode("#3599db");
        Color rProffesionColor = Color.decode("#2ecc71");
        Color genericRoleColor = Color.decode("#94a5a7");
        Color classRoleColor = Color.decode("#e64c3d");

        // Helper method to assign a role by name
        BiConsumer<String, Color> createAndAssignRole = (roleName, color) -> {
            Optional<Role> existingRole = guild.getRolesByName(roleName, true).stream().findFirst();

            if (existingRole.isPresent()) {
                // If the role exists, assign it
                guild.addRoleToMember(member, existingRole.get()).queue(
                        success -> logger.info("Successfully assigned existing role {} to user {}", roleName, user.getId()),
                        error -> logger.error("Failed to assign existing role {} to user {}. Error: {}", roleName, user.getId(), error.getMessage())
                );
            } else {
                // If the role doesn't exist, create it and then assign it
                guild.createRole()
                        .setName(roleName)
                        .setColor(color)
                        .setMentionable(true)
                        .setPermissions(Permission.MESSAGE_HISTORY, Permission.MESSAGE_SEND, Permission.MESSAGE_ADD_REACTION, Permission.VIEW_CHANNEL)
                        .queue(
                                role -> {
                                    guild.addRoleToMember(member, role).queue(
                                            success -> logger.info("Created and assigned new role {} to user {}", roleName, user.getId()),
                                            error -> logger.error("Failed to assign new role {} to user {}. Error: {}", roleName, user.getId(), error.getMessage())
                                    );
                                },
                                error -> logger.error("Failed to create role {}. Error: {}", roleName, error.getMessage())
                        );
            }
        };

        // assign client profession to user role
        Professions profession = client.getProfession().getName();
        switch (profession) {
            case IT -> createAndAssignRole.accept(profession.getName(), itProffesionColor);
            case L -> createAndAssignRole.accept(profession.getName(), lProffesionColor);
            case M -> createAndAssignRole.accept(profession.getName(), mProffesionColor);
            case R -> createAndAssignRole.accept(profession.getName(), rProffesionColor);
        }

        // select if teacher or student
        if (client.getScholar().getName().equals(Scholars.STUDENT)) {
            // find and assign student role
            Scholar role = scholarRepository.findByName(Scholars.STUDENT).stream().findFirst().get();
            createAndAssignRole.accept(role.getName().getName(), genericRoleColor);

            // assign client year to user role
            createAndAssignRole.accept("Jahrgang " + client.getEnrolment().getYear().getYear().getYear(), genericRoleColor);
            // assign client className to user role
            createAndAssignRole.accept(client.getEnrolment().getName(), classRoleColor);
        } else {
            // find and assign teacher role
            Scholar role = scholarRepository.findByName(Scholars.TEACHER).stream().findFirst().get();
            createAndAssignRole.accept(role.getName().getName(), genericRoleColor);
        }
    }

    public void persistClient(VerificationClient verificationClient) {
        Client client = verificationClient.getClient();
        User user = verificationClient.getUser();

        clientRepository.findByDiscordId(user.getId()).ifPresentOrElse(
                existingClient -> {
                    existingClient.setEmail(client.getEmail());
                    existingClient.setProfession(client.getProfession());
                    existingClient.setScholar(client.getScholar());
                    existingClient.setJoinedAt(LocalDateTime.now(ZoneOffset.UTC));

                    if (existingClient.getLeftAt() != null) {
                        existingClient.setLeftAt(null);
                    }

                    if (existingClient.getScholar().getName().equals(Scholars.STUDENT)) {
                        existingClient.setEnrolment(client.getEnrolment());
                    }
                    else {
                        existingClient.setEnrolment(null);
                    }

                    clientRepository.save(existingClient);
                },
                () -> {
                    client.setDiscordId(user.getId());
                    client.setDiscordName(user.getName());
                    client.setJoinedAt(LocalDateTime.now(ZoneOffset.UTC));
                    clientRepository.save(client);
                }
        );
    }
}
