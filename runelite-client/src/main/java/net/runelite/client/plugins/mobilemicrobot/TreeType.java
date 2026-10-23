package net.runelite.client.plugins.mobilemicrobot;

public enum TreeType
{
    NORMAL("Tree"),
    OAK("Oak tree"),
    WILLOW("Willow tree"),
    MAPLE("Maple tree"),
    YEW("Yew tree");

    private final String objectName;

    TreeType(String objectName)
    {
        this.objectName = objectName;
    }

    public String objectName()
    {
        return objectName;
    }
}
