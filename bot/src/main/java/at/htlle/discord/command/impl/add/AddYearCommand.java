package at.htlle.discord.command.impl.add;

import at.htlle.discord.command.CommandAction;
import at.htlle.discord.command.Commands;
import at.htlle.discord.jpa.entity.Year;
import at.htlle.discord.jpa.repository.YearRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AddYearCommand implements CommandAction {
    private static final Logger logger = LogManager.getLogger(AddYearCommand.class);

    @Autowired
    private YearRepository yearRepository;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // get the year from the command option
        String input = Objects.requireNonNull(event.getOption(
                Commands.ADD_YEAR.getOptions()
                        .stream()
                        .findFirst()
                        .map(Commands.CommandOption::name)
                        .orElseThrow()
        )).getAsString().toUpperCase();

        // check if input is truly an integer
        int year;
        try {
            year = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            event.reply("Input was not an integer").queue();
            return;
        }

        // find or create year
        yearRepository.findByYear(year)
                .ifPresentOrElse(
                        y -> event.reply("Year already exists: **" + year + "**").queue(),
                        () ->
                        {
                            yearRepository.save(new Year(year));
                            event.reply("Added year: **" + input + "**").queue();
                            logger.info("Added class teacher: {}", input);
                        }
                );
    }
}
