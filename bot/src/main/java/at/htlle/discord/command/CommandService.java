package at.htlle.discord.command;

import at.htlle.discord.command.impl.add.*;
import at.htlle.discord.command.impl.change.*;
import at.htlle.discord.command.impl.print.*;
import at.htlle.discord.command.impl.rotate.RotateCommand;
import lombok.Getter;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Getter
@Service
public class CommandService {
    private Map<Commands, CommandAction> commandActions = new HashMap<>();

    @Autowired
    public CommandService(
            // add commands
            AddClassCommand addClassCommand,
            AddColorCommand addColorCommand,
            AddProfessionCommand addProfessionCommand,
            AddTeacherCommand addTeacherCommand,
            AddYearCommand addYearCommand,
            // change commands
            ChangeClassNameCommand changeClassNameCommand,
            ChangeClassTeacherCommand changeClassTeacherCommand,
            ChangeColorCommand changeColorCommand,
            ChangeColorScopeCommand changeColorScopeCommand,
            ChangeProfessionCommand changeProfessionCommand,
            ChangeTeacherCommand changeTeacherCommand,
            // print commands
            PrintClassCommand printClassCommand,
            PrintColorCommand printColorCommand,
            PrintProfessionCommand printProfessionCommand,
            PrintTeacherCommand printTeacherCommand,
            PrintYearCommand printYearCommand,
            // rotate command
            RotateCommand rotateCommand
    ) {
        // add commands
        commandActions.put(Commands.ADD_CLASS, addClassCommand);
        commandActions.put(Commands.ADD_COLOR, addColorCommand);
        commandActions.put(Commands.ADD_PROFESSION, addProfessionCommand);
        commandActions.put(Commands.ADD_TEACHER, addTeacherCommand);
        commandActions.put(Commands.ADD_YEAR, addYearCommand);
        // change commands
        commandActions.put(Commands.CHANGE_CLASS_NAME, changeClassNameCommand);
        commandActions.put(Commands.CHANGE_CLASS_TEACHER, changeClassTeacherCommand);
        commandActions.put(Commands.CHANGE_COLOR, changeColorCommand);
        commandActions.put(Commands.CHANGE_COLOR_SCOPE, changeColorScopeCommand);
        commandActions.put(Commands.CHANGE_PROFESSION, changeProfessionCommand);
        commandActions.put(Commands.CHANGE_TEACHER, changeTeacherCommand);
        // print commands
        commandActions.put(Commands.PRINT_CLASS, printClassCommand);
        commandActions.put(Commands.PRINT_COLOR, printColorCommand);
        commandActions.put(Commands.PRINT_PROFESSION, printProfessionCommand);
        commandActions.put(Commands.PRINT_TEACHER, printTeacherCommand);
        commandActions.put(Commands.PRINT_YEAR, printYearCommand);
        // rotate command
        commandActions.put(Commands.ROTATE, rotateCommand);
    }

    public void handleCommand(SlashCommandInteractionEvent event, Commands botCommand) {
        CommandAction action = commandActions.get(botCommand);
        if (action != null) {
            action.execute(event);
        }
    }

    public List<CommandData> initializeCommands(Map<Commands, CommandAction> commandActions) {
        List<CommandData> commands = new ArrayList<>();

        // group commands by their main command name
        Map<String, List<Commands>> groupedCommands = new HashMap<>();

        for (Commands command : commandActions.keySet()) {
            // only group commands that have subcommands
            if (command.getSubcommand() != null) {
                groupedCommands.computeIfAbsent(command.getCommand(), k -> new ArrayList<>()).add(command);
            }
        }

        // create slash commands with subcommands
        for (Map.Entry<String, List<Commands>> entry : groupedCommands.entrySet()) {
            String mainCommand = entry.getKey();
            List<Commands> subcommands = entry.getValue();

            // if only one command exists in the group and has no subcommand treat it as standalone
            if (subcommands.size() == 1 && subcommands.getFirst().getSubcommand() == null) {
                commands.add(
                        net.dv8tion.jda.api.interactions.commands.build.Commands
                                .slash(mainCommand, subcommands.getFirst().getDescription())
                );
            } else {
                commands.add(
                        net.dv8tion.jda.api.interactions.commands.build.Commands
                                .slash(mainCommand, "Main command for " + mainCommand)
                                .addSubcommands(subcommands.stream()
                                        .map(this::createSubcommand)
                                        .collect(Collectors.toList()))
                );
            }
        }

        // add standalone commands that are not part of a grouped command
        for (Commands command : commandActions.keySet()) {
            if (command.getSubcommand() == null && !groupedCommands.containsKey(command.getCommand())) {
                commands.add(
                        net.dv8tion.jda.api.interactions.commands.build.Commands
                                .slash(command.getCommand(), command.getDescription())
                );
            }
        }

        return commands;
    }

    // create SubcommandData from BotCommands enum
    private SubcommandData createSubcommand(Commands botCommand) {
        SubcommandData subcommandData = new SubcommandData(botCommand.getSubcommand(), botCommand.getDescription());

        for (Commands.CommandOption option : botCommand.getOptions()) {
            subcommandData.addOption(OptionType.STRING, option.name(), option.description(), option.required());
        }

        return subcommandData;
    }
}
