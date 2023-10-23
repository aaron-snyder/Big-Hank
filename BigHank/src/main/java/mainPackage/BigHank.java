package mainPackage;

import commands.*;
import java.lang.reflect.Member;
import java.util.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.*;
import javax.security.auth.login.LoginException;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jetbrains.annotations.NotNull;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;

public class BigHank extends ListenerAdapter {
    public static void main(String[] args) throws LoginException
    {
        JDABuilder bot = JDABuilder.createDefault("OTYxODQ5MzMyMzM0NzQzNTUy.GCKBXI.tivrTLObszU3UZ0JifsQ7-Vy244YrZse-D9ruU");
        bot.setActivity(Activity.playing("bantz-big-bass-fishing")); // Sets bot's Discord activity
        bot.addEventListeners(new BotCommands()); // BotCommands class become event listener
        bot.enableIntents(GatewayIntent.GUILD_MEMBERS); // Allows bot to read from users
        bot.enableIntents(GatewayIntent.MESSAGE_CONTENT); // Allows bot to send messages
        bot.build();
    }
}
