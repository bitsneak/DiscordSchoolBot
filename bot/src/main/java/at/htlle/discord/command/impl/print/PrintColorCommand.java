package at.htlle.discord.command.impl.print;

import at.htlle.discord.command.CommandAction;
import at.htlle.discord.command.impl.change.ChangeProfessionCommand;
import at.htlle.discord.jpa.entity.Color;
import at.htlle.discord.jpa.entity.Enrolment;
import at.htlle.discord.jpa.repository.ColorRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PrintColorCommand implements CommandAction {
    private static final Logger logger = LogManager.getLogger(PrintColorCommand.class);

    @Autowired
    private ColorRepository colorRepository;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<Color> colors = colorRepository.findAll();

        // check if there are any classes
        if (colors.isEmpty()) {
            event.reply("No colors found").queue();
            return;
        }

        // format colors with their scopes
        String formattedClasses = colors.stream()
                .sorted(Comparator.comparing(Color::getScope))
                .map(color -> "- " + color.getScope() + ": " + color.getColor())
                .collect(Collectors.joining("\n"));

        event.reply("**Colors and scopes**\n" + formattedClasses).queue();
    }
}
