package at.htlle.discord.command.impl.change;

import at.htlle.discord.command.CommandAction;
import at.htlle.discord.command.Commands;
import at.htlle.discord.jpa.entity.Enrolment;
import at.htlle.discord.jpa.entity.Teacher;
import at.htlle.discord.jpa.repository.EnrolmentRepository;
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
public class ChangeClassTeacherCommand implements CommandAction {
    private static final Logger logger = LogManager.getLogger(ChangeClassTeacherCommand.class);

    @Autowired
    private EnrolmentRepository enrolmentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private DiscordUtil discordUtil;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<String> optionValues = new ArrayList<>();

        // retrieve all the options for this command
        Commands.CHANGE_CLASS_TEACHER.getOptions().forEach(option -> {
            // add the option value dynamically based on the option name in the enum
            optionValues.add(Objects.requireNonNull(event.getOption(option.name())).getAsString());
        });

        String className = optionValues.getFirst().toUpperCase();
        String teacherAbbreviationNew = optionValues.get(1).toUpperCase();

        // check if the class exists
        Optional<Enrolment> existingEnrolment = enrolmentRepository.findByName(className);
        if (existingEnrolment.isEmpty()) {
            event.reply("Class not found: **" + className + "**").queue();
            return;
        }

        // check if the teacher exists
        Optional<Teacher> existingNewTeacher = teacherRepository.findByAbbreviation(teacherAbbreviationNew);
        if (existingNewTeacher.isEmpty()) {
            event.reply("Class teacher not found: **" + teacherAbbreviationNew + "**").queue();
            return;
        }

        // check if the teacher already has an enrolment
        Teacher newTeacher = existingNewTeacher.get();
        Optional<Enrolment> existingTeacherEnrolment = enrolmentRepository.findByTeacher(newTeacher);
        if (existingTeacherEnrolment.isPresent()) {
            event.reply("Class teacher **" + newTeacher.getAbbreviation() + "** already has class **" + existingTeacherEnrolment.get().getName() + "**").queue();
            return;
        }

        // get the enrolment
        Enrolment enrolment = existingEnrolment.get();
        String teacherAbbreviationOld = enrolment.getTeacher() == null ? "No teacher" : enrolment.getTeacher().getAbbreviation();

        // update the class teacher and enrolment
        enrolment.setTeacher(newTeacher);
        enrolmentRepository.save(enrolment);

        // send JSON file to the log channel
        discordUtil.sendJsonToLogChannel();

        event.reply("Class teacher changed for class **" + className + "** from **" + teacherAbbreviationOld + "** to **" + teacherAbbreviationNew + "**").queue();
        logger.info("Class teacher changed for class: {} from: {} to: {}", className, teacherAbbreviationOld, teacherAbbreviationNew);
    }
}
