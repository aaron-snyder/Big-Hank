package bighank;

import bighank.commands.BotCommands;
import bighank.data.GameStore;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Starts Big Hank. */
public final class BigHank {
    private static final Logger LOG = LoggerFactory.getLogger(BigHank.class);

    public static void main(String[] args) throws Exception {
        Config config = Config.load();
        String token = config.get("DISCORD_TOKEN", null);
        if (token == null) {
            System.err.println("""
                    No bot token found!
                    Set the DISCORD_TOKEN environment variable, or copy .env.example to .env and paste
                    your token in there. See README.md for where to get one.""");
            System.exit(1);
        }

        GameStore store = GameStore.load(Path.of(config.get("DATA_FILE", "data/inventories.json")));

        // Never ping anyone (no @everyone, @here, roles, or users), even if a player's name contains one.
        MessageRequest.setDefaultMentions(EnumSet.noneOf(Message.MentionType.class));

        JDA jda;
        try {
            // Least privilege: slash commands need no gateway intents at all, so we request none.
            // No message content, no member list, no presences. createLight also turns off every cache.
            jda = JDABuilder.createLight(token, EnumSet.noneOf(GatewayIntent.class))
                    .setActivity(Activity.playing("bantz-big-bass-fishing"))
                    .addEventListeners(new BotCommands(store))
                    .build();
            jda.awaitReady();
        } catch (InvalidTokenException e) {
            System.err.println("Discord rejected the bot token. Reset it in the Developer Portal and update .env.");
            System.exit(1);
            return;
        }

        // Global commands work in every server the bot is added to.
        jda.updateCommands().addCommands(BotCommands.definitions()).queue(
                cmds -> LOG.info("Registered {} slash commands", cmds.size()),
                err -> LOG.error("Failed to register slash commands", err));

        LOG.info("Big Hank is on the lake as {}", jda.getSelfUser().getName());
        LOG.info("Invite link (no permissions needed): https://discord.com/oauth2/authorize?client_id={}"
                + "&scope=bot+applications.commands&permissions=0", jda.getSelfUser().getApplicationId());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down, saving the lake...");
            jda.shutdown();
            try {
                jda.awaitShutdown(Duration.ofSeconds(10));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            synchronized (store) {
                store.save();
            }
        }, "bighank-shutdown"));
    }
}
