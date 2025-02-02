package at.htlle.discord.service;

import at.htlle.discord.enums.Scholars;
import at.htlle.discord.jpa.entity.*;
import at.htlle.discord.jpa.repository.*;
import at.htlle.discord.model.VerificationClient;
import at.htlle.discord.util.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
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
    private ProfessionRepository professionRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private ScholarRepository scholarRepository;

    public boolean handleEmailInput(VerificationClient verificationClient, String email) {
        if (!email.matches(EMAIL_STUDENT_REGEX) && !email.matches(EMAIL_TEACHER_REGEX)) {
            discordUtil.sendPrivateMessage(verificationClient.getUser(), "Enter a valid school email address.");
            return false;
        }

        String verificationCode = verificationService.generateVerificationCode(verificationClient);
        mailService.sendVerificationEmail(email, verificationCode);
        verificationClient.getClient().setEmail(email);
        verificationClient.getClient().setDiscordName(verificationClient.getUser().getName());

        if (email.matches(EMAIL_STUDENT_REGEX)) {
            // find or create student scholar and assign it
            scholarRepository.findByName(Scholars.STUDENT).ifPresentOrElse(
                    verificationClient.getClient()::setScholar,
                    () -> {
                        Scholar scholar = scholarRepository.save(new Scholar(Scholars.STUDENT));
                        verificationClient.getClient().setScholar(scholar);
                    }
            );
        } else {
            // find or create teacher scholar and assign it
            scholarRepository.findByName(Scholars.TEACHER).ifPresentOrElse(
                    s -> verificationClient.getClient().setScholar(s),
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
            discordUtil.sendPrivateMessage(verificationClient.getUser(), "Please enter your profession. One out of: " + allProfessionNames());
            logger.info("Code found for user: {}. Entered email is correct.", verificationClient.getUser().getId());
            return true;
        }

        discordUtil.sendPrivateMessage(verificationClient.getUser(), "Invalid code. Try again.");
        return false;
    }

    public boolean handleProfessionInput(VerificationClient verificationClient, String profession) {
        // find the profession
        Optional<Profession> professionEntity = professionRepository.findByName(profession);
        if (professionEntity.isEmpty()) {
            discordUtil.sendPrivateMessage(verificationClient.getUser(), "Invalid profession. Enter one out of: " + allProfessionNames());
            return false;
        }

        Client client = verificationClient.getClient();
        client.setProfession(professionEntity.get());

        logger.info("Profession {} set for user: {}", profession, verificationClient.getUser().getId());
        return true;
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
        Guild guild = verificationClient.getGuild();
        Member member = guild.retrieveMember(user).complete();
        Client client = verificationClient.getClient();

        // create / assign client profession as user role
        String profession = client.getProfession().getName();
        guild.getRolesByName(profession, true).stream()
                .findFirst()
                .ifPresentOrElse(
                        r -> discordUtil.assignRole(guild, member, r.getName()),
                        () -> discordUtil.createRole(guild, profession, discordUtil.findColorForName(profession), createdRole -> {
                            discordUtil.assignRole(guild, member, createdRole.getName());
                        })
                );

        // select if teacher or student
        if (client.getScholar().getName().equals(Scholars.STUDENT)) {
            // create / assign student role
            String role = scholarRepository.findByName(Scholars.STUDENT).stream().findFirst().get().getName().getName();
            guild.getRolesByName(role, true).stream()
                    .findFirst()
                    .ifPresentOrElse(
                            r -> discordUtil.assignRole(guild, member, r.getName()),
                            () -> discordUtil.createRole(guild, role, discordUtil.findColorForName(role), createdRole -> {
                                discordUtil.assignRole(guild, member, createdRole.getName());
                            })
                    );

            String year = String.valueOf(client.getEnrolment().getYear().getYear());
            String enrolment = client.getEnrolment().getName();

            // create / assign client year to user role
            guild.getRolesByName("Year " + year, true).stream()
                    .findFirst()
                    .ifPresentOrElse(
                            r -> discordUtil.assignRole(guild, member, r.getName()),
                            () -> discordUtil.createRole(guild, "Year " + year, discordUtil.findColorForName("Year " + year), createdRole -> {
                                discordUtil.assignRole(guild, member, createdRole.getName());
                            })
                    );

            // create / assign client className to user role
            guild.getRolesByName(enrolment, true).stream()
                    .findFirst()
                    .ifPresentOrElse(
                            r -> discordUtil.assignRole(guild, member, r.getName()),
                            () -> discordUtil.createRole(guild, enrolment, discordUtil.findColorForName(enrolment), createdRole -> {
                                discordUtil.assignRole(guild, member, createdRole.getName());
                            })
                    );
        } else {
            // find / create teacher role
            String role = scholarRepository.findByName(Scholars.TEACHER).stream().findFirst().get().getName().getName();
            guild.getRolesByName(role, true).stream()
                    .findFirst()
                    .ifPresentOrElse(
                            r -> discordUtil.assignRole(guild, member, r.getName()),
                            () -> discordUtil.createRole(guild, role, discordUtil.findColorForName(role), createdRole -> {
                                discordUtil.assignRole(guild, member, createdRole.getName());
                            })
                    );
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
                    } else {
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

    private String allProfessionNames() {
        return String.join(", ", professionRepository.findAll()
                .stream()
                .map(Profession::getName)
                .toList());
    }
}
