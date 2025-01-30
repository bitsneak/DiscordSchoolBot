package at.htlle.discord.util;

import at.htlle.discord.jpa.entity.Client;
import at.htlle.discord.jpa.repository.ClientRepository;
import at.htlle.discord.service.LoginService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class DiscordUtil {

    private static final Logger logger = LogManager.getLogger(DiscordUtil.class);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @Setter
    private TextChannel logChannel;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ObjectMapper objectMapper;

    public void sendPrivateMessage(User user, String message) {
        user.openPrivateChannel().queue(channel -> channel.sendMessage(message).queue());
    }

    public void sendJsonToLogChannel() {
        String timestamp = LocalDateTime.now(ZoneOffset.UTC).format(DATE_TIME_FORMATTER);
        logger.info("Sending JSON to log channel with timestamp: {}", timestamp);
        List<Client> clients = clientRepository.findAll();

        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(byteArrayOutputStream, clients);
            byte[] content = byteArrayOutputStream.toByteArray();
            logChannel.sendFiles(Collections.singletonList(FileUpload.fromData(content, "user_data-" + timestamp.replace(":", "_") + ".json"))).queue();
            logger.info("JSON file sent to log channel successfully.");
        } catch (IOException e) {
            logger.error("Error while sending JSON to log channel: {}", e.getMessage(), e);
        }
    }

    public void assignRole(Guild guild, Member member, String roleName, Color color) {
        Optional<Role> existingRole = guild.getRolesByName(roleName, true).stream().findFirst();

        if (existingRole.isPresent()) {
            // assign existing role
            guild.addRoleToMember(member, existingRole.get()).queue(
                    success -> logger.info("Successfully assigned existing role {} to user {}", roleName, member.getId()),
                    error -> logger.error("Failed to assign existing role {} to user {}. Error: {}", roleName, member.getId(), error.getMessage())
            );
        } else {
            // create role and assign it
            guild.createRole()
                    .setName(roleName)
                    .setColor(color)
                    .setMentionable(true)
                    .setPermissions(Permission.MESSAGE_HISTORY, Permission.MESSAGE_SEND, Permission.MESSAGE_ADD_REACTION, Permission.VIEW_CHANNEL)
                    .queue(
                            role -> guild.addRoleToMember(member, role).queue(
                                    success -> logger.info("Created and assigned new role {} to user {}", roleName, member.getId()),
                                    error -> logger.error("Failed to assign new role {} to user {}. Error: {}", roleName, member.getId(), error.getMessage())
                            ),
                            error -> logger.error("Failed to create role {}. Error: {}", roleName, error.getMessage())
                    );
        }
    }
}
