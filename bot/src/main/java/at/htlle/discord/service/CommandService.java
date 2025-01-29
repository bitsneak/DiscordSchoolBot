package at.htlle.discord.service;

import at.htlle.discord.bot.LoginHandler;
import at.htlle.discord.jpa.entity.Client;
import at.htlle.discord.jpa.entity.Enrolment;
import at.htlle.discord.jpa.entity.Teacher;
import at.htlle.discord.jpa.entity.Year;
import at.htlle.discord.jpa.repository.ClientRepository;
import at.htlle.discord.jpa.repository.EnrolmentRepository;
import at.htlle.discord.jpa.repository.TeacherRepository;
import at.htlle.discord.jpa.repository.YearRepository;
import at.htlle.discord.model.enums.BotCommands;
import at.htlle.discord.model.enums.Years;
import at.htlle.discord.util.DiscordUtil;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
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
    private LoginHandler loginHandler;

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

                            // TODO only let class names be allowed where only the first character is a number (year)
                            // TODO after the year number there should be something else -> so not just the year number
                            // first character of the class name is always the year
                            String classYear = String.valueOf(className.charAt(0));
                            Optional<Years> yearEnumOptional = Arrays.stream(Years.values()).
                                    filter(y -> y.getYear().equalsIgnoreCase(classYear)).findFirst();
                            // check if the year is valid
                            if (yearEnumOptional.isEmpty()) {
                                event.reply("Invalid class year / name: " + className).queue();
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
        // get all enrolments
        List<Enrolment> allEnrolments = enrolmentRepository.findAll();
        Guild guild = event.getGuild();

        // iterate over all enrolments
        for (Enrolment enrolment : allEnrolments) {
            // get clients for the enrolment (if any)
            List<Client> clients = clientRepository.findByEnrolment(enrolment);

            Years currentYear = enrolment.getYear().getYear();
            Years nextYear = currentYear.getNextYear();

            // if next year is null, handle the deletion and cleanup
            if (nextYear == null) {
                // first, handle clients of this enrolment (if there are any)
                for (Client client : clients) {
                    String discordId = client.getDiscordId();

                    guild.retrieveMemberById(discordId).queue(
                            member -> {
                                // kick the user if the member is found
                                guild.kick(member).queue(
                                        success -> logger.info("Kicked user: {}", discordId),
                                        failure -> logger.error("Failed to kick user: {}", discordId)
                                );
                            },
                            failure -> logger.debug("Member not found or unable to retrieve: {}", discordId)
                    );

                    // remove the client from the database
                    clientRepository.delete(client);
                    logger.info("Removed client: {}", client.getId());
                }

                // remove the corresponding role from discord
                String roleName = enrolment.getName();
                guild.getRolesByName(roleName, true).stream()
                        .findFirst()
                        .ifPresent(role -> {
                            // delete the role from discord
                            role.delete().queue(
                                    success -> logger.info("Deleted role: {}", roleName),
                                    failure -> logger.error("Failed to delete role {}. Error: {}", roleName, failure.getMessage())
                            );
                        });

                // remove the enrolment from the database
                enrolmentRepository.delete(enrolment);
                logger.info("Deleted enrolment: {}", enrolment.getName());

            } else {
                // promote the enrolment to the next year
                // if the next year does not exist, create it
                Year nextYearEntity = yearRepository.findByYear(nextYear)
                        .orElseGet(() -> yearRepository.save(new Year(nextYear)));

                // old role name has to be set before updating the enrolment name
                String oldRoleName = enrolment.getName();

                // update the enrolment with the new year
                enrolment.setYear(nextYearEntity);
                enrolment.updateNameWithYear(nextYear);
                enrolmentRepository.save(enrolment);

                // get the new role name
                String newRoleName = enrolment.getName();

                // find the existing role by the old name and rename it
                guild.getRolesByName(oldRoleName, true).stream()
                        .findFirst()
                        .ifPresentOrElse(role -> {
                            role.getManager().setName(newRoleName).queue(
                                    success -> logger.info("Successfully renamed role {} to {}", oldRoleName, newRoleName),
                                    error -> logger.error("Failed to rename role {}. Error: {}", oldRoleName, error.getMessage())
                            );
                        }, () -> {
                            logger.info("Role {} not found to rename", oldRoleName);
                        });
            }
        }

        // TODO sends JSON multiple times. Once here, this should be, but everytime a member is kicked, it sends the JSON again -> this should not be
        discordUtil.sendJsonToLogChannel();
        event.reply("Rotate command executed!").queue();
        logger.info(event.getCommandString() + " command executed by {}", event.getUser().getIdLong());
    }
}
