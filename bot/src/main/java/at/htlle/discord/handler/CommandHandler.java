package at.htlle.discord.handler;

import at.htlle.discord.command.Commands;
import at.htlle.discord.command.CommandService;
import lombok.Getter;
import lombok.Setter;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class CommandHandler extends ListenerAdapter {
    private static final Logger logger = LogManager.getLogger(CommandHandler.class);

    @Autowired
    @Getter
    private CommandService commandService;

    @Setter
    private TextChannel commandChannel;

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getChannel().equals(commandChannel)) {
            return; // ignore commands from other channels
        }

        String command = event.getName();
        String subcommand = event.getSubcommandName();

        // check if the command is a valid bot command
        for (Commands botCommand : Commands.values()) {
            if (botCommand.matches(command, subcommand)) {
                commandService.handleCommand(event, botCommand);
                return;
            }
        }

        event.reply("Unknown command.").queue();
        logger.debug("Unknown command: {} (subcommand: {})", command, subcommand);
    }
}
