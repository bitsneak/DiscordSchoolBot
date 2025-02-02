package at.htlle.discord.command.impl.change;

import at.htlle.discord.command.CommandAction;
import at.htlle.discord.command.Commands;
import at.htlle.discord.jpa.entity.Profession;
import at.htlle.discord.jpa.repository.ProfessionRepository;
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
public class ChangeProfessionCommand implements CommandAction {
    private static final Logger logger = LogManager.getLogger(ChangeProfessionCommand.class);

    @Autowired
    ProfessionRepository professionRepository;

    @Autowired
    private DiscordUtil discordUtil;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<String> optionValues = new ArrayList<>();

        // retrieve all the options for this command
        Commands.CHANGE_PROFESSION.getOptions().forEach(option -> {
            // add the option value dynamically based on the option name in the enum
            optionValues.add(Objects.requireNonNull(event.getOption(option.name())).getAsString());
        });

        String professionNameOld = optionValues.getFirst().toUpperCase();
        String professionNameNew = optionValues.get(1).toUpperCase();

        // check if the new profession already exists
        Optional<Profession> existingNewProfession = professionRepository.findByName(professionNameNew);
        if (existingNewProfession.isPresent()) {
            event.reply("Profession already exists: **" + professionNameNew + "**").queue();
            return;
        }

        // check if the old profession exists
        Optional<Profession> existingOldProfession = professionRepository.findByName(professionNameOld);
        if (existingOldProfession.isPresent()) {
            Profession oldProfession = existingOldProfession.get();
            // rename profession
            oldProfession.setName(professionNameNew);
            professionRepository.save(oldProfession);

            // change the corresponding Discord role
            discordUtil.changeRole(Objects.requireNonNull(event.getGuild()), professionNameOld, professionNameNew, discordUtil.findColorForName(professionNameNew));

            // send JSON file to the log channel
            discordUtil.sendJsonToLogChannel();

            event.reply("Renamed profession **" + professionNameOld + "** to **" + professionNameNew + "**").queue();
            logger.info("Renamed profession: {} to: {}", professionNameOld, professionNameNew);
        } else {
            event.reply("Profession not found: **" + professionNameOld + "**").queue();
            logger.error("Profession abbreviation: {} not found for renaming.", professionNameOld);
        }
    }
}
