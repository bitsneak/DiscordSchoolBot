package at.htlle.discord.service;

import at.htlle.discord.jpa.entity.Client;
import at.htlle.discord.jpa.entity.Enrolment;
import at.htlle.discord.jpa.entity.Teacher;
import at.htlle.discord.jpa.entity.Year;
import at.htlle.discord.jpa.repository.ClientRepository;
import at.htlle.discord.jpa.repository.EnrolmentRepository;
import at.htlle.discord.jpa.repository.TeacherRepository;
import at.htlle.discord.jpa.repository.YearRepository;
import at.htlle.discord.model.enums.BotCommands;
import at.htlle.discord.model.enums.Colors;
import at.htlle.discord.util.DiscordUtil;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class CommandService {
    private static final Logger logger = LogManager.getLogger(CommandService.class);

    @Getter
    @Setter
    private TextChannel commandChannel;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private EnrolmentRepository enrolmentRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private YearRepository yearRepository;

    @Autowired
    private DiscordUtil discordUtil;

    public void handleCommand(SlashCommandInteractionEvent event, BotCommands botCommand) {
        switch (botCommand) {
            case ADD_YEAR -> addYear(event);
            case ADD_CLASS_TEACHER -> addClassTeacher(event);
            case ADD_CLASS -> addClass(event);
            case CHANGE_CLASS_TEACHER -> changeClassTeacher(event);
            case CHANGE_CLASS_CLASS_TEACHER -> changeClassTeacherFromClass(event);
            case CHANGE_CLASS_NAME -> changeClassName(event);
            case PRINT_YEAR -> printYears(event);
            case PRINT_CLASS_TEACHER -> printClassTeachers(event);
            case PRINT_CLASS -> printClasses(event);
            case ROTATE -> rotate(event);
        }
    }

    private void addYear(SlashCommandInteractionEvent event) {
        // get the year from the command option
        String input = Objects.requireNonNull(event.getOption(
                BotCommands.ADD_YEAR.getOptions()
                        .stream()
                        .findFirst()
                        .map(BotCommands.CommandOption::name)
                        .orElseThrow()
        )).getAsString().toUpperCase();

        // check if input is truly an integer
        int year;
        try {
            year = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            event.reply("Input was not an integer").queue();
            return;
        }

        // find or create year
        yearRepository.findByYear(year)
                .ifPresentOrElse(
                        y -> event.reply("Year already exists: **" + year + "**").queue(),
                        () ->
                        {
                            yearRepository.save(new Year(year));
                            event.reply("Added year: **" + input + "**").queue();
                            logger.info("Added class teacher: {}", input);
                        }
                );
    }

    private void addClassTeacher(SlashCommandInteractionEvent event) {
        // get the abbreviation of the class teacher from the command option
        String input = Objects.requireNonNull(event.getOption(
                BotCommands.ADD_CLASS_TEACHER.getOptions()
                        .stream()
                        .findFirst()
                        .map(BotCommands.CommandOption::name)
                        .orElseThrow()
        )).getAsString().toUpperCase();

        // find or create teacher by abbreviation
        teacherRepository.findByAbbreviation(input)
                .ifPresentOrElse(
                        t -> event.reply("Class teacher already exists: **" + input + "**").queue(),
                        () ->
                        {
                            teacherRepository.save(new Teacher(input));
                            event.reply("Added class teacher: **" + input + "**").queue();
                            logger.info("Added class teacher: {}", input);
                        }
                );
    }

    private void addClass(SlashCommandInteractionEvent event) {
        List<String> optionValues = new ArrayList<>();

        // retrieve all the options for this command
        BotCommands.ADD_CLASS.getOptions().forEach(option -> {
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

        enrolmentRepository.findByName(enrolmentName)
                .ifPresentOrElse(
                        e -> event.reply("Class already exists: **" + enrolmentName + "**").queue(),
                        () -> {
                            // find teacher by abbreviation
                            Optional<Teacher> teacherOptional = teacherRepository.findByAbbreviation(teacherAbbreviation);
                            if (teacherOptional.isEmpty()) {
                                event.reply("Class teacher not found: **" + teacherAbbreviation + "**").queue();
                                return;
                            }
                            Teacher teacher = teacherOptional.get();

                            // check if teacher already has a class
                            if (enrolmentRepository.findByTeacher(teacher).isPresent()) {
                                event.reply("Class teacher **" + teacher.getAbbreviation() + "** already has a class").queue();
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
                );
    }

    private void changeClassTeacher(SlashCommandInteractionEvent event) {
        List<String> optionValues = new ArrayList<>();

        // retrieve all the options for this command
        BotCommands.CHANGE_CLASS_TEACHER.getOptions().forEach(option -> {
            // add the option value dynamically based on the option name in the enum
            optionValues.add(Objects.requireNonNull(event.getOption(option.name())).getAsString());
        });

        String teacherAbbreviationOld = optionValues.getFirst().toUpperCase();
        String teacherAbbreviationNew = optionValues.get(1).toUpperCase();

        // check if the new teacher already exists
        Optional<Teacher> existingNewTeacher = teacherRepository.findByAbbreviation(teacherAbbreviationNew);
        if (existingNewTeacher.isPresent()) {
            event.reply("Class teacher already exists: **" + teacherAbbreviationNew + "**").queue();
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

            event.reply("Renamed class teacher **" + teacherAbbreviationOld + "** to **" + teacherAbbreviationNew + "**").queue();
            logger.info("Renamed class teacher: {} to: {}", teacherAbbreviationOld, teacherAbbreviationNew);
        } else {
            event.reply("Class teacher not found: **" + teacherAbbreviationOld + "**").queue();
            logger.error("Class teacher abbreviation: {} not found for renaming.", teacherAbbreviationOld);
        }
    }

    private void changeClassName(SlashCommandInteractionEvent event) {
        List<String> optionValues = new ArrayList<>();

        // retrieve all the options for this command
        BotCommands.CHANGE_CLASS_NAME.getOptions().forEach(option -> {
            // add the option value dynamically based on the option name in the enum
            optionValues.add(Objects.requireNonNull(event.getOption(option.name())).getAsString());
        });

        String enrolmentNameOld = optionValues.getFirst().toUpperCase();
        String enrolmentNameNew = optionValues.get(1).toUpperCase();

        // check if the new enrolment name already exists
        Optional<Enrolment> existingNewEnrolment = enrolmentRepository.findByName(enrolmentNameNew);
        if (existingNewEnrolment.isPresent()) {
            event.reply("Class already exists: **" + enrolmentNameNew + "**").queue();
            return;
        }

        // check if the old enrolment exists
        Optional<Enrolment> existingOldEnrolment = enrolmentRepository.findByName(enrolmentNameOld);
        if (existingOldEnrolment.isPresent()) {
            Enrolment newEnrolment = existingOldEnrolment.get();

            // first character of the class name is always the year
            String classYear = String.valueOf(enrolmentNameNew.charAt(0));

            // find year
            Optional<Year> year = yearRepository.findByYear(Integer.parseInt(classYear));
            if (year.isEmpty()) {
                event.reply("Invalid class year: **" + classYear + "**").queue();
                return;
            }

            // rename enrolment
            newEnrolment.setName(enrolmentNameNew);
            newEnrolment.setYear(year.get());
            enrolmentRepository.save(newEnrolment);

            // change the corresponding Discord role
            discordUtil.assignOrChangeRole(Objects.requireNonNull(event.getGuild()), event.getMember(), enrolmentNameOld, enrolmentNameNew, Colors.CLASS.getColor());

            // send JSON file to the log channel
            discordUtil.sendJsonToLogChannel();

            event.reply("Renamed class from **" + enrolmentNameOld + "** to **" + enrolmentNameNew + "**").queue();
            logger.info("Renamed class: {} to: {}", enrolmentNameOld, enrolmentNameNew);
        } else {
            event.reply("Class not found: **" + enrolmentNameOld + "**").queue();
            logger.error("Class: {} not found for renaming.", enrolmentNameOld);
        }
    }

    private void changeClassTeacherFromClass(SlashCommandInteractionEvent event) {
        List<String> optionValues = new ArrayList<>();

        // retrieve all the options for this command
        BotCommands.CHANGE_CLASS_CLASS_TEACHER.getOptions().forEach(option -> {
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

    private void printYears(SlashCommandInteractionEvent event) {
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

    private void printClassTeachers(SlashCommandInteractionEvent event) {
        List<Teacher> teachers = teacherRepository.findAll();

        // check if there are any teachers
        if (teachers.isEmpty()) {
            event.reply("No class teachers found").queue();
            return;
        }

        // format teachers
        String formattedTeachers = teachers.stream()
                .sorted(Comparator.comparing(Teacher::getAbbreviation))
                .map(teacher -> "- " + teacher.getAbbreviation())
                .collect(Collectors.joining("\n"));

        event.reply("**Class teachers**\n" + formattedTeachers).queue();
    }

    private void printClasses(SlashCommandInteractionEvent event) {
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

        event.reply("**Classes and class teachers**\n" + formattedClasses).queue();
    }

    private void rotate(SlashCommandInteractionEvent event) {
        List<Enrolment> allEnrolments = enrolmentRepository.findAll();
        Guild guild = event.getGuild();

        // separate final year enrolments
        List<Enrolment> finalYearEnrolments = allEnrolments.stream()
                .filter(e -> yearRepository.findByYear(e.getYear().getYear() + 1).isEmpty()) // no next year exists
                .toList();

        // separate non-final year enrolments
        List<Enrolment> nonFinalYearEnrolments = allEnrolments.stream()
                .filter(e -> yearRepository.findByYear(e.getYear().getYear() + 1).isPresent())
                .sorted(Comparator.comparingInt(e -> -e.getYear().getYear())) // sorting by year in descending order
                .toList();

        // handle final year enrolments
        for (Enrolment enrolment : finalYearEnrolments) {
            List<Client> clients = clientRepository.findByEnrolment(enrolment);

            for (Client client : clients) {
                String discordId = client.getDiscordId();

                // kick user
                guild.retrieveMemberById(discordId).queue(
                        member -> guild.kick(member).queue(
                                success -> logger.info("Kicked user: {}", discordId),
                                failure -> logger.error("Failed to kick user: {}", discordId)
                        ),
                        failure -> logger.debug("Member not found: {}", discordId)
                );
            }

            // remove class role from Discord
            String roleName = enrolment.getName();
            guild.getRolesByName(roleName, true).stream()
                    .findFirst()
                    .ifPresent(role -> role.delete().queue(
                            success -> logger.info("Deleted role: {}", roleName),
                            failure -> logger.error("Failed to delete role: {}. Error: {}", roleName, failure.getMessage())
                    ));

            // remove the teacher from the enrolment
            enrolment.setTeacher(null);
            enrolmentRepository.save(enrolment);
        }

        // promote non final year enrolments
        for (Enrolment enrolment : nonFinalYearEnrolments) {
            List<Client> clients = clientRepository.findByEnrolment(enrolment);

            int currentYearValue = enrolment.getYear().getYear();
            int nextYearValue = currentYearValue + 1;

            // get the next year entity
            Year nextYearEntity = yearRepository.findByYear(nextYearValue).get();

            // generate new class role name
            String oldClassRoleName = enrolment.getName();
            String newClassRoleName = nextYearValue + oldClassRoleName.substring(1);

            // the current class teacher
            Teacher teacher = enrolment.getTeacher();

            // delete the teacher from old year
            enrolment.setTeacher(null);
            enrolmentRepository.save(enrolment);

            // check if the new class already exists
            Enrolment nextEnrolment = enrolmentRepository.findByName(newClassRoleName)
                    .orElseGet(() -> {
                        // if the class does not exist, create it with the same teacher as the previous class
                        return enrolmentRepository.save(new Enrolment(newClassRoleName, teacher, nextYearEntity));
                    });

            // ensure the teacher is always updated
            nextEnrolment.setTeacher(teacher);
            enrolmentRepository.save(nextEnrolment);

            // move students to the new enrolment
            for (Client client : clients) {
                client.setEnrolment(nextEnrolment);
                clientRepository.save(client);
            }

            // rename roles
            guild.getRolesByName(oldClassRoleName, true).stream()
                    .findFirst()
                    .ifPresentOrElse(role -> role.getManager().setName(newClassRoleName).queue(
                            success -> logger.info("Renamed class role: {} to: {}", oldClassRoleName, newClassRoleName),
                            error -> logger.error("Failed to rename class role: {}. Error: {}", oldClassRoleName, error.getMessage())
                    ), () -> logger.info("Class role: {} not found to rename", oldClassRoleName));

            // assign new year role and remove old one
            String oldYearRoleName = "Year " + currentYearValue;
            String newYearRoleName = "Year " + nextYearValue;

            for (Client client : clients) {
                String discordId = client.getDiscordId();
                guild.retrieveMemberById(discordId).queue(
                        member -> {
                            // remove old year role
                            guild.getRolesByName(oldYearRoleName, true).stream()
                                    .findFirst()
                                    .ifPresent(role -> guild.removeRoleFromMember(member, role).queue(
                                            success -> logger.info("Removed old year role: {} from user: {}", oldYearRoleName, discordId),
                                            error -> logger.error("Failed to remove old year role: {} from user: {}. Error: {}", oldYearRoleName, discordId, error.getMessage())
                                    ));

                            // assign new year role
                            discordUtil.assignOrChangeRole(guild, member, newYearRoleName, newYearRoleName, Colors.GENERIC.getColor());
                        },
                        failure -> logger.debug("Member not found: {}", discordId)
                );
            }
        }

        // send JSON file to the log channel
        discordUtil.sendJsonToLogChannel();
        event.reply("Rotate command executed").queue();
        logger.info(event.getCommandString() + " command executed by: {}", event.getUser().getIdLong());
    }
}
