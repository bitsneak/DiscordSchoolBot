package at.htlle.discord.core;

import at.htlle.discord.handler.CommandHandler;
import at.htlle.discord.handler.LoginHandler;
import at.htlle.discord.util.DiscordUtil;
import jakarta.annotation.PostConstruct;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BotSupervisor extends ListenerAdapter {
    private static final Logger logger = LogManager.getLogger(BotSupervisor.class);

    @Value("${discord.token}")
    private String discordToken;

    @Autowired
    private LoginHandler loginBot;

    @Autowired
    private CommandHandler commandBot;

    @Autowired
    private DiscordUtil discordUtil;

    @Value("${discord.channel.log.name}")
    private String logChannelName;

    @Value("${discord.channel.command.name}")
    private String commandChannelName;

    @PostConstruct
    public void initBot() throws Exception {
        logger.info("Initializing Discord bot with token: {}", discordToken);

        // register bots
        try {
            JDA jda = JDABuilder.createDefault(discordToken)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS)
                    .addEventListeners(this, loginBot, commandBot)
                    .build();
            jda.awaitReady();
            logger.info("Bot is now online and ready.");
        } catch (Exception e) {
            logger.error("Error initializing Discord bot: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public void onReady(ReadyEvent event) {
        JDA jda = event.getJDA();
        logger.info("Bot is ready. Fetching guilds and channels");

        // set required channels
        jda.getGuilds().forEach(guild -> {
            guild.getTextChannels().forEach(channel -> {
                if (channel.getName().equals(logChannelName)) {
                    discordUtil.setLogChannel(channel);
                    logger.info("Login channel set: {}", channel.getName());
                }

                if (channel.getName().equals(commandChannelName)) {
                    commandBot.setCommandChannel(channel);
                    logger.info("Bot channel set: {}", channel.getName());
                }
            });
        });

        // get all commands
        List<CommandData> commands = commandBot.getCommandService().initializeCommands(commandBot.getCommandService().getCommandActions());
        // register commands
        jda.updateCommands().addCommands(commands).queue(success -> {
            logger.info("Commands successfully registered.");
        }, error -> {
            logger.error("Failed to register commands: {}", error.getMessage(), error);
        });
    }
}
