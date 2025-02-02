package at.htlle.discord.command.impl.add;

import at.htlle.discord.command.CommandAction;
import at.htlle.discord.command.Commands;
import at.htlle.discord.jpa.entity.Enrolment;
import at.htlle.discord.jpa.entity.Teacher;
import at.htlle.discord.jpa.entity.Year;
import at.htlle.discord.jpa.repository.EnrolmentRepository;
import at.htlle.discord.jpa.repository.TeacherRepository;
import at.htlle.discord.jpa.repository.YearRepository;
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
public class AddClassCommand implements CommandAction {
    private static final Logger logger = LogManager.getLogger(AddClassCommand.class);

    @Autowired
    private EnrolmentRepository enrolmentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private YearRepository yearRepository;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<String> optionValues = new ArrayList<>();

        // retrieve all the options for this command
        Commands.ADD_CLASS.getOptions().forEach(option -> {
            // add the option value dynamically based on the option name in the enum
            optionValues.add(Objects.requireNonNull(event.getOption(option.name())).getAsString());
        });

        String enrolmentName = optionValues.getFirst().toUpperCase();
        String teacherAbbreviation = optionValues.get(1).toUpperCase();

        // check if the class name matches the pattern (starts with a number followed by other characters)
        if (!enrolmentName.matches("^\\d\\D.*")) {
            event.reply("Invalid class name format **" + enrolmentName + "**. Must start with an integer followed by other characters").queue();
            return;
        }

        // check if the enrolment is already there
        if (enrolmentRepository.findByName(enrolmentName).isPresent()) {
            event.reply("Class already exists: **" + enrolmentName + "**").queue();
            return;
        }

        // find teacher by abbreviation
        Optional<Teacher> teacherOptional = teacherRepository.findByAbbreviation(teacherAbbreviation);
        if (teacherOptional.isEmpty()) {
            event.reply("Teacher not found: **" + teacherAbbreviation + "**").queue();
            return;
        }
        Teacher teacher = teacherOptional.get();

        // check if teacher already has a class
        if (enrolmentRepository.findByTeacher(teacher).isPresent()) {
            event.reply("Teacher **" + teacher.getAbbreviation() + "** already has a class").queue();
            return;
        }

        // first character of the class name is always the year
        String classYear = String.valueOf(enrolmentName.charAt(0));

        // find year
        Optional<Year> year = yearRepository.findByYear(Integer.parseInt(classYear));
        if (year.isEmpty()) {
            event.reply("Invalid class year: **" + classYear + "**").queue();
            return;
        }

        // save enrolment
        enrolmentRepository.save(new Enrolment(enrolmentName, teacher, year.get()));
        event.reply("Added class **" + enrolmentName + "** with teacher **" + teacher.getAbbreviation() + "**").queue();
        logger.info("Added class: {} with teacher: {}", enrolmentName, teacher.getAbbreviation());
    }
}
