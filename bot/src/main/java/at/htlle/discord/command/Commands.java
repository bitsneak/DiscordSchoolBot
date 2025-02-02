package at.htlle.discord.command;

import lombok.*;

import java.util.Collections;
import java.util.List;

@Getter
public enum Commands {
    // add commands
    ADD_YEAR(
            "add",
            "year",
            "Add a year",
            List.of(new CommandOption("year", "The integer of the school year", true))),
    ADD_PROFESSION(
            "add",
            "profession",
            "Add a profession",
            List.of(new CommandOption("profession", "The name of the profession", true))),
    ADD_TEACHER(
            "add",
            "teacher",
            "Add a teacher",
            List.of(new CommandOption("abbreviation", "The abbreviation of the teacher", true))),
    ADD_CLASS(
            "add",
            "class",
            "Add a class",
            List.of(
                    new CommandOption("class", "The name of the class", true),
                    new CommandOption("teacher-abbreviation", "The abbreviation of the teacher", true)
            )),
    ADD_COLOR(
            "add",
            "color",
            "Add a color",
            List.of(
                    new CommandOption("scope", "The scope in which the color should be used", true),
                    new CommandOption("color", "The hex code of the color", true)
            )),
    // change commands
    CHANGE_PROFESSION(
            "change",
            "profession",
            "Change the name of an existing profession",
            List.of(
                    new CommandOption("profession-old", "The old name of the profession", true),
                    new CommandOption("profession-new", "The new name of the profession", true)
            )),
    CHANGE_TEACHER(
            "change",
            "teacher",
            "Change the name of an existing class teacher",
            List.of(
                    new CommandOption("teacher-abbreviation-old", "The old abbreviation of the class teacher", true),
                    new CommandOption("teacher-abbreviation-new", "The new abbreviation of the class teacher", true)
            )),
    CHANGE_CLASS_NAME(
            "change",
            "class",
            "Change the name of an existing class",
            List.of(
                    new CommandOption("class-name-old", "The old name of the class", true),
                    new CommandOption("class-name-new", "The new name of the class", true)
            )),
    CHANGE_CLASS_TEACHER(
            "change",
            "class-teacher",
            "Change the teacher of an existing class",
            List.of(
                    new CommandOption("class", "The name of the class", true),
                    new CommandOption("teacher-abbreviation", "The abbreviation of the class teacher", true)
            )),
    CHANGE_COLOR(
            "change",
            "color",
            "Change the color of an existing scope",
            List.of(
                    new CommandOption("scope", "The scope of the color", true),
                    new CommandOption("color-new", "The hex code of the new color", true)
            )),
    CHANGE_COLOR_SCOPE(
            "change",
            "color-scope",
            "Change the scope of an existing color",
            List.of(
                    new CommandOption("scope-old", "The old scope of the color", true),
                    new CommandOption("scope-new", "The new scope of the color", true)
            )),
    // print commands
    PRINT_YEAR(
            "print",
            "year",
            "Print out all years"),
    PRINT_PROFESSION(
            "print",
            "profession",
            "Print out all professions"),
    PRINT_TEACHER(
            "print",
            "teacher",
            "Print out all teachers"),
    PRINT_CLASS(
            "print",
            "class",
            "Print out all classes with corresponding teachers"),
    PRINT_COLOR(
            "print",
            "color",
            "Print out all colors with corresponding scopes"),
    // rotate command
    ROTATE("rotate", "Rotate the classes one year forward");

    private final String command;
    private final String subcommand;
    private final String description;
    private final List<CommandOption> options;

    Commands(String command, String description) {
        this(command, null, description, Collections.emptyList());
    }

    Commands(String command, String subcommand, String description) {
        this(command, subcommand, description, Collections.emptyList());
    }

    Commands(String command, String subcommand, String description, List<CommandOption> options) {
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