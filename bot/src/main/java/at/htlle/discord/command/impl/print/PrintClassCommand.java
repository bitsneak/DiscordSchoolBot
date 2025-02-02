package at.htlle.discord.command.impl.print;

import at.htlle.discord.command.CommandAction;
import at.htlle.discord.jpa.entity.Enrolment;
import at.htlle.discord.jpa.repository.EnrolmentRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PrintClassCommand implements CommandAction {
    @Autowired
    private EnrolmentRepository enrolmentRepository;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<Enrolment> enrolments = enrolmentRepository.findAll();

        // check if there are any classes
        if (enrolments.isEmpty()) {
            event.reply("No classes found").queue();
            return;
        }

        // format enrolments with their class teachers
        String formattedClasses = enrolments.stream()
                .sorted(Comparator.comparing(Enrolment::getName))
                .map(enrolment -> "- " + enrolment.getName() + ": " +
                        (enrolment.getTeacher() != null ? enrolment.getTeacher().getAbbreviation() : "No teacher"))
                .collect(Collectors.joining("\n"));

        event.reply("**Classes and their teachers**\n" + formattedClasses).queue();
    }
}
