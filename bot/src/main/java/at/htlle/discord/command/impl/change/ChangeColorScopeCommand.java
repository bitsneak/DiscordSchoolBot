package at.htlle.discord.command.impl.change;

import at.htlle.discord.command.CommandAction;
import at.htlle.discord.command.Commands;
import at.htlle.discord.jpa.entity.Color;
import at.htlle.discord.jpa.repository.ColorRepository;
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
public class ChangeColorScopeCommand implements CommandAction {
    private static final Logger logger = LogManager.getLogger(ChangeProfessionCommand.class);

    @Autowired
    private ColorRepository colorRepository;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<String> optionValues = new ArrayList<>();

        // retrieve all the options for this command
        Commands.CHANGE_COLOR_SCOPE.getOptions().forEach(option -> {
            // add the option value dynamically based on the option name in the enum
            optionValues.add(Objects.requireNonNull(event.getOption(option.name())).getAsString());
        });

        String colorScopeOld = optionValues.getFirst().toUpperCase();
        String colorScopeNew = optionValues.get(1).toUpperCase();

        // check if the new color already exists
        Optional<Color> existingNewColor = colorRepository.findByScope(colorScopeNew);
        if (existingNewColor.isPresent()) {
            event.reply("Scope already exists: **" + colorScopeNew + "**").queue();
            return;
        }

        // check if the old scope exists
        Optional<Color> existingOldColor = colorRepository.findByScope(colorScopeNew);
        if (existingOldColor.isPresent()) {
            Color oldColor = existingOldColor.get();
            // rename scope
            oldColor.setScope(colorScopeNew);
            colorRepository.save(oldColor);

            event.reply("Renamed color scope **" + colorScopeOld + "** to **" + colorScopeNew + "**").queue();
            logger.info("Renamed color scope: {} to: {}", colorScopeOld, colorScopeNew);
        } else {
            event.reply("Scope not found: **" + colorScopeOld + "**").queue();
            logger.error("Color scope: {} not found for renaming.", colorScopeOld);
        }
    }
}
