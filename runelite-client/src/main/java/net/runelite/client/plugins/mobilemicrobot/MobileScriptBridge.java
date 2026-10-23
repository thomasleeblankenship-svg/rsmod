package net.runelite.client.plugins.mobilemicrobot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Local-only status/control facade, suitable for an optional JNI binding. */
public final class MobileScriptBridge
{
    private static volatile MobileMicrobotPlugin plugin;
    private static volatile String status = "Not initialized";
    private static volatile String selected = "";
    private static volatile boolean running;

    private MobileScriptBridge() { }

    static void attach(MobileMicrobotPlugin value) { plugin = value; }

    static void detach(MobileMicrobotPlugin value)
    {
        if (plugin == value)
        {
            plugin = null;
            update("Disabled", "", false);
        }
    }

    static void update(String message, String scriptName, boolean isRunning)
    {
        status = message;
        selected = scriptName;
        running = isRunning;
    }

    public static String statusJson()
    {
        MobileMicrobotPlugin current = plugin;
        boolean active = running && current != null && current.inputActive();
        JsonObject obj = new JsonObject();
        obj.addProperty("ready", current != null);
        obj.addProperty("running", active);
        obj.addProperty("script", selected);
        obj.addProperty("status", running && !active ? "Stopped: input permission cancelled" : status);
        return obj.toString();
    }

    public static String scriptsJson()
    {
        JsonArray array = new JsonArray();
        for (ScriptKind kind : ScriptKind.values())
        {
            JsonObject script = new JsonObject();
            script.addProperty("id", kind.name());
            script.addProperty("name", kind.toString());
            array.add(script);
        }
        return array.toString();
    }

    public static void start(String kind)
    {
        MobileMicrobotPlugin current = plugin;
        if (current != null)
        {
            current.requestStart(kind);
        }
    }

    public static void stop()
    {
        MobileMicrobotPlugin current = plugin;
        if (current != null)
        {
            current.requestStop();
        }
    }
}
