package at.htlle.discord.bot;

import at.htlle.discord.model.enums.BotCommands;
import at.htlle.discord.service.CommandService;
import at.htlle.discord.service.LoginService;
import lombok.Getter;
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

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getChannel().equals(commandService.getCommandChannel())) {
            return; // ignore commands from other channels
        }

        String command = event.getName();
        String subcommand = event.getSubcommandName();

        // check if the command is a valid bot command
        for (BotCommands botCommand : BotCommands.values()) {
            if (botCommand.matches(command, subcommand)) {
                commandService.handleCommand(event, botCommand);
                return;
            }
        }

        event.reply("Unknown command.").queue();
        logger.debug("Unhandled command: {} (subcommand: {})", command, subcommand);
    }
}
