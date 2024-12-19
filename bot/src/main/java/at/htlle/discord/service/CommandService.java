package at.htlle.discord.service;

import at.htlle.discord.jpa.entity.Enrolment;
import at.htlle.discord.jpa.entity.Teacher;
import at.htlle.discord.jpa.entity.Year;
import at.htlle.discord.jpa.repository.EnrolmentRepository;
import at.htlle.discord.jpa.repository.TeacherRepository;
import at.htlle.discord.jpa.repository.YearRepository;
import at.htlle.discord.model.enums.BotCommands;
import at.htlle.discord.model.enums.Years;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CommandService {
    private static final Logger logger = LogManager.getLogger(CommandService.class);

    @Getter
    @Setter
    private TextChannel commandChannel;

    @Autowired
    private EnrolmentRepository enrolmentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private YearRepository yearRepository;


    public void handleCommand(SlashCommandInteractionEvent event, BotCommands botCommand) {
        switch (botCommand) {
            case ADD_CLASS_TEACHER -> handleAddClassTeacher(event);
            case ADD_CLASS -> handleAddClass(event);
            case ROTATE -> handleRotate(event);
        }
    }

    private void handleAddClassTeacher(SlashCommandInteractionEvent event) {
        // get the abbreviation of the class teacher from the command option
        String abbreviation = Objects.requireNonNull(event.getOption(
                BotCommands.ADD_CLASS_TEACHER.getOptions()
                        .stream()
                        .findFirst()
                        .map(BotCommands.CommandOption::name)
                        .orElseThrow()
        )).getAsString().toUpperCase();

        // find or create teacher by abbreviation
        teacherRepository.findByAbbreviation(abbreviation)
                .ifPresentOrElse(
                        t -> event.reply("Class teacher already exists: " + abbreviation).queue(),
                        () ->
                        {
                            teacherRepository.save(new Teacher(abbreviation));
                            event.reply("Added class teacher: " + abbreviation).queue();
                            logger.info("Added class teacher: {}", abbreviation);
                        }
                );
    }

    private void handleAddClass(SlashCommandInteractionEvent event) {
        // use linked hash set to maintain the order of the option values
        List<String> optionValues = new ArrayList<>();

        // retrieve all the options for this command
        BotCommands.ADD_CLASS.getOptions().forEach(option -> {
            // add the option value dynamically based on the option name in the enum
            optionValues.add(Objects.requireNonNull(event.getOption(option.name())).getAsString());
        });

        String className = optionValues.getFirst().toUpperCase();
        String teacherAbbreviation = optionValues.get(1).toUpperCase();

        enrolmentRepository.findByName(className)
                .ifPresentOrElse(
                        e -> event.reply("Class already exists: " + className).queue(),
                        () -> {
                            // find teacher by abbreviation
                            Optional<Teacher> teacherOptional = teacherRepository.findByAbbreviation(teacherAbbreviation);
                            if (teacherOptional.isEmpty()) {
                                event.reply("Class teacher not found: " + teacherAbbreviation).queue();
                                return;
                            }
                            Teacher teacher = teacherOptional.get();

                            // check if teacher already has a class
                            if (enrolmentRepository.findByClassTeacher(teacher).isPresent()) {
                                event.reply("Teacher " + teacher.getAbbreviation() + " has already a class").queue();
                                return;
                            }

                            // first character of the class name is always the year
                            String classYear = String.valueOf(className.charAt(0));
                            Optional<Years> yearEnumOptional = Arrays.stream(Years.values()).
                                    filter(y -> y.getName().equalsIgnoreCase(classYear)).findFirst();
                            // check if the year is valid
                            if (yearEnumOptional.isEmpty()) {
                                event.reply("Invalid class year: " + className).queue();
                                return;
                            }
                            Years yearEnum = yearEnumOptional.get();

                            // find or create year
                            Optional<Year> yearOptional = yearRepository.findByYear(yearEnum);
                            if (yearOptional.isEmpty()) {
                                yearRepository.save(new Year(yearEnum));
                            }
                            Year year = yearRepository.findByYear(yearEnum).get();

                            // save enrolment
                            enrolmentRepository.save(new Enrolment(className, teacher, year));
                            event.reply("Added class: " + className + " with teacher: " + teacher.getAbbreviation()).queue();
                            logger.info("Added class: {} with teacher: {}", className, teacher.getAbbreviation());
                        }
                );
    }

    private void handleRotate(SlashCommandInteractionEvent event) {
        // TODO

        event.reply("Rotate command executed!").queue();
        logger.info("/rotate command executed by {}", event.getUser().getIdLong());
    }
}
