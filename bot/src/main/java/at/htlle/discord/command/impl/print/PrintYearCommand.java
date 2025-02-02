package at.htlle.discord.command.impl.print;

import at.htlle.discord.command.CommandAction;
import at.htlle.discord.jpa.entity.Year;
import at.htlle.discord.jpa.repository.YearRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PrintYearCommand implements CommandAction {
    @Autowired
    private YearRepository yearRepository;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<Year> years = yearRepository.findAll();

        // check if there are any years
        if (years.isEmpty()) {
            event.reply("No years found").queue();
            return;
        }

        // format years
        String formattedYears = years.stream()
                .sorted(Comparator.comparing(Year::getYear))
                .map(year -> "- " + year.getYear())
                .collect(Collectors.joining("\n"));

        event.reply("**Years**\n" + formattedYears).queue();
    }
}
