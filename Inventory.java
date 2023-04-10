package mainPackage;
import java.util.*;

public class Inventory
{
    String owner;
    ArrayList<String> rods;
    ArrayList<String> baits;
    ArrayList<Fish> fish;

    public Inventory(String owner)
    {
        this.owner = owner;
        rods.add("Kids Spongebob Rod");
    }

    public void addRod(String rod)
    {
        rods.add(rod);
    }

    public void addFish(Fish fish)
    {
        this.fish.add(fish);
    }

    public void addBait(String bait)
    {
        baits.add(bait);
    }

    public String getRods()
    {
        return owner + "'s Rods:\n" + rods.toString();
    }

    public String getBaits()
    {
        return owner + "'s Baits:\n" + baits.toString();
    }

    public String getFish()
    {
        String returnString = owner + "'s Fish:\n";
        for(int i = 0; i < fish.size(); i++) {
            returnString += fish.get(i).getSize() + " inch " + fish.get(i).getType() + "\n";
        }
        return returnString;
    }
}