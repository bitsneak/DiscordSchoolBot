package at.htlle.discord.bot;

import at.htlle.discord.jpa.entity.Client;
import at.htlle.discord.jpa.repository.ClientRepository;
import at.htlle.discord.model.VerificationClient;
import at.htlle.discord.model.enums.Scholars;
import at.htlle.discord.model.enums.VerificationStates;
import at.htlle.discord.service.LoginService;
import at.htlle.discord.util.DiscordUtil;
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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

        discordUtil.sendPrivateMessage(user, "Welcome to the server! Please reply with your school email address for verification.");
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
                            // set the left at attribute from the client
                            clientRepository.findByDiscordId(user.getId()).ifPresent(client -> {
                                client.setLeftAt(LocalDateTime.now(ZoneOffset.UTC));
                                clientRepository.save(client);
                                logger.info("client: {} left the server", user.getId());
                            });

                            // send JSON file of the db to the log channel
                            discordUtil.sendJsonToLogChannel();
                        }
                );
    }

    private void assignRoles(VerificationClient verificationClient) {
        User user = verificationClient.getUser();

        // assign roles
        loginService.assignRoles(verificationClient);
        logger.info("Roles assigned to user {}", user.getId());

        // persist client to database
        loginService.persistClient(verificationClient);
        logger.info("Client {} persisted to database", verificationClient.getClient().getId());

        // send JSON file of all server members to log channel
        discordUtil.sendJsonToLogChannel();
        logger.info("Sent client data for client {} to admin channel", verificationClient.getClient().getId());

        // client is now verified, therefore remove from pending verifications
        pendingVerifications.remove(verificationClient);
        discordUtil.sendPrivateMessage(user, "You have been successfully verified. Welcome!");
        logger.info("User {} has been successfully verified.", user.getId());
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
                case AWAIT_EMAIL -> {
                    if (loginService.handleEmailInput(verificationClient, input)) {
                        verificationClient.setState(VerificationStates.AWAIT_CODE);
                    }
                }
                case AWAIT_CODE -> {
                    if (loginService.handleCodeInput(verificationClient, input)) {
                        verificationClient.setState(VerificationStates.AWAIT_PROFESSION);
                    }
                }
                case AWAIT_PROFESSION -> {
                    if (loginService.handleProfessionInput(verificationClient, input)) {
                        if (verificationClient.getClient().getScholar().getName().equals(Scholars.STUDENT)) {
                            discordUtil.sendPrivateMessage(verificationClient.getUser(), "Please enter your class teacher abbreviation.");
                            verificationClient.setState(VerificationStates.AWAIT_CLASS_TEACHER);
                        } else {
                            assignRoles(verificationClient);
                        }
                    }
                }
                case AWAIT_CLASS_TEACHER -> {
                    if (loginService.handleTeacherInput(verificationClient, input)) {
                        assignRoles(verificationClient);
                    }
                }
                default -> {
                    discordUtil.sendPrivateMessage(user, "Invalid input.");
                }
            }
        }, () -> {
            discordUtil.sendPrivateMessage(user, "You are not currently in the verification process.");
        });
    }
}