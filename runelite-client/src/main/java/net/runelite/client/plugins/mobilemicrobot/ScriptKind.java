package net.runelite.client.plugins.mobilemicrobot;

public enum ScriptKind
{
    DIAGNOSTICS("Diagnostics (read-only)"),
    SCENE_SCAN("Scene scan (read-only)"),
    WOODCUTTING("Woodcutting (example)");

    private final String display;

    ScriptKind(String display)
    {
        this.display = display;
    }

    @Override
    public String toString()
    {
        return display;
    }
}
