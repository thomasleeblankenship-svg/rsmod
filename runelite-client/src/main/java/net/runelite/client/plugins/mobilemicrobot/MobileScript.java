package net.runelite.client.plugins.mobilemicrobot;

/**
 * Deliberately small tick-driven interface. This is NOT the full Microbot Script
 * superclass. Port script behavior via MobileScriptContext; do not link a
 * desktop Microbot script jar directly to this package.
 */
public interface MobileScript
{
    default void onStart(MobileScriptContext context)
    {
    }

    void onGameTick(MobileScriptContext context);

    default void onStop(MobileScriptContext context)
    {
    }

    default String status()
    {
        return "Running";
    }
}
