package net.runelite.client.plugins.mobilemicrobot;

import java.util.function.Consumer;
import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.mobile.MobileActions;

/**
 * Hosts Microbot Hub plugins on mobile.
 * <p>
 * A Hub plugin is an ordinary RuneLite plugin under {@code net.runelite.client.plugins.microbot}
 * whose descriptor says {@code isExternal = true} (every Hub plugin sets it through
 * {@code PluginConstants.IS_EXTERNAL}). The user enabling one in the plugin list is the explicit
 * start: the host gives it its own {@link ScriptInputSession} and binds {@link MobileActions} to
 * it, so its actions are permitted. Only one script runs at a time. The session ends, and the
 * plugin is turned off, when the plugin is disabled, on logout, hop or disconnect, when the app is
 * backgrounded, and when Microbot Mobile is disabled or another script starts. Nothing restarts
 * automatically.
 * <p>
 * Not thread-confined: every method is synchronized. {@code stopper} must not call back into
 * this host synchronously (the plugin manager's stop runs later, on the EDT).
 */
final class HubPluginHost
{
	private static final String HUB_PACKAGE = "net.runelite.client.plugins.microbot.";

	private final Consumer<Plugin> stopper;
	private Plugin active;
	private ScriptInputSession session;

	/** @param stopper turns a plugin off through the plugin manager (for example {@code Microbot::stopPlugin}) */
	HubPluginHost(Consumer<Plugin> stopper)
	{
		this.stopper = stopper;
	}

	static boolean isHubPlugin(Plugin plugin)
	{
		if (plugin == null || !plugin.getClass().getName().startsWith(HUB_PACKAGE))
		{
			return false;
		}
		PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
		return descriptor != null && descriptor.isExternal();
	}

	/**
	 * A Hub plugin was started. When the runtime is not ready the plugin is turned off again
	 * (fail closed); otherwise it replaces any previously active Hub plugin.
	 *
	 * @return a status line for the mobile status surface
	 */
	synchronized String started(Plugin plugin, boolean runtimeReady)
	{
		if (!runtimeReady)
		{
			stopper.accept(plugin);
			return "Enable Microbot Mobile before starting " + name(plugin);
		}
		if (active != null && active != plugin)
		{
			end(true);
		}
		ScriptInputSession next = new ScriptInputSession();
		if (!next.isActive())
		{
			stopper.accept(plugin);
			return "Stopped " + name(plugin) + ": app backgrounded during start";
		}
		session = next;
		active = plugin;
		MobileActions.bind(next);
		return "Running Hub plugin " + name(plugin);
	}

	/** A plugin was stopped; ends the session when it was the active Hub plugin. */
	synchronized void stopped(Plugin plugin)
	{
		if (plugin != null && plugin == active)
		{
			end(false);
		}
	}

	/**
	 * Ends the active Hub plugin's session and, when {@code turnOff}, turns the plugin off.
	 *
	 * @return true when a Hub plugin was active
	 */
	synchronized boolean end(boolean turnOff)
	{
		Plugin old = active;
		ScriptInputSession oldSession = session;
		active = null;
		session = null;
		if (oldSession != null)
		{
			MobileActions.unbind(oldSession);
		}
		if (old != null && turnOff)
		{
			stopper.accept(old);
		}
		return old != null;
	}

	/** Called every game tick: a session ended by backgrounding or an input fault turns the plugin off. */
	synchronized boolean checkSession()
	{
		if (active != null && (session == null || !session.isActive()))
		{
			end(true);
			return true;
		}
		return false;
	}

	synchronized boolean isRunning()
	{
		return active != null;
	}

	synchronized Plugin activePlugin()
	{
		return active;
	}

	static String name(Plugin plugin)
	{
		PluginDescriptor descriptor = plugin == null ? null : plugin.getClass().getAnnotation(PluginDescriptor.class);
		// Drop HTML and the leading author tag, e.g. "<html>[<font ...>MB</font>] Name" -> "Name".
		return descriptor == null ? String.valueOf(plugin)
			: descriptor.name().replaceAll("<[^>]*>", "").replaceFirst("^\\s*\\[[^\\]]*\\]", "").trim();
	}
}
