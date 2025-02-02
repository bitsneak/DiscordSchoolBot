package at.htlle.discord.command.impl.change;

import at.htlle.discord.command.CommandAction;
import at.htlle.discord.command.Commands;
import at.htlle.discord.jpa.entity.Teacher;
import at.htlle.discord.jpa.repository.TeacherRepository;
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
public class ChangeTeacherCommand implements CommandAction {
    private static final Logger logger = LogManager.getLogger(ChangeTeacherCommand.class);

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private DiscordUtil discordUtil;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<String> optionValues = new ArrayList<>();

        // retrieve all the options for this command
        Commands.CHANGE_TEACHER.getOptions().forEach(option -> {
            // add the option value dynamically based on the option name in the enum
            optionValues.add(Objects.requireNonNull(event.getOption(option.name())).getAsString());
        });

        String teacherAbbreviationOld = optionValues.getFirst().toUpperCase();
        String teacherAbbreviationNew = optionValues.get(1).toUpperCase();

        // check if the new teacher already exists
        Optional<Teacher> existingNewTeacher = teacherRepository.findByAbbreviation(teacherAbbreviationNew);
        if (existingNewTeacher.isPresent()) {
            event.reply("Teacher already exists: **" + teacherAbbreviationNew + "**").queue();
            return;
        }

        // check if the old teacher exists
        Optional<Teacher> existingOldTeacher = teacherRepository.findByAbbreviation(teacherAbbreviationOld);
        if (existingOldTeacher.isPresent()) {
            Teacher oldTeacher = existingOldTeacher.get();
            // rename teacher
            oldTeacher.setAbbreviation(teacherAbbreviationNew);
            teacherRepository.save(oldTeacher);

            // send JSON file to the log channel
            discordUtil.sendJsonToLogChannel();

            event.reply("Renamed teacher **" + teacherAbbreviationOld + "** to **" + teacherAbbreviationNew + "**").queue();
            logger.info("Renamed teacher: {} to: {}", teacherAbbreviationOld, teacherAbbreviationNew);
        } else {
            event.reply("Teacher not found: **" + teacherAbbreviationOld + "**").queue();
            logger.error("Teacher abbreviation: {} not found for renaming.", teacherAbbreviationOld);
        }
    }
}
