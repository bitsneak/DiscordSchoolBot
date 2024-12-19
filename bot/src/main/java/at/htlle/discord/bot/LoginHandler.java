package at.htlle.discord.bot;

import at.htlle.discord.jpa.entity.Client;
import at.htlle.discord.model.VerificationClient;
import at.htlle.discord.model.enums.VerificationStates;
import at.htlle.discord.service.LoginService;
import lombok.Getter;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class LoginHandler extends ListenerAdapter {
    private static final Logger logger = LogManager.getLogger(LoginHandler.class);

    private final Set<VerificationClient> pendingVerifications = new HashSet<>();

    @Autowired
    @Getter
    private LoginService loginService;

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        User user = event.getUser();
        pendingVerifications.add(new VerificationClient(user, new Client()));

        loginService.sendPrivateMessage(user, "Welcome to the server! Please reply with your school email address for verification.");
        logger.info("New member joined: {}", user.getIdLong());

    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        User user = event.getUser();

        pendingVerifications.stream()
                .filter(vc -> vc.getUser().equals(user))
                .findFirst().
                ifPresentOrElse(pendingVerifications::remove, () -> {
                    loginService.setClientLeftAt(user);
                    loginService.sendJsonToAdminChannel();
                });

        logger.info("Member removed: {}", user.getIdLong());
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromType(ChannelType.PRIVATE) || event.getAuthor().isBot()) {
            return; // ignore messages from other channels or bots
        }

        User user = event.getAuthor();
        logger.debug("Message received from user {} in private channel.", user.getName());

        pendingVerifications.stream().filter(vc -> vc.getUser().equals(user)).findFirst().ifPresentOrElse(verificationClient -> {
            String input = event.getMessage().getContentRaw().strip().trim();
            logger.debug("User input: {}", input);

            switch (verificationClient.getState()) {
                case AWAITING_EMAIL -> {
                    if (loginService.handleEmailInput(verificationClient, input)) {
                        verificationClient.setState(VerificationStates.AWAITING_CODE);
                    }
                }
                case AWAITING_CODE -> {
                    if (loginService.handleCodeInput(verificationClient, input)) {
                        verificationClient.setState(VerificationStates.AWAITING_PROFESSION);
                    }
                }
                case AWAITING_PROFESSION -> {
                    if (loginService.handleProfessionInput(verificationClient, input)) {
                        verificationClient.setState(VerificationStates.AWAITING_CLASS_TEACHER);
                    }
                }
                case AWAITING_CLASS_TEACHER -> {
                    if (loginService.handleTeacherInput(verificationClient, input)) {
                        logger.info("User {} has been successfully verified.", user.getIdLong());
                        loginService.assignRoles(verificationClient);
                        logger.info("Roles assigned to user {}", user.getIdLong());
                        loginService.persistClient(verificationClient);
                        // client is now persisted, therefore remove from pending verifications
                        pendingVerifications.remove(verificationClient);
                        logger.info("Client {} persisted to database", verificationClient.getClient().getId());
                        loginService.sendJsonToAdminChannel();
                        logger.info("Sent client data for client {} to admin channel", verificationClient.getClient().getId());
                    }
                }
                default -> {
                    loginService.sendPrivateMessage(user, "Invalid input. Please enter school email, 6-digit code, profession or class name.");
                }
            }
        }, () -> {
            loginService.sendPrivateMessage(user, "You are not currently in the verification process.");
        });
    }
}