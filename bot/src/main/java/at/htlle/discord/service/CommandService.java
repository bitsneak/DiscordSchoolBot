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
import at.htlle.discord.model.enums.Years;
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
            case ADD_CLASS_TEACHER -> addClassTeacher(event);
            case ADD_CLASS -> addClass(event);
            case CHANGE_CLASS_TEACHER -> changeClassTeacher(event);
            case CHANGE_CLASS_CLASS_TEACHER -> changeClassTeacherFromClass(event);
            case CHANGE_CLASS_NAME -> changeClassName(event);
            case PRINT_CLASS_TEACHER -> printClassTeacher(event);
            case PRINT_CLASS -> printClass(event);
            case ROTATE -> rotate(event);
        }
    }

    private void addClassTeacher(SlashCommandInteractionEvent event) {
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
                        t -> event.reply("Class teacher already exists: **" + abbreviation + "**").queue(),
                        () ->
                        {
                            teacherRepository.save(new Teacher(abbreviation));
                            event.reply("Added class teacher: **" + abbreviation + "**").queue();
                            logger.info("Added class teacher: {}", abbreviation);
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

        String className = optionValues.getFirst().toUpperCase();
        String teacherAbbreviation = optionValues.get(1).toUpperCase();

        // check if the class name matches the pattern (starts with a number followed by other characters)
        if (!className.matches("^\\d\\D.*")) {
            event.reply("Invalid class name format **" + className + "**. Must start with a number followed by other characters").queue();
            return;
        }

        enrolmentRepository.findByName(className)
                .ifPresentOrElse(
                        e -> event.reply("Class already exists: **" + className + "**").queue(),
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
                            String classYear = String.valueOf(className.charAt(0));
                            Optional<Years> yearEnumOptional = Arrays.stream(Years.values()).
                                    filter(y -> y.getYear().equalsIgnoreCase(classYear)).findFirst();

                            // check if the year is valid
                            if (yearEnumOptional.isEmpty()) {
                                event.reply("Invalid class year / name: **" + className + "**").queue();
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
                            event.reply("Added class **" + className + "** with teacher **" + teacher.getAbbreviation() + "**").queue();
                            logger.info("Added class: {} with teacher: {}", className, teacher.getAbbreviation());
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

            // rename enrolment
            newEnrolment.setName(enrolmentNameNew);
            enrolmentRepository.save(newEnrolment);

            // reset class teacher of now old class
            //Enrolment oldEnrolment = enrolmentRepository.findByName(enrolmentNameOld).get();
            //oldEnrolment.setTeacher(null);
            //enrolmentRepository.save(newEnrolment);

            // change the corresponding Discord role
            discordUtil.assignOrChangeRole(event.getGuild(), event.getMember(), enrolmentNameOld, enrolmentNameNew, Colors.CLASS.getColor());

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

    private void printClassTeacher(SlashCommandInteractionEvent event) {
        List<Teacher> teachers = teacherRepository.findAll();

        // check if there are any teachers
        if (teachers.isEmpty()) {
            event.reply("No class teachers found").queue();
            return;
        }

        // format teachers
        String teacherNames = teachers.stream()
                .sorted(Comparator.comparing(Teacher::getAbbreviation))
                .map(teacher -> "- " + teacher.getAbbreviation())
                .collect(Collectors.joining("\n"));

        event.reply("**Class teachers**\n" + teacherNames).queue();
    }

    private void printClass(SlashCommandInteractionEvent event) {
        List<Enrolment> enrolments = enrolmentRepository.findAll();

        // check if there are any classes
        if (enrolments.isEmpty()) {
            event.reply("No classes found").queue();
            return;
        }

        // format enrolments with their class teachers
        String classList = enrolments.stream()
                .sorted(Comparator.comparing(Enrolment::getName))
                .map(enrolment -> "- " + enrolment.getName() + ": " +
                        (enrolment.getTeacher() != null ? enrolment.getTeacher().getAbbreviation() : "No teacher"))
                .collect(Collectors.joining("\n"));

        event.reply("**Classes and class teachers**\n" + classList).queue();
    }

    private void rotate(SlashCommandInteractionEvent event) {
        List<Enrolment> allEnrolments = enrolmentRepository.findAll();
        Guild guild = event.getGuild();

        // separate final year enrolments
        List<Enrolment> finalYearEnrolments = allEnrolments.stream()
                .filter(e -> e.getYear().getYear().getNextYear() == null)
                .toList();

        // separate non final year enrolments
        List<Enrolment> nonFinalYearEnrolments = allEnrolments.stream()
                .filter(e -> e.getYear().getYear().getNextYear() != null)
                .sorted((e1, e2) -> e2.getYear().getYear().compareTo(e1.getYear().getYear())) // sorting by year in descending order
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

            Years currentYear = enrolment.getYear().getYear();
            Years nextYear = currentYear.getNextYear();

            Year nextYearEntity = yearRepository.findByYear(nextYear)
                    .orElseGet(() -> yearRepository.save(new Year(nextYear)));

            String oldClassRoleName = enrolment.getName();
            String newClassRoleName = nextYear.getYear() + oldClassRoleName.substring(1);

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
            String oldYearRoleName = "Year " + currentYear.getYear();
            String newYearRoleName = "Year " + nextYear.getYear();

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
