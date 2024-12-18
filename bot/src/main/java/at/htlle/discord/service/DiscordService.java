package at.htlle.discord.service;

import at.htlle.discord.jpa.entity.*;
import at.htlle.discord.jpa.repository.*;
import at.htlle.discord.model.enums.*;
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
import java.util.Optional;
import java.util.function.BiConsumer;

@Service
public class DiscordService {
    private static final Logger logger = LogManager.getLogger(DiscordService.class);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@o365\\.htl-leoben\\.at$";

    private static final String EMAIL_TEACHER_REGEX = "^[a-z]+@o365\\.htl-leoben\\.at$";

    @Setter
    private TextChannel logChannel;

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
            sendPrivateMessage(verificationClient.getUser(), "Please enter your profession. One out of " + Arrays.toString(Professions.values()));
            logger.info("Code found for user: {}. Entered email is correct.", verificationClient.getUser().getIdLong());
            return true;
        }

        sendPrivateMessage(verificationClient.getUser(), "Invalid code. Please enter the code again.");
        return false;
    }

    public boolean handleProfessionInput(VerificationClient verificationClient, String profession) {
        if (Arrays.stream(Professions.values()).anyMatch(p -> p.getName().equalsIgnoreCase(profession))) {
            // get enum profession from profession name
            Professions professionEnum = Professions.valueOf(profession.toUpperCase());
            Client client = verificationClient.getClient();

            professionRepository.findByName(professionEnum).ifPresentOrElse(
                    client::setProfession,
                    () -> {
                        Profession professionEntity = new Profession(professionEnum);
                        professionRepository.save(professionEntity);
                        client.setProfession(professionEntity);
                    }
            );

            sendPrivateMessage(verificationClient.getUser(), "Please enter your class name.");
            logger.info("Profession found for user: {}", verificationClient.getUser().getIdLong());
            return true;
        }

        sendPrivateMessage(verificationClient.getUser(), "Invalid profession. Please enter one out of " + Arrays.toString(Professions.values()));
        return false;
    }

    public boolean handleClassInput(VerificationClient verificationClient, String className) {
        // check if the className matches any enrolment enum
        Optional<Enrolments> enrolmentEnumOptional = Arrays.stream(Enrolments.values())
                .filter(e -> e.getName().equalsIgnoreCase(className))
                .findFirst();

        if (enrolmentEnumOptional.isPresent()) {
            // get enum enrolment from class name
            Enrolments enrolmentEnum = enrolmentEnumOptional.get();

            // first character of the class name is always the year
            String yearString = String.valueOf(className.charAt(0));

            // find the corresponding year enum
            Optional<Years> yearEnumOptional = Arrays.stream(Years.values())
                    .filter(y -> y.getName().equals(yearString))
                    .findFirst();

            if (yearEnumOptional.isPresent()) {
                Years yearEnum = yearEnumOptional.get();
                Client client = verificationClient.getClient();

                // find or create the enrolment entity
                enrolmentRepository.findByName(enrolmentEnum).ifPresentOrElse(
                        // if enrolment exists, set it to the client
                        client::setEnrolment,
                        // if enrolment does not exist, create it and set it to the client
                        () -> {
                            // find or create the year entity
                            Year year = yearRepository.findByYear(yearEnum)
                                    .orElseGet(() -> yearRepository.save(new Year(yearEnum)));

                            // create and save the enrolment entity
                            Enrolment enrolmentEntity = new Enrolment(enrolmentEnum, year);
                            enrolmentRepository.save(enrolmentEntity);

                            // set the new enrolment to the client
                            client.setEnrolment(enrolmentEntity);
                        }
                );
                logger.info("Class found for user: {}", verificationClient.getUser().getIdLong());
                return true;
            } else {
                sendPrivateMessage(verificationClient.getUser(), "Invalid year in the class name.");
                return false;
            }
        }

        sendPrivateMessage(verificationClient.getUser(), "Invalid class name. Please enter a valid class name.");
        return false;
    }

    public void assignRoles(VerificationClient verificationClient) {
        User user = verificationClient.getUser();
        Client client = verificationClient.getClient();
        Guild guild = logChannel.getGuild();
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
            // find or create teacher role
            roleRepository.findByName(Roles.TEACHER).ifPresentOrElse(
                    client::setRole,
                    () -> {
                        Role role = roleRepository.save(new Role(Roles.TEACHER));
                        client.setRole(role);
                    }
            );
        } else {
            // find or create student role
            roleRepository.findByName(Roles.STUDENT).ifPresentOrElse(
                    client::setRole,
                    () -> {
                        Role role = roleRepository.save(new Role(Roles.STUDENT));
                        client.setRole(role);
                    }
            );

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

        clientRepository.findByDiscordId(user.getIdLong()).ifPresentOrElse(
                existingClient -> {
                    existingClient.setEmail(client.getEmail());
                    existingClient.setProfession(client.getProfession());
                    existingClient.setEnrolment(client.getEnrolment());
                    existingClient.setRole(client.getRole());
                    existingClient.setJoinedAt(LocalDateTime.now(ZoneOffset.UTC));
                    existingClient.setLeftAt(null);
                    clientRepository.save(existingClient);
                },
                () -> {
                    client.setDiscordId(user.getIdLong());
                    client.setDiscordName(user.getName());
                    client.setJoinedAt(LocalDateTime.now(ZoneOffset.UTC));
                    clientRepository.save(client);
                }
        );
    }

    @Transactional
    public void setClientLeftAt(User user) {
        Client client = clientRepository.findByDiscordId(user.getIdLong()).get();

        client.setLeftAt(LocalDateTime.now(ZoneOffset.UTC));
        clientRepository.save(client);
    }

    public void sendJsonToAdminChannel() {
        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(DATE_TIME_FORMATTER);
        logger.info("Sending JSON to admin channel with timestamp: {}", timestamp);
        List<Client> clients = clientRepository.findAll();

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(byteArrayOutputStream, clients);
            byte[] content = byteArrayOutputStream.toByteArray();
            logChannel.sendFiles(Collections.singletonList(FileUpload.fromData(content, "user_data-" + timestamp.replace(":", "_") + ".json"))).queue();
            logger.info("JSON file sent to admin channel successfully.");
        } catch (IOException e) {
            logger.error("Error while sending JSON to admin channel: {}", e.getMessage(), e);
        }
    }
}
