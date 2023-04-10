/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package mainPackage;
import mainPackage.*;
import java.util.*;


import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.*;

import javax.security.auth.login.LoginException;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jetbrains.annotations.NotNull;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
/**
 *
 * @author Aaron Snyder
 */
public class BigHank extends ListenerAdapter {

    Random rand = new Random();

    public static void main(String[] args) throws LoginException
    {

        JDABuilder bot = JDABuilder.createDefault("OTYxODQ5MzMyMzM0NzQzNTUy.G4cm-n.fRamoDIyWHfO2R3SlE80tk6pz-lppayqHHm_4M");
        bot.setActivity(Activity.playing("bantz-big-bass-fishing"));
        bot.addEventListeners(new BigHank());
        bot.enableIntents(GatewayIntent.GUILD_MEMBERS);
        bot.enableIntents(GatewayIntent.MESSAGE_CONTENT);
        bot.build();
        
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) 
    {
        if(event.getAuthor().isBot()) {
            return;
        }

        String message = event.getMessage().getContentRaw();
        MessageChannelUnion channel = event.getChannel();
        int randFish = rand.nextInt(0,4);

    }

    public static String determineType()
    {
        String returnType = "go away";

        return returnType;
    }

    public static int determineSize()
    {
        int returnInt = 0;
        return returnInt;
    }

    public static void weatherMap()
    {
        Map<String, Object> weatherMap = new HashMap<>();
    }
}

/*
 * func getWeatherMap() map[string]WeatherInfo {
        WeatherMap := make(map[string]WeatherInfo)
        WeatherMap["drizzle"] = WeatherInfo{Bait: "plug lure", Message: "It's drizzling..."}
        WeatherMap["snow"] = WeatherInfo{Bait: "plain powerbait", Message: "Snow is falling..."}
        WeatherMap["wind"] = WeatherInfo{Bait: "jig lure", Message: "A cold wind blows..."}
        WeatherMap["storm"] = WeatherInfo{Bait: "spinner lure", Message: "The thunder rolls..."}
        WeatherMap["fog"] = WeatherInfo{Bait: "glitter powerbait", Message: "The fog is thick..."}
        WeatherMap["sun"] = WeatherInfo{Bait: "offal", Message: "The sun beats down..."}
        WeatherMap["mist"] = WeatherInfo{Bait: "fly", Message: "Mist fills the air..."}
        WeatherMap["sandstorm"] = WeatherInfo{Bait: "worm", Message: "A harsh sandstorm rages..."}
        return WeatherMap
 */