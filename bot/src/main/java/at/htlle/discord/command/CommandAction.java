package at.htlle.discord.command;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public interface CommandAction {
    void execute(SlashCommandInteractionEvent event);
}
