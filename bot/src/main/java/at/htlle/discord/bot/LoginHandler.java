package at.htlle.discord.bot;

import at.htlle.discord.jpa.entity.Client;
import at.htlle.discord.jpa.repository.ClientRepository;
import at.htlle.discord.model.VerificationClient;
import at.htlle.discord.model.enums.VerificationStates;
import at.htlle.discord.service.LoginService;
import at.htlle.discord.util.DiscordUtil;
import lombok.Getter;
import lombok.Setter;
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

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private DiscordUtil discordUtil;

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        User user = event.getUser();
        pendingVerifications.add(new VerificationClient(event.getGuild(), user, new Client()));

        loginService.sendPrivateMessage(user, "Welcome to the server! Please reply with your school email address for verification.");
        logger.info("New member joined: {}", user.getId());

    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        User user = event.getUser();

        // check if the user has a pending verification and remove them if necessary
        pendingVerifications.stream()
                .filter(vc -> vc.getUser().equals(user))
                .findFirst()
                .ifPresentOrElse(
                        pendingVerifications::remove,
                        () -> {
                            // delete the client from the database if they exist
                            clientRepository.findByDiscordId(user.getId()).ifPresent(client -> {
                                clientRepository.delete(client);
                                logger.info("Removed client: {}", user.getId());
                            });

                            // Only send the JSON update if it is not part of the rotation process
                            discordUtil.sendJsonToLogChannel();
                        }
                );
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
                        logger.info("User {} has been successfully verified.", user.getId());
                        loginService.assignRoles(verificationClient);
                        logger.info("Roles assigned to user {}", user.getId());
                        loginService.persistClient(verificationClient);
                        // client is now persisted, therefore remove from pending verifications
                        pendingVerifications.remove(verificationClient);
                        logger.info("Client {} persisted to database", verificationClient.getClient().getId());
                        discordUtil.sendJsonToLogChannel();
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