package at.htlle.discord.service;

import at.htlle.discord.jpa.entity.*;
import at.htlle.discord.jpa.repository.*;
import at.htlle.discord.model.enums.*;
import at.htlle.discord.model.VerificationClient;
import at.htlle.discord.util.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;

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
        verificationClient.getClient().setDiscordName(verificationClient.getUser().getName());

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
            Optional<Enrolment> enrolmentOptional = enrolmentRepository.findByTeacher(teacher);
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

        // assign client profession to user role
        Professions profession = client.getProfession().getName();
        switch (profession) {
            case IT -> discordUtil.assignOrChangeRole(guild, member, profession.getName(), profession.getName(), Colors.IT.getColor());
            case L -> discordUtil.assignOrChangeRole(guild, member, profession.getName(), profession.getName(), Colors.L.getColor());
            case M -> discordUtil.assignOrChangeRole(guild, member, profession.getName(), profession.getName(), Colors.M.getColor());
            case R -> discordUtil.assignOrChangeRole(guild, member, profession.getName(), profession.getName(), Colors.R.getColor());
        }

        // select if teacher or student
        if (client.getScholar().getName().equals(Scholars.STUDENT)) {
            // find and assign student role
            Scholar role = scholarRepository.findByName(Scholars.STUDENT).stream().findFirst().get();
            discordUtil.assignOrChangeRole(guild, member, role.getName().getName(), role.getName().getName(), Colors.GENERIC.getColor());

            // assign client year to user role
            discordUtil.assignOrChangeRole(guild, member, "Year " + client.getEnrolment().getYear().getYear().getYear(), "Year " + client.getEnrolment().getYear().getYear().getYear(), Colors.GENERIC.getColor());
            // assign client className to user role
            discordUtil.assignOrChangeRole(guild, member, client.getEnrolment().getName(), client.getEnrolment().getName(), Colors.CLASS.getColor());
        } else {
            // find and assign teacher role
            Scholar role = scholarRepository.findByName(Scholars.TEACHER).stream().findFirst().get();
            discordUtil.assignOrChangeRole(guild, member, client.getEnrolment().getName(), role.getName().getName(), Colors.GENERIC.getColor());
        }
    }

    public void persistClient(VerificationClient verificationClient) {
        Client client = verificationClient.getClient();
        User user = verificationClient.getUser();

        clientRepository.findByDiscordId(user.getId()).ifPresentOrElse(
                existingClient -> {
                    existingClient.setDiscordName(client.getDiscordName());
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

    public void changeUsername(String oldName, String newName) {
        clientRepository.findByDiscordName(oldName)
                .ifPresent(client -> client.setDiscordName(newName));
        logger.info("User renamed from: {} to: {}", oldName, newName);
    }
}
