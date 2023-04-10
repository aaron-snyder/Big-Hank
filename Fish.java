package mainPackage;

// Author: Aaron Snyder
// Title: Object Fish

public class Fish {
    String name;
    String type;
    int size;

    public Fish(String name, String type, int size) 
    {
        this.name = name;
        this.type = type;
        this.size = size;
    }

    public void setName(String name) 
    {
        this.name = name;
    }

    public void setType(String type) 
    {
        this.type = type;
    }

    public void setSize(int size) 
    {
        this.size = size;
    }

    public String getName() 
    {
        return name;
    }

    public String getType()
    {
        return type;
    }

    public int getSize()
    {
        return size;
    }
}