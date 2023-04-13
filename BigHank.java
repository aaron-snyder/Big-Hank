/package mainPackage;
import mainPackage.*;

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
    Map<String, Inventory> globalInventory = new HashMap<>();
    Random rand = new Random();

    public static void main(String[] args) throws LoginException
    {

        JDABuilder bot = JDABuilder.createDefault("Token goes here");
        bot.setActivity(Activity.playing("bantz-big-bass-fishing"));
        bot.addEventListeners(new BigHank());
        bot.enableIntents(GatewayIntent.GUILD_MEMBERS);
        bot.enableIntents(GatewayIntent.MESSAGE_CONTENT);
        bot.build();
        
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) 
    {
        MessageChannelUnion channel = event.getChannel();
        String message = event.getMessage().toString();
        User currentUser = event.getAuthor();
        String ah = currentUser.getAsMention();

        if(event.getAuthor().isBot()) {
            return;
        }

        // Check if user already has an inventory else create a default inventory for them
        if(!globalInventory.containsKey(ah)) {
            Inventory userInv = new Inventory(ah);
            globalInventory.put(ah, userInv);
            channel.sendMessage("Welcome to the lake " + ah + "!").queue();
        }

        // inventory command
        if(message.contains("/inventory")){
            channel.sendMessage(globalInventory.get(ah).getRods()).queue();
            channel.sendMessage(globalInventory.get(ah).getFish()).queue();
            channel.sendMessage(globalInventory.get(ah).getBaits()).queue();
        }     
        
        // fish command
        if(message.contains("/fish")){
            channel.sendMessage("Which rod would you like to use?").queue();
            
        }
    }

// Below code contains methods necessary for fishing process!
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public static String determineType(String rod)
    {
        ArrayList<String> catchableFish = new ArrayList<>();
        switch(rod){
            case "Tuna Tamer":
                catchableFish.add("Tuna");
            case "Salmon Slapper":
                catchableFish.add("Salmon");
                catchableFish.add("Halibut");
            case "Catfish Clobberer":
                catchableFish.add("Rainbow Trout");
                catchableFish.add("Largemouth Bass");
                catchableFish.add("Catfish");
            case "Trout Tickler":
                catchableFish.add("Trout");
                catchableFish.add("Smallmouth Bass");
            case "Guppy Getter":
                catchableFish.add("Goldfish");
                catchableFish.add("Guppy");
                catchableFish.add("Pufferfish");
                break;     
            default:
                return null;       
        }
        Random r = new Random();
        String returnType = catchableFish.get(r.nextInt(0, catchableFish.size()));
        return returnType;
    }

    public static int determineSize(String type)
    {
        int returnInt = 0;
        return returnInt;
    }

    public static void store(String user){

    }
}
