package at.htlle.discord.util;

import at.htlle.discord.jpa.entity.Client;
import at.htlle.discord.jpa.repository.ClientRepository;
import at.htlle.discord.jpa.repository.ColorRepository;
import at.htlle.discord.enums.Colors;
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
import java.util.function.Consumer;

@Component
public class DiscordUtil {

    private static final Logger logger = LogManager.getLogger(DiscordUtil.class);

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @Setter
    private TextChannel logChannel;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ColorRepository colorRepository;

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
            logChannel.sendFiles(Collections.singletonList(FileUpload.fromData(content, "user-data-" + timestamp.replace(":", "-") + ".json"))).queue();
            logger.info("JSON file sent to log channel successfully.");
        } catch (IOException e) {
            logger.error("Error while sending JSON to log channel: {}", e.getMessage(), e);
        }
    }

    public java.awt.Color findColorForName(String scope) {
        return colorRepository.findByScope(scope)
                .map(at.htlle.discord.jpa.entity.Color::getColor) // extract hex string from color entity
                .map(java.awt.Color::decode) // convert hex string to java.awt.Color
                .orElse(Colors.GENERIC.getColor()); // default color if not found
    }

    public boolean createRole(Guild guild, String roleName, Color color, Consumer<Role> onSuccess) {
        Optional<Role> existingRole = guild.getRolesByName(roleName, true).stream().findFirst();

        // check if the role already exists
        if (existingRole.isPresent()) {
            return false;
        }

        // create new role
        guild.createRole()
                .setName(roleName)
                .setColor(color)
                .setMentionable(true)
                .setPermissions(Permission.MESSAGE_HISTORY, Permission.MESSAGE_SEND, Permission.MESSAGE_ADD_REACTION, Permission.VIEW_CHANNEL)
                .queue(
                        role -> {
                            logger.info("Created role: {}", roleName);
                            if (onSuccess != null) {
                                onSuccess.accept(role);
                            }
                        },
                        error -> logger.error("Failed to create role: {}. Error: {}", roleName, error.getMessage())
                );
        return true;
    }

    public boolean assignRole(Guild guild, Member member, String roleName) {
        Optional<Role> existingRole = guild.getRolesByName(roleName, true).stream().findFirst();

        // check if the role exists
        if (existingRole.isEmpty()) {
            return false;
        }

        // assign role
        existingRole.ifPresent(role -> guild.addRoleToMember(member, role).queue(
                success -> logger.info("Assigned role {} to user {}", roleName, member.getId()),
                error -> logger.error("Failed to assign role {} to user {}. Error: {}", roleName, member.getId(), error.getMessage())
        ));
        return true;
    }

    public boolean changeRole(Guild guild, String oldRoleName, String newRoleName, Color color) {
        Optional<Role> existingRole = guild.getRolesByName(oldRoleName, true).stream().findFirst();

        // check if the role exists
        if (existingRole.isEmpty()) {
            return false;
        }

        // change role
        existingRole.ifPresent(role -> role.getManager().setName(newRoleName).setColor(color).queue(
                success -> logger.info("Changed role from: {} to: {} with color: {}", oldRoleName, newRoleName, color.getRGB()),
                error -> logger.error("Failed to change role from: {} to: {} with color: {}. Error: {}", oldRoleName, oldRoleName, color.getRGB(), error.getMessage())
        ));
        return true;
    }

    // only change color
    public boolean changeRole(Guild guild, String roleName, Color color) {
        return changeRole(guild, roleName, roleName, color);
    }

    // only change name
    public boolean changeRole(Guild guild, String oldRoleName, String newRoleName) {
        return changeRole(guild, oldRoleName, newRoleName, findColorForName(oldRoleName));
    }
}
