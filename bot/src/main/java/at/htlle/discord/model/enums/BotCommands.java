package at.htlle.discord.model.enums;

import lombok.*;

import java.util.Collections;
import java.util.List;

@Getter
public enum BotCommands {
    ADD_YEAR(
            "add",
            "year",
            "Add a year",
            List.of(new CommandOption("year", "The integer of the school year", true))),
    ADD_CLASS_TEACHER(
            "add",
            "class-teacher",
            "Add a class teacher",
            List.of(new CommandOption("abbreviation", "The abbreviation of the class teacher", true))),
    ADD_CLASS(
            "add",
            "class",
            "Add a class",
            List.of(
                    new CommandOption("class", "The name of the class", true),
                    new CommandOption("teacher-abbreviation", "The abbreviation of the class teacher", true)
            )),
    CHANGE_CLASS_TEACHER(
            "change",
            "class-teacher-name",
            "Change the name of an existing class teacher",
            List.of(
                    new CommandOption("teacher-abbreviation-old", "The old abbreviation of the class teacher", true),
                    new CommandOption("teacher-abbreviation-new", "The new abbreviation of the class teacher", true)
            )),
    CHANGE_CLASS_NAME(
            "change",
            "class-name",
            "Change the name of an existing class",
            List.of(
                    new CommandOption("class-name-old", "The old name of the class", true),
                    new CommandOption("class-name-new", "The new name of the class", true)
            )),
    CHANGE_CLASS_CLASS_TEACHER(
            "change",
            "class-teacher",
            "Change the class teacher of an existing class",
            List.of(
                    new CommandOption("class", "The name of the class", true),
                    new CommandOption("teacher-abbreviation", "The abbreviation of the class teacher", true)
            )),
    PRINT_YEAR(
            "print",
            "year",
            "Print out all years"),
    PRINT_CLASS_TEACHER(
            "print",
            "class-teacher",
            "Print out all class teachers"),
    PRINT_CLASS(
            "print",
            "class",
            "Print out all classes with corresponding class teachers"),
    ROTATE("rotate", "Rotate the classes one year forward");

    private final String command;
    private final String subcommand;
    private final String description;
    private final List<CommandOption> options;

    BotCommands(String command, String description) {
        this(command, null, description, Collections.emptyList());
    }

    BotCommands(String command, String subcommand, String description) {
        this(command, subcommand, description, Collections.emptyList());
    }

    BotCommands(String command, String subcommand, String description, List<CommandOption> options) {
        this.command = command;
        this.subcommand = subcommand;
        this.description = description;
        this.options = options;
    }

    public boolean matches(String command, String subcommand) {
        return this.command.equals(command) &&
                (this.subcommand == null || this.subcommand.equals(subcommand));
    }

    public record CommandOption(String name, String description, boolean required) {
    }
}