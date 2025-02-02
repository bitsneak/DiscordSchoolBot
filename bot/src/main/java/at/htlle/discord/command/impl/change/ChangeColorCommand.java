package at.htlle.discord.command.impl.change;

import at.htlle.discord.command.CommandAction;
import at.htlle.discord.command.Commands;
import at.htlle.discord.jpa.entity.Color;
import at.htlle.discord.jpa.repository.ColorRepository;
import at.htlle.discord.jpa.repository.EnrolmentRepository;
import at.htlle.discord.util.DiscordUtil;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ChangeColorCommand implements CommandAction {
    private static final Logger logger = LogManager.getLogger(ChangeColorCommand.class);

    @Autowired
    private ColorRepository colorRepository;

    @Autowired
    private EnrolmentRepository enrolmentRepository;

    @Autowired
    private DiscordUtil discordUtil;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<String> optionValues = new ArrayList<>();

        // retrieve all the options for this command
        Commands.CHANGE_COLOR.getOptions().forEach(option -> {
            // add the option value dynamically based on the option name in the enum
            optionValues.add(Objects.requireNonNull(event.getOption(option.name())).getAsString());
        });

        String scope = optionValues.getFirst().toUpperCase();
        String colorHex = optionValues.get(1).toUpperCase();

        // check if the color matches the pattern (# followed by 6 characters)
        if (!colorHex.matches("^#[0-9A-F]{6}")) {
            event.reply("Invalid color format **" + colorHex + "**. Must start with a # followed by 6 characters consisting of 0-9 and A-F").queue();
            return;
        }

        // check if the scope exists
        Optional<Color> existingNewColor = colorRepository.findByScope(scope);
        if (existingNewColor.isEmpty()) {
            event.reply("Scope not found: **" + scope + "**").queue();
            return;
        }

        Color color = existingNewColor.get();
        String colorOld = color.getColor();
        color.setColor(colorHex);

        // if all enrolments should have the same color
        if (scope.equalsIgnoreCase("class")) {
            enrolmentRepository.findAll().forEach(
                    e -> {
                        discordUtil.changeRole(event.getGuild(), e.getName(), java.awt.Color.decode(colorHex));
                    }
            );
        } else {
            // save color
            colorRepository.save(color);
            // change role
            discordUtil.changeRole(event.getGuild(), scope, java.awt.Color.decode(colorHex));
        }

        // update the color
        colorRepository.save(color);

        // update roles
        //discordUtil.changeRole(Objects.requireNonNull(event.getGuild()), scope, java.awt.Color.decode(colorHex));

        event.reply("Changed color from **" + colorOld + "** to **" + colorHex + "** for scope **" + scope + "**").queue();
        logger.info("Changes color from: {} to: {} for scope: {}", colorOld, colorHex, scope);
    }
}
