package at.htlle.discord.command.impl.change;

import at.htlle.discord.command.CommandAction;
import at.htlle.discord.command.Commands;
import at.htlle.discord.jpa.entity.Enrolment;
import at.htlle.discord.jpa.entity.Year;
import at.htlle.discord.jpa.repository.EnrolmentRepository;
import at.htlle.discord.jpa.repository.YearRepository;
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
public class ChangeClassNameCommand implements CommandAction {
    private static final Logger logger = LogManager.getLogger(ChangeClassNameCommand.class);

    @Autowired
    private EnrolmentRepository enrolmentRepository;

    @Autowired
    private YearRepository yearRepository;

    @Autowired
    private DiscordUtil discordUtil;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<String> optionValues = new ArrayList<>();

        // retrieve all the options for this command
        Commands.CHANGE_CLASS_NAME.getOptions().forEach(option -> {
            // add the option value dynamically based on the option name in the enum
            optionValues.add(Objects.requireNonNull(event.getOption(option.name())).getAsString());
        });

        String enrolmentNameOld = optionValues.getFirst().toUpperCase();
        String enrolmentNameNew = optionValues.get(1).toUpperCase();

        // check if the new enrolment name already exists
        Optional<Enrolment> existingNewEnrolment = enrolmentRepository.findByName(enrolmentNameNew);
        if (existingNewEnrolment.isPresent()) {
            event.reply("Class already exists: **" + enrolmentNameNew + "**").queue();
            return;
        }

        // check if the old enrolment exists
        Optional<Enrolment> existingOldEnrolment = enrolmentRepository.findByName(enrolmentNameOld);
        if (existingOldEnrolment.isPresent()) {
            Enrolment newEnrolment = existingOldEnrolment.get();

            // check if input is truly an integer
            int yearNum;
            try {
                // first character of the class name is always the year
                yearNum = Integer.parseInt(String.valueOf(enrolmentNameNew.charAt(0)));
            } catch (NumberFormatException e) {
                event.reply("Input was not an integer").queue();
                return;
            }

            // find year
            Optional<Year> year = yearRepository.findByYear(yearNum);
            if (year.isEmpty()) {
                event.reply("Invalid class year: **" + yearNum + "**").queue();
                return;
            }

            // rename enrolment
            newEnrolment.setName(enrolmentNameNew);
            newEnrolment.setYear(year.get());
            enrolmentRepository.save(newEnrolment);

            // change the corresponding Discord role
            discordUtil.changeRole(Objects.requireNonNull(event.getGuild()), enrolmentNameOld, enrolmentNameNew, discordUtil.findColorForName(enrolmentNameNew));

            // send JSON file to the log channel
            discordUtil.sendJsonToLogChannel();

            event.reply("Renamed class from **" + enrolmentNameOld + "** to **" + enrolmentNameNew + "**").queue();
            logger.info("Renamed class: {} to: {}", enrolmentNameOld, enrolmentNameNew);
        } else {
            event.reply("Class not found: **" + enrolmentNameOld + "**").queue();
            logger.error("Class: {} not found for renaming.", enrolmentNameOld);
        }
    }
}
