package at.htlle.discord.command.impl.print;

import at.htlle.discord.command.CommandAction;
import at.htlle.discord.jpa.entity.Teacher;
import at.htlle.discord.jpa.repository.TeacherRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PrintTeacherCommand implements CommandAction {
    @Autowired
    private TeacherRepository teacherRepository;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<Teacher> teachers = teacherRepository.findAll();

        // check if there are any teachers
        if (teachers.isEmpty()) {
            event.reply("No teachers found").queue();
            return;
        }

        // format teachers
        String formattedTeachers = teachers.stream()
                .sorted(Comparator.comparing(Teacher::getAbbreviation))
                .map(teacher -> "- " + teacher.getAbbreviation())
                .collect(Collectors.joining("\n"));

        event.reply("**Teachers**\n" + formattedTeachers).queue();
    }
}
