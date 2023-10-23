package mainPackage;
import java.util.ArrayList;
import java.util.Random;

public class Fish 
{
    private String type;
    private int size;
    private Random rand = new Random();

    public Fish(){} // Default constructor

    public Fish(String rod, String bait)
    {
        int extraSize = 0;
        switch (bait)
        {
            case "worm":
                extraSize += rand.nextInt(1, 5);
                break;
            case "powerbait":
                extraSize += rand.nextInt(1, 10);
                break;
            case "glitter":
                extraSize += rand.nextInt(1, 15);
                break;
            case "spinner":
                extraSize += rand.nextInt(15, 20);
                break;
        }
        type = determineType(rod);
        size = determineSize(type) + extraSize;
    }

    public String determineType(String rod)
    {
        int getRand;
        ArrayList<String> catchableFish = new ArrayList<>();
        switch(rod)
        {
            case "tuna":
                catchableFish.add("Tuna");
            case "salmon":
                catchableFish.add("Salmon");
                catchableFish.add("Halibut");
            case "catfish":
                catchableFish.add("Largemouth Bass");
                catchableFish.add("Catfish");
            case "trout":
                catchableFish.add("Rainbow Trout");
                catchableFish.add("Smallmouth Bass");
            case "guppy":
                catchableFish.add("Goldfish");
                catchableFish.add("Guppy");
                catchableFish.add("Pufferfish");
                break;     
            default:
                return null;       
        }
        getRand = rand.nextInt(0, catchableFish.size());
            
        String returnType = catchableFish.get(getRand);
        return returnType;
    }

    public int determineSize(String type)
    {
        int returnSize = 0;
        switch(type)
        {
            case "Tuna":
                returnSize = rand.nextInt(43, 83);
                break;
            case "Salmon":
                returnSize = rand.nextInt(20, 40);
                break;
            case "Halibut":
                returnSize = rand.nextInt(20, 45);
                break;
            case "Rainbow Trout":
                returnSize = rand.nextInt(10, 18);
                break;
            case "Largemouth Bass":
                returnSize = rand.nextInt(6, 25);
                break;
            case "Catfish":
                returnSize = rand.nextInt(10, 25);
                break;
            case "Smallmouth Bass":
                returnSize = rand.nextInt(5, 20);
                break;
            case "Goldfish":
                returnSize = rand.nextInt(1, 5);
                break;
            case "Guppy":
                returnSize = rand.nextInt(1, 3);
                break;
            case "Pufferfish":
                returnSize = rand.nextInt(1, 3);
                break;
        }
        return returnSize;
    }

    // Getters and Setters
    public void setType(String type) 
    {
        this.type = type;
    }

    public void setSize(int size) 
    {
        this.size = size;
    }

    public String getType()
    {
        return type;
    }

    public int getSize()
    {
        return size;
    }

    public String toString()
    {
        return size + " inch " + type;
    }
}