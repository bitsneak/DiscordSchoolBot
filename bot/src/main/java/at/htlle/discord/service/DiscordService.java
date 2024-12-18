package at.htlle.discord.service;

import at.htlle.discord.jpa.repository.*;
import at.htlle.discord.model.enums.*;
import at.htlle.discord.jpa.entity.Client;
import at.htlle.discord.model.VerificationClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.Setter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

@Service
public class DiscordService {
    private static final Logger logger = LogManager.getLogger(DiscordService.class);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@o365\\.htl-leoben\\.at$";

    private static final String EMAIL_TEACHER_REGEX = "^[a-z]+@o365\\.htl-leoben\\.at$";

    @Setter
    private TextChannel adminChannel;

    @Autowired
    private VerificationService verificationService;

    @Autowired
    private MailService mailService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private EnrolmentRepository enrolmentRepository;

    @Autowired
    private YearRepository yearRepository;

    @Autowired
    private ProfessionRepository professionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    public void sendPrivateMessage(User user, String message) {
        user.openPrivateChannel().queue(channel -> channel.sendMessage(message).queue());
    }

    public boolean handleEmailInput(VerificationClient verificationClient, String email) {
        if (!email.matches(EMAIL_REGEX)) {
            sendPrivateMessage(verificationClient.getUser(), "Please enter a valid school email address.");
            return false;
        }

        String verificationCode = verificationService.generateVerificationCode(verificationClient);
        mailService.sendVerificationEmail(email, verificationCode);
        verificationClient.getClient().setEmail(email);

        sendPrivateMessage(verificationClient.getUser(), "A verification code has been sent to your email. Please enter the code.");
        logger.info("Verification email sent to user: {}", verificationClient.getUser().getIdLong());

        return true;
    }

    public boolean handleCodeInput(VerificationClient verificationClient, String code) {
        Boolean isCodeValid = verificationService.verifyCode(verificationClient, code);

        if (isCodeValid == null) {
            sendPrivateMessage(verificationClient.getUser(), "Your verification code has expired.");
            handleEmailInput(verificationClient, verificationClient.getClient().getEmail());
            return false;
        }

        if (isCodeValid) {
            sendPrivateMessage(verificationClient.getUser(), "Please enter your profession. One out of " + Arrays.toString(Profession.values()));
            logger.info("Code found for user: {}. Entered email is correct.", verificationClient.getUser().getIdLong());
            return true;
        }

        sendPrivateMessage(verificationClient.getUser(), "Invalid code. Please enter the code again.");
        return false;
    }

    public boolean handleProfessionInput(VerificationClient verificationClient, String profession) {
        if (Arrays.stream(Profession.values()).anyMatch(p -> p.getName().equalsIgnoreCase(profession))) {
            Profession professionEnum = Profession.valueOf(profession.toUpperCase());
            Client client = verificationClient.getClient();
            client.setProfession(new at.htlle.discord.jpa.entity.Profession(professionEnum));

            sendPrivateMessage(verificationClient.getUser(), "Please enter your class name.");
            logger.info("Profession found for user: {}", verificationClient.getUser().getIdLong());
            return true;
        }

        sendPrivateMessage(verificationClient.getUser(), "Invalid profession. Please enter one out of " + Arrays.toString(Profession.values()));
        return false;
    }

    public boolean handleClassInput(VerificationClient verificationClient, String className) {
        if (Arrays.stream(Enrolment.values()).anyMatch(e -> e.getName().equalsIgnoreCase(className))) {
            // first character of the class name is always the year
            char yearChar = className.charAt(0);
            Year yearEnum = Arrays.stream(Year.values()).filter(y -> y.getName().equals(String.valueOf(yearChar))).findFirst().get();

            // get jpa year from year enum
            at.htlle.discord.jpa.entity.Year year = new at.htlle.discord.jpa.entity.Year(yearEnum);

            // get enum enrolment from class name
            Enrolment enrolmentEnum = Arrays.stream(Enrolment.values()).filter(e -> e.getName().equalsIgnoreCase(className)).findFirst().get();

            // create jpa enrolment from enum enrolment and year
            Client client = verificationClient.getClient();
            client.setEnrolment(new at.htlle.discord.jpa.entity.Enrolment(enrolmentEnum, year));

            logger.info("Class found for user: {}", verificationClient.getUser().getIdLong());
            return true;
        }

        sendPrivateMessage(verificationClient.getUser(), "Invalid class name. Please enter a valid class name.");
        return false;
    }

    public void assignRoles(VerificationClient verificationClient) {
        User user = verificationClient.getUser();
        Client client = verificationClient.getClient();
        Guild guild = adminChannel.getGuild();
        Member member = guild.retrieveMember(user).complete();

        // Helper method to assign a role by name
        BiConsumer<User, String> assignRole = (u, roleName) ->
                guild.getRolesByName(roleName, true).stream()
                        .findFirst()
                        .ifPresentOrElse(
                                role -> guild.addRoleToMember(member, role).queue(
                                        success -> logger.info("Successfully assigned role {} to user {}", role.getName(), u.getIdLong()),
                                        error -> logger.error("Failed to assign role {} to user {}. Error: {}", role.getName(), u.getIdLong(), error.getMessage())
                                ),
                                () -> logger.warn("Role {} not found in guild.", roleName)
                        );

        // select if teacher or student
        if (client.getEmail().matches(EMAIL_TEACHER_REGEX)) {
            client.setRole(new at.htlle.discord.jpa.entity.Role(Role.TEACHER));
        } else {
            client.setRole(new at.htlle.discord.jpa.entity.Role(Role.STUDENT));
            // assign client className to user role
            assignRole.accept(user, client.getEnrolment().getName().getName());
            // assign client year to user role
            assignRole.accept(user, "Jahrgang " + client.getEnrolment().getYear().getYear().getName());
        }

        // assign client role to user role
        assignRole.accept(user, client.getRole().getName().getName());
        // assign client profession to user role
        assignRole.accept(user, client.getProfession().getName().getName());

        sendPrivateMessage(verificationClient.getUser(), "You have been successfully verified. Welcome!");
    }

    @Transactional
    public void persistClient(VerificationClient verificationClient) {
        Client client = verificationClient.getClient();
        User user = verificationClient.getUser();

        yearRepository.save(client.getEnrolment().getYear());
        enrolmentRepository.save(client.getEnrolment());
        professionRepository.save(client.getProfession());
        roleRepository.save(client.getRole());

        client.setDiscordId(user.getIdLong());
        client.setDiscordName(user.getName());
        client.setJoinedAt(LocalDateTime.now());

        clientRepository.save(client);
    }

    @Transactional
    public void setClientLeftAt(User user) {
        Client client = clientRepository.findByDiscordId(user.getIdLong());

        client.setLeftAt(LocalDateTime.now());
        clientRepository.save(client);
    }

    public void sendJsonToAdminChannel() {
        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(DATE_TIME_FORMATTER);
        logger.info("Sending JSON to admin channel with timestamp: {}", timestamp);
        List<Client> clients = clientRepository.findAll();

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(byteArrayOutputStream, clients);
            byte[] content = byteArrayOutputStream.toByteArray();
            adminChannel.sendFiles(Collections.singletonList(FileUpload.fromData(content, "user_data-" + timestamp.replace(":", "_") + ".json"))).queue();
            logger.info("JSON file sent to admin channel successfully.");
        } catch (IOException e) {
            logger.error("Error while sending JSON to admin channel: {}", e.getMessage(), e);
        }
    }
}
