package at.htlle.discord.command.impl.add;

import at.htlle.discord.command.CommandAction;
import at.htlle.discord.command.Commands;
import at.htlle.discord.enums.Scholars;
import at.htlle.discord.jpa.entity.*;
import at.htlle.discord.jpa.repository.*;
import at.htlle.discord.util.DiscordUtil;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AddColorCommand implements CommandAction {
    private static final Logger logger = LogManager.getLogger(AddColorCommand.class);

    @Autowired
    private ColorRepository colorRepository;

    @Autowired
    private EnrolmentRepository enrolmentRepository;

    @Autowired
    private ProfessionRepository professionRepository;

    @Autowired
    private YearRepository yearRepository;

    @Autowired
    private ScholarRepository scholarRepository;

    @Autowired
    private DiscordUtil discordUtil;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<String> optionValues = new ArrayList<>();

        // retrieve all the options for this command
        Commands.ADD_COLOR.getOptions().forEach(option -> {
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

        // check if the color is already associated with the scope
        if (colorRepository.findByScope(scope).isEmpty()) {
            event.reply("Color already exists for scope: **" + scope + "**").queue();
            return;
        }

        // check if the scope is in the accepted selection
        if (!isValidScope(scope)) {
            event.reply("Invalid scope **" + scope + "**. Must match an existing Enrolment, Profession, Year, or Scholar.").queue();
            return;
        }

        // if all enrolments should have the same color
        if (scope.equalsIgnoreCase("class")) {
            enrolmentRepository.findAll().forEach(
                    e -> {
                        discordUtil.changeRole(event.getGuild(), e.getName(), java.awt.Color.decode(colorHex));
                        colorRepository.save(new Color(e.getName(), colorHex));
                    }
            );
        } else {
            // save color
            colorRepository.save(new Color(scope, colorHex));
            // change role
            discordUtil.changeRole(event.getGuild(), scope, java.awt.Color.decode(colorHex));
        }

        event.reply("Added color **" + colorHex + "** with scope **" + scope + "**").queue();
        logger.info("Added color: {} with scope: {}", colorHex, scope);
    }

    /**
     * Checks if the provided command scope is valid.
     * Valid command scope are:
     * <ul>
     *     <li>class: all of the class roles are affected equally</li>
     *     <li>{profession}: an individual profession</li>
     *     <li>{year}: an individual year</li>
     *     <li>{scholar}: an individual scholar</li>
     *  </ul>
     * @param scope
     * @return true, if the scope is the right one as defined
     */
    private boolean isValidScope(String scope) {
        return scope.equalsIgnoreCase("class")
                || professionRepository.findByName(scope).isPresent()
                || yearRepository.findByYear(parseYear(scope)).isPresent()
                || scholarRepository.findByName(parseScholar(scope)).isPresent();
    }

    private int parseYear(String scope) {
        try {
            return Integer.parseInt(scope);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private Scholars parseScholar(String scope) {
        try {
            return Scholars.valueOf(scope.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
