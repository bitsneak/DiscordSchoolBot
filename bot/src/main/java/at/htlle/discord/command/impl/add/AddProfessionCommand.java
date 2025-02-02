package at.htlle.discord.command.impl.add;

import at.htlle.discord.command.CommandAction;
import at.htlle.discord.command.Commands;
import at.htlle.discord.jpa.entity.Profession;
import at.htlle.discord.jpa.repository.ProfessionRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AddProfessionCommand implements CommandAction {
    private static final Logger logger = LogManager.getLogger(AddProfessionCommand.class);

    @Autowired
    ProfessionRepository professionRepository;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // get the name of the profession from the command option
        String input = Objects.requireNonNull(event.getOption(
                Commands.ADD_PROFESSION.getOptions()
                        .stream()
                        .findFirst()
                        .map(Commands.CommandOption::name)
                        .orElseThrow()
        )).getAsString().toUpperCase();

        // find or create profession by name
        professionRepository.findByName(input)
                .ifPresentOrElse(
                        t -> event.reply("Profession already exists: **" + input + "**").queue(),
                        () ->
                        {
                            professionRepository.save(new Profession(input));
                            event.reply("Added profession: **" + input + "**").queue();
                            logger.info("Added profession: {}", input);
                        }
                );
    }
}
