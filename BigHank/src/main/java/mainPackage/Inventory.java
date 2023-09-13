package mainPackage;
import java.util.*;

public class Inventory
{
    private String owner;
    private ArrayList<String> rods = new ArrayList<>();
    private ArrayList<String> baits = new ArrayList<>();
    private ArrayList<Fish> fish = new ArrayList<>();
    private int CarpCoins;
    private boolean canFish = true;
    private String bait = "";
    public Timer timer = new Timer();

    public Inventory(){}; // Default constructor

    public Inventory(String owner)
    {
        this.owner = owner;
        rods.add("Guppy Getter");
        CarpCoins = 100;
    }

    
    public String buyMethod(String buying)
    {
        String returnMessage = "Yain't got enough CarpCoins!";
        switch (buying) 
        {
            case "tuna":
                if (getCoins() >= 1000000) {
                    addRod("Tuna Tamer");
                    returnMessage = "Yeehaw! You bought the Tuna Tamer!";
                }
                break;
            
            case "salmon":
                if (getCoins() >= 10000) {
                    addRod("Salmon Slapper");
                    returnMessage = "Yeehaw! You bought the Salmon Slapper!";
                }
                break;

            case "catfish":
                if (getCoins() >= 1000) {
                    addRod("Catfish Clobberer");
                    returnMessage = "Yeehaw! You bought the Catfish Clobberer!";
                }
                break;

            case "trout":
                if (getCoins() >= 100) {
                    addRod("Trout Tickler");
                    returnMessage = "Yeehaw! You bought the Trout Tickler!";
                }
                break;

            case "worm":
                if (getCoins() >= 5) {
                    addBait("Worm");
                    returnMessage = "Yeehaw! You bought a worm!";
                }
                break;

            case "powerbait":
                if (getCoins() >= 10) {
                    addBait("Powerbait");
                    returnMessage = "Yeehaw! You bought some PowerBait!";
                }
                break;

            case "glitter":
                if (getCoins() >= 15) {
                    addBait("Glitter Powerbait");
                    returnMessage = "Yeehaw! You bought some glitter PowerBait!";
                }
                break;

            case "spinner":
                if (getCoins() >= 20) {
                    addBait("Spinner Lure");
                    returnMessage = "Yeehaw! You bought a spinner lure!";
                } else {
                    returnMessage = "Yain't got enough CarpCoins!";
                }
                break;
        }
        return returnMessage;
    }

    public String sell()
    {
        int sellValue = 0;
        int forValue = fish.size();
        for(int i = 0; i < forValue; i++) 
        {
            Fish f = fish.get(0);
            sellValue += f.getSize();
            removeFish();
        }
        addCoins(sellValue);
        return "All fish sold for " + sellValue + " CarpCoins!";
    }
    
    public void addRod(String rod)
    {
        rods.add(rod);
    }

    public void addFish(Fish fish)
    {
        this.fish.add(fish);
        setCanFish(false);
        setTask();
        setBait("");
    }

    public void removeFish()
    {
        fish.remove(0);
    }

    public void addBait(String bait)
    {
        baits.add(bait);
    }

    public void removeBait(String bait)
    {
        baits.remove(bait);
    }

    public String setBait(String bait)
    {
        String checkBait = "";
        String returnMessage = "Yain't got any of that kind of bait!";
        switch (bait)
        {
            case "worm":
                checkBait = "Worm";
                break;
            case "powerbait":
                checkBait = "Powerbait";
                break;
            case "glitter":
                checkBait = "Glitter Powerbait";
                break;
            case "spinner":
                checkBait = "Spinner Lure";
                break;
            default:
                this.bait = "";
                break;
        }
        if (baits.contains(checkBait)) {
            this.bait = bait;
            returnMessage = "Bait set!";
            removeBait(checkBait);
        }
        return returnMessage;
    }

    public void addCoins(int add)
    {
        CarpCoins += add;
    }

    public String getRods()
    {
        String returnString = "Rods:\n";
        for (String rod : rods) {
            returnString = returnString + rod + " ";
        }
        return returnString;
    }

    public String getBaits()
    {
        String returnString = "Baits:\n";
        for (String bait : baits) {
            returnString = returnString + bait + " ";
        }
        return returnString;
    }

    public String getSetBait()
    {
        return bait;
    }

    public String getFish()
    {
        String returnString = "";
        for(int i = 0; i < fish.size(); i++) {
            returnString += fish.get(i).getSize() + " inch " + fish.get(i).getType() + "\n";
        }
        return returnString;
    }

    public int getCoins()
    {
        return CarpCoins;
    }

    public boolean getCanFish()
    {
        return canFish;
    }

    public void setCanFish(boolean b)
    {
        canFish = b;
    }

    public void setTask()
    {
        TimerTask task = new TimerTask() {
            
            @Override
            public void run(){
                canFish = true;
            }
        };
        timer.schedule(task, 10);
    }
}