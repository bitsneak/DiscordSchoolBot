package at.htlle.discord.command.impl.add;

import at.htlle.discord.command.CommandAction;
import at.htlle.discord.command.Commands;
import at.htlle.discord.jpa.entity.Teacher;
import at.htlle.discord.jpa.repository.TeacherRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AddTeacherCommand implements CommandAction {
    private static final Logger logger = LogManager.getLogger(AddTeacherCommand.class);

    @Autowired
    private TeacherRepository teacherRepository;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // get the abbreviation of the class teacher from the command option
        String input = Objects.requireNonNull(event.getOption(
                Commands.ADD_TEACHER.getOptions()
                        .stream()
                        .findFirst()
                        .map(Commands.CommandOption::name)
                        .orElseThrow()
        )).getAsString().toUpperCase();

        // find or create teacher by abbreviation
        teacherRepository.findByAbbreviation(input)
                .ifPresentOrElse(
                        t -> event.reply("Teacher already exists: **" + input + "**").queue(),
                        () ->
                        {
                            teacherRepository.save(new Teacher(input));
                            event.reply("Added teacher: **" + input + "**").queue();
                            logger.info("Added teacher: {}", input);
                        }
                );
    }
}
