package at.htlle.discord.command.impl.print;

import at.htlle.discord.command.CommandAction;
import at.htlle.discord.jpa.entity.Profession;
import at.htlle.discord.jpa.repository.ProfessionRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PrintProfessionCommand implements CommandAction {
    @Autowired
    private ProfessionRepository professionRepository;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<Profession> professions = professionRepository.findAll();

        // check if there are any professions
        if (professions.isEmpty()) {
            event.reply("No professions found").queue();
            return;
        }

        // format professions
        String formattedTeachers = professions.stream()
                .sorted(Comparator.comparing(Profession::getName))
                .map(profession -> "- " + profession.getName())
                .collect(Collectors.joining("\n"));

        event.reply("**Professions**\n" + formattedTeachers).queue();
    }
}
