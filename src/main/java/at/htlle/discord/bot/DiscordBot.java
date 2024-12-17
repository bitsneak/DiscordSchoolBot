package at.htlle.discord.bot;

import at.htlle.discord.entity.JsonRecord;
import at.htlle.discord.entity.Role;
import at.htlle.discord.service.JsonService;
import at.htlle.discord.service.MailService;
import at.htlle.discord.service.VerificationService;
import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

@Component
public class DiscordBot extends ListenerAdapter
{
    private static final Logger logger = LogManager.getLogger(DiscordBot.class);

    private final Map<String, User> pendingVerifications = new HashMap<>();

    @Autowired
    private JsonService jsonService;

    @Autowired
    private MailService mailService;

    @Autowired
    private VerificationService verificationService;

    @Value("${discord.token}")
    private String discordToken;

    @Value("${discord.channel.admin.name}")
    private String adminChannelName;

    @PostConstruct
    public void initBot() throws Exception
    {
        logger.info("Initializing Discord bot with token: {}", discordToken);
        try
        {
            JDA jda = JDABuilder.createDefault(discordToken)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS)
                    .addEventListeners(this)
                    .build();
            jda.awaitReady();
            logger.info("Bot is now online and ready.");
        }
        catch (Exception e)
        {
            logger.error("Error initializing Discord bot: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void onReady(ReadyEvent event)
    {
        JDA jda = event.getJDA();
        logger.info("Bot is ready. Fetching guilds and channels...");

        jda.getGuilds().stream()
                .flatMap(guild -> guild.getTextChannels().stream())
                .filter(channel -> channel.getName().equals(adminChannelName))
                .findFirst()
                .ifPresent(channel -> {
                    jsonService.setAdminChannel(channel);
                    logger.info("Admin channel found: {}", channel.getName());
                });
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event)
    {
        User user = event.getUser();
        logger.info("New member joined: {}", user.getName());

        sendPrivateMessage(user, "Welcome to the server! Please reply with your school email address for verification.");
        pendingVerifications.put(user.getId(), user);
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event)
    {
        User user = event.getUser();
        logger.info("Member removed: {}", user.getIdLong());
        jsonService.clearUserInfo(user.getIdLong());
        jsonService.sendJsonToAdminChannel();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        if (event.isFromType(ChannelType.PRIVATE))
        {
            User user = event.getAuthor();
            String userId = user.getId();
            logger.info("Message received from user {} in private channel.", user.getName());

            Optional.ofNullable(pendingVerifications.get(userId))
                    .ifPresent(verificationCode ->
                    {
                        String input = event.getMessage().getContentRaw();
                        logger.debug("User input: {}", input);

                        // Handle verification code input
                        if (input.matches("\\d{6}"))
                        {
                            Optional.ofNullable(verificationService.getEmailByCode(input))
                                    .ifPresentOrElse(
                                            email -> handleCodeInput(user, email),
                                            () -> sendPrivateMessage(user, "Invalid verification code. Please try again.")
                                    );
                        }
                        // Handle email input
                        else if (input.matches("^[a-zA-Z0-9._%+-]+@o365\\.htl-leoben\\.at$"))
                        {
                            handleEmailInput(user, input);
                        }
                        // Invalid input
                        else
                        {
                            sendPrivateMessage(user, "Invalid input. Please enter a valid school email or a 6-digit verification code.");
                        }
                    });
        }
    }

    private void sendPrivateMessage(User user, String message)
    {
        user.openPrivateChannel().queue(channel -> channel.sendMessage(message).queue());
    }

    private void handleEmailInput(User user, String email)
    {
        String verificationCode = verificationService.generateVerificationCode(email);
        mailService.sendVerificationEmail(email, verificationCode);
        sendPrivateMessage(user, "A verification code has been sent to your email. Please enter the 6-digit code to complete verification.");
        logger.info("Verification email sent to user: {}", user.getName());
    }

    private void handleCodeInput(User user, String email)
    {
        JsonRecord record = jsonService.findRecordByEmail(email);
        if (record != null)
        {
            logger.info("Email found for user: {}. Assigning roles.", user.getName());
            assignRoles(user, record);
            jsonService.updateUserInfo(record, user.getIdLong(), user.getName());
            jsonService.sendJsonToAdminChannel();
            user.openPrivateChannel().queue(channel -> channel.sendMessage("You have been successfully verified and assigned the appropriate roles. Welcome!").queue());
        }
        else
        {
            logger.warn("Email not found for user: {}. Verification failed.", user.getName());
            user.openPrivateChannel().queue(channel -> channel.sendMessage("The provided email address was not found in our records. Please try again.").queue());
        }
        pendingVerifications.remove(user.getId());
        verificationService.removeCode(email);
    }

    private void assignRoles(User user, JsonRecord record)
    {
        Guild guild = jsonService.getAdminChannel().getGuild();
        Member member = guild.retrieveMember(user).complete();

        // Helper method to assign a role by name
        BiConsumer<String, Long> assignRole = (roleName, userId) ->
                guild.getRolesByName(roleName, true).stream()
                        .findFirst()
                        .ifPresentOrElse(
                                role -> guild.addRoleToMember(member, role).queue(
                                        success -> logger.info("Successfully assigned role {} to {}", role.getName(), userId),
                                        error -> logger.error("Failed to assign role {} to {}. Error: {}", role.getName(), userId, error.getMessage())
                                ),
                                () -> logger.warn("Role {} not found in guild.", roleName)
                        );

        // Assign roles for the roles in the record
        record.getRoles().forEach(role -> assignRole.accept(role.getName(), user.getIdLong()));

        // Assign roles for the professions in the record
        record.getProfessions().forEach(profession -> assignRole.accept(profession.getName(), user.getIdLong()));

        // Assign year role if the user has the student role
        if (record.getRoles().contains(Role.STUDENT))
        {
            String className = record.getClassName();
            if (!className.isEmpty())
            {
                // Assign year role based on the class year
                assignRole.accept("Jahrgang " + className.charAt(0), user.getIdLong());
            }
            else
            {
                logger.warn("className is null or empty. Cannot assign year and class role.");
            }
        }
    }
}