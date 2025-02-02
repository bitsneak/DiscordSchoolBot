package at.htlle.discord.command.impl.rotate;

import at.htlle.discord.command.CommandAction;
import at.htlle.discord.jpa.entity.Client;
import at.htlle.discord.jpa.entity.Enrolment;
import at.htlle.discord.jpa.entity.Teacher;
import at.htlle.discord.jpa.entity.Year;
import at.htlle.discord.jpa.repository.ClientRepository;
import at.htlle.discord.jpa.repository.EnrolmentRepository;
import at.htlle.discord.jpa.repository.YearRepository;
import at.htlle.discord.util.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class RotateCommand implements CommandAction {
    private static final Logger logger = LogManager.getLogger(RotateCommand.class);

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private EnrolmentRepository enrolmentRepository;

    @Autowired
    private YearRepository yearRepository;

    @Autowired
    private DiscordUtil discordUtil;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<Enrolment> allEnrolments = enrolmentRepository.findAll();
        Guild guild = Objects.requireNonNull(event.getGuild());

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

            // rename class role if it exists
            guild.getRolesByName(oldClassRoleName, true).stream()
                    .findFirst()
                    .ifPresent(role -> discordUtil.changeRole(guild, oldClassRoleName, newClassRoleName, role.getColor()));

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
                                    .ifPresent(r -> guild.removeRoleFromMember(member, r).queue(
                                            success -> logger.info("Removed old year role: {} from user: {}", oldYearRoleName, discordId),
                                            error -> logger.error("Failed to remove old year role: {} from user: {}. Error: {}", oldYearRoleName, discordId, error.getMessage())
                                    ));

                            // create / assign new year role
                            guild.getRolesByName(newYearRoleName, true).stream()
                                    .findFirst()
                                    .ifPresentOrElse(
                                            role -> discordUtil.assignRole(guild, member, role.getName()),
                                            () -> discordUtil.createRole(guild, newYearRoleName, discordUtil.findColorForName(newYearRoleName), createdRole -> {
                                                discordUtil.assignRole(guild, member, createdRole.getName());
                                            })
                                    );

                            // create / rename class role
                            guild.getRolesByName(oldClassRoleName, true).stream()
                                    .findFirst()
                                    .ifPresentOrElse(
                                            role -> discordUtil.changeRole(guild, oldClassRoleName, newClassRoleName, role.getColor()),
                                            () -> discordUtil.createRole(guild, newClassRoleName, discordUtil.findColorForName(newClassRoleName), createdRole -> {
                                                discordUtil.assignRole(guild, member, createdRole.getName());
                                            })
                                    );
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
