package bighank;

import bighank.commands.BotCommands;
import bighank.commands.LakeKeeper;
import bighank.data.GameStore;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
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
            // Least privilege: no optional gateway intents. JDA always uses the basic, non-privileged GUILDS
            // intent, which tells Big Hank when he joins or leaves a server and which channels exist (all he needs
            // to manage #big-hanks-lake). No message content, no member list, no presences. createLight also
            // turns off every optional cache.
            LakeKeeper lakeKeeper = new LakeKeeper(store);
            jda = JDABuilder.createLight(token, EnumSet.noneOf(GatewayIntent.class))
                    .setActivity(Activity.playing("bantz-big-bass-fishing"))
                    .addEventListeners(lakeKeeper, new BotCommands(store, lakeKeeper))
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
        // The only permission Big Hank asks for is Manage Channels, to create #big-hanks-lake.
        LOG.info("Invite link: https://discord.com/oauth2/authorize?client_id={}&scope=bot+applications.commands"
                + "&permissions={}", jda.getSelfUser().getApplicationId(), Permission.MANAGE_CHANNEL.getRawValue());

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
