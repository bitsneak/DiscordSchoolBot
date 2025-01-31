package at.htlle.discord.bot;

import at.htlle.discord.model.enums.BotCommands;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CommandInitializer {
    public List<CommandData> initializeCommands() {
        List<CommandData> commands = new ArrayList<>();

        // add add-command with subcommands
        commands.add(Commands
                .slash(BotCommands.ADD_YEAR.getCommand(), "Adds a year, class and class teacher")
                .addSubcommands(
                        createSubcommand(BotCommands.ADD_YEAR),
                        createSubcommand(BotCommands.ADD_CLASS_TEACHER),
                        createSubcommand(BotCommands.ADD_CLASS)
                ));

        // add change-command with subcommands
        commands.add(Commands
                .slash(BotCommands.CHANGE_CLASS_CLASS_TEACHER.getCommand(), "Change a class and class teacher")
                .addSubcommands(
                        createSubcommand(BotCommands.CHANGE_CLASS_TEACHER),
                        createSubcommand(BotCommands.CHANGE_CLASS_NAME),
                        createSubcommand(BotCommands.CHANGE_CLASS_CLASS_TEACHER)
                ));

        // add print-command with subcommands
        commands.add(Commands
                .slash(BotCommands.PRINT_YEAR.getCommand(), "Prints out all years, classes and class teachers")
                .addSubcommands(
                        createSubcommand(BotCommands.PRINT_YEAR),
                        createSubcommand(BotCommands.PRINT_CLASS_TEACHER),
                        createSubcommand(BotCommands.PRINT_CLASS)
                ));

        // add rotation command
        commands.add(Commands
                .slash(BotCommands.ROTATE.getCommand(), BotCommands.ROTATE.getDescription()));

        return commands;
    }

    // create SubcommandData from BotCommands enum
    private SubcommandData createSubcommand(BotCommands botCommand) {
        SubcommandData subcommandData = new SubcommandData(botCommand.getSubcommand(), botCommand.getDescription());

        for (BotCommands.CommandOption option : botCommand.getOptions()) {
            subcommandData.addOption(OptionType.STRING, option.name(), option.description(), option.required());
        }

        return subcommandData;
    }
}
