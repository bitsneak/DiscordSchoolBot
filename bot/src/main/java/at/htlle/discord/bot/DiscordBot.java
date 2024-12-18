package at.htlle.discord.bot;

import at.htlle.discord.jpa.entity.Client;
import at.htlle.discord.model.VerificationClient;
import at.htlle.discord.model.enums.VerificationState;
import at.htlle.discord.service.DiscordService;
import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
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

import java.util.*;

@Component
public class DiscordBot extends ListenerAdapter {
    private static final Logger logger = LogManager.getLogger(DiscordBot.class);

    private final Set<VerificationClient> pendingVerifications = new HashSet<>();

    @Autowired
    private DiscordService discordService;

    @Value("${discord.token}")
    private String discordToken;

    @Value("${discord.channel.log.name}")
    private String adminChannelName;

    @PostConstruct
    public void initBot() throws Exception {
        logger.info("Initializing Discord bot with token: {}", discordToken);
        try {
            JDA jda = JDABuilder.createDefault(discordToken)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS)
                    .addEventListeners(this)
                    .build();
            jda.awaitReady();
            logger.info("Bot is now online and ready.");
        } catch (Exception e) {
            logger.error("Error initializing Discord bot: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void onReady(ReadyEvent event) {
        JDA jda = event.getJDA();
        logger.info("Bot is ready. Fetching guilds and channels...");

        jda.getGuilds().stream()
                .flatMap(guild -> guild.getTextChannels().stream())
                .filter(channel -> channel.getName().equals(adminChannelName))
                .findFirst()
                .ifPresent(channel -> {
                    discordService.setAdminChannel(channel);
                    logger.info("Admin channel found: {}", channel.getName());
                });
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        User user = event.getUser();
        System.out.println(user.getName());
        pendingVerifications.add(new VerificationClient(user, new Client()));

        discordService.sendPrivateMessage(user, "Welcome to the server! Please reply with your school email address for verification.");
        logger.info("New member joined: {}", user.getName());

    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        User user = event.getUser();
        discordService.setClientLeftAt(user);
        discordService.sendJsonToAdminChannel();

        logger.info("Member removed: {}", user.getIdLong());
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.isFromType(ChannelType.PRIVATE) && !event.getAuthor().isBot()) {
            User user = event.getAuthor();
            logger.debug("Message received from user {} in private channel.", user.getName());

            pendingVerifications.stream().filter(vc -> vc.getUser().equals(user)).findFirst().ifPresentOrElse(verificationClient -> {
                String input = event.getMessage().getContentRaw().strip().trim();
                logger.debug("User input: {}", input);

                switch (verificationClient.getState()) {
                    case AWAITING_EMAIL -> {
                        if (discordService.handleEmailInput(verificationClient, input)) {
                            verificationClient.setState(VerificationState.AWAITING_CODE);
                        }
                    }
                    case AWAITING_CODE -> {
                        if (discordService.handleCodeInput(verificationClient, input)) {
                            verificationClient.setState(VerificationState.AWAITING_PROFESSION);
                        }
                    }
                    case AWAITING_PROFESSION -> {
                        if (discordService.handleProfessionInput(verificationClient, input)) {
                            verificationClient.setState(VerificationState.AWAITING_CLASS);
                        }
                    }
                    case AWAITING_CLASS -> {
                        if (discordService.handleClassInput(verificationClient, input)) {
                            logger.info("User {} has been successfully verified. Assigning roles", user.getIdLong());
                            discordService.assignRoles(verificationClient);
                            logger.info("Roles assigned to user {}", user.getIdLong());
                            discordService.persistClient(verificationClient);
                            // client is now persisted, therefore remove from pending verifications
                            pendingVerifications.remove(verificationClient);
                            logger.info("Client {} persisted to database", verificationClient.getClient().getId());
                            discordService.sendJsonToAdminChannel();
                            logger.info("Sent client data for client {} to admin channel", verificationClient.getClient().getId());
                        }
                    }
                    default -> {
                        discordService.sendPrivateMessage(user, "Invalid input. Please enter school email, 6-digit code, profession or class name.");
                    }
                }
            }, () -> {
                discordService.sendPrivateMessage(user, "You are not currently in the verification process.");
            });
        }
    }
}