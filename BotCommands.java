// Author: Aaron Snyder
// File containing all slash commands and handles. String is parsed and tokenized, and determined by statements belowed

package commands;
import mainPackage.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Member;
import java.util.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.*;
import javax.security.auth.login.LoginException;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;

import org.jetbrains.annotations.NotNull;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.interactions.components.text.TextInput;

public class BotCommands extends ListenerAdapter {

    Map<String, Inventory> globalInventory = new HashMap<>();
    

    @Override
    public void onMessageReceived(MessageReceivedEvent event) 
    {
        // Fields
        MessageChannelUnion channel = event.getChannel();
        String message = event.getMessage().getContentRaw();
        String currentUser = event.getAuthor().getName();

        // Check if event author is a bot
        if(event.getAuthor().isBot()) 
        {
            return;
        }

        // Check if new user and /start command
        if(!globalInventory.containsKey(currentUser)) 
        {
            if (message.equalsIgnoreCase("/start"))
            {
                Inventory userInv = new Inventory(currentUser);
                globalInventory.put(currentUser, userInv);
                channel.sendMessage("Welcome to the lake " + currentUser + "!").queue();
                channel.sendMessage("For a list of commands, type /commands").queue();
                return;
            }
        }
        // Call dispatcher to process command
        dispatcher(message, channel, currentUser);
    }

    // Dispatcher method, processes message recieved and performs given command
    public void dispatcher(String message, MessageChannelUnion channel, String user)
    {
        Inventory inv = globalInventory.get(user);
        message = message.toLowerCase();
        String tokens[] = message.split(" ");

        switch (tokens[0]) 
        {
            case "/fish":
                if (inv.getCanFish() == true) {  
                    Fish goneFishin = new Fish(tokens[1], inv.getSetBait());
                    channel.sendMessage("Yeehaw! You caught a " + goneFishin.getSize() + " inch " + goneFishin.getType()).queue();
                    inv.addFish(goneFishin);
                } else {
                    channel.sendMessage("Whoa there! Wait a bit before fishing again.").queue();
                }
                break;
            
            case "/inventory":
                channel.sendMessage(inv.getRods()).queue();
                channel.sendMessage("CarpCoins: " + inv.getCoins()).queue();
                channel.sendMessage(inv.getBaits()).queue();
                break;

            case "/getfish":
                channel.sendMessage(user + "'s Fish:\n" + inv.getFish()).queue();
                break;

            case "/setbait":
                channel.sendMessage(inv.setBait(tokens[1])).queue();
                break;

            case "/store":
                channel.sendMessage("Rods:\nTrout Tickler -> 100 CarpCoins\nCatfish Clobberer - > 1,000 CarpCoins\nSalmon Slapper -> 10,000 CarpCoins\nTuna Tamer -> 1,000,000 CarpCoins").queue();
                channel.sendMessage("Baits:\nWorm -> 5 CarpCoins\nPowerbait -> 10 CarpCoins\nGlitter Powerbait -> 15 CarpCoins\nSpinner Lure -> 20 CarpCoins").queue();
                break;

            case "/buy":
                channel.sendMessage(inv.buyMethod(tokens[1])).queue();
                break;

            case "/sell":
                channel.sendMessage(inv.sell()).queue();
                break;

            case "/commands":
                channel.sendMessage("/fish [rod name] -> Attempts to catch a fish with selected rod").queue();
                channel.sendMessage("/getFish -> Displays all of user's fish").queue();
                channel.sendMessage("/inventory -> Shows users current inventory of rods, fish, and bait").queue();
                channel.sendMessage("/setBait [bait] -> Sets default bait to user if in inventory").queue();
                channel.sendMessage("/store -> Displays store content, and user's CarpCoins").queue();
                channel.sendMessage("/buy [item] -> Adds item to inventory if you have enough CarpCoins").queue();
                channel.sendMessage("/sell -> Sells all user's fish for their value in length").queue();
                break;

            case "/save":
                try {
                    // Serialize the object and save it to a file
                    FileOutputStream fileOut = new FileOutputStream("globalInventory.ser");
                    ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
                    objectOut.writeObject(globalInventory);
                    objectOut.close();
                    fileOut.close();
                    System.out.println("Succesfully created/updated save file");
                } catch (IOException e) {
                    System.out.println("Failed to create/update save file: " + e.toString());
                }
                break;
            
            case "/load":
                try {
                    FileInputStream fileIn = new FileInputStream("globalInventory.ser");
                    ObjectInputStream objectIn = new ObjectInputStream(fileIn);
                    globalInventory = (Map<String, Inventory>) objectIn.readObject();
                    objectIn.close();
                    fileIn.close();
                    System.out.println("Successfully loaded save file");
                } catch (IOException | ClassNotFoundException e) {
                    System.out.println("Failed to load save file");
                }
        }
    }
}