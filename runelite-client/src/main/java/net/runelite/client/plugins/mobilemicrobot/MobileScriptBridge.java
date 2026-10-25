package net.runelite.client.plugins.mobilemicrobot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Local-only status/control facade, suitable for an optional JNI binding. */
public final class MobileScriptBridge
{
	private static volatile MobileMicrobotPlugin plugin;
	/** Shown while the Microbot Mobile plugin is off; the Microbot screen's Start turns it on. */
	static final String OFF_STATUS = "Microbot Mobile is off - Start turns it on";

	private static volatile String status = OFF_STATUS;
	// A Start requested while the plugin was off; run once the plugin attaches (see startPending), but only
	// within PENDING_START_MILLIS so a later manual enable never starts a script by itself.
	static final long PENDING_START_MILLIS = 15_000;
	private static volatile String pendingStart;
	private static volatile long pendingStartAt;
	private static volatile String selected = "";
	private static volatile boolean running;

	private MobileScriptBridge()
	{
	}

	static void attach(MobileMicrobotPlugin value)
	{
		plugin = value;
	}

	static void detach(MobileMicrobotPlugin value)
	{
		if (plugin == value)
		{
			plugin = null;
			pendingStart = null;
			update(OFF_STATUS, "", false);
		}
	}

	static void update(String message, String scriptName, boolean isRunning)
	{
		status = message;
		selected = scriptName;
		running = isRunning;
	}

	/** The running script's {@code Microbot.status}, or "" when it is unset or upstream's default "IDLE". */
	static String scriptDetail()
	{
		String value = net.runelite.client.plugins.microbot.Microbot.status;
		return value == null || value.isBlank() || "IDLE".equalsIgnoreCase(value.trim()) ? "" : value.trim();
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
		// The running script's own progress line (Microbot.status, e.g. "Walking to bank"), shown under the status.
		String detail = active ? scriptDetail() : "";
		obj.addProperty("detail", detail);
		return obj.toString();
	}

	/**
	 * The Microbot Mobile screen's live view: {@link #statusJson()}'s fields plus {@code scripts}, each
	 * script's id (enum name) and display name.
	 */
	public static String panelJson()
	{
		JsonObject obj = new com.google.gson.JsonParser().parse(statusJson()).getAsJsonObject();
		obj.add("scripts", new com.google.gson.JsonParser().parse(scriptsJson()));
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
			if (kind.getConfigGroup() != null)
			{
				script.addProperty("configGroup", kind.getConfigGroup());
			}
			array.add(script);
		}
		return array.toString();
	}

	/**
	 * Starts script {@code kind}. While the plugin is off the request is kept and run by
	 * {@link #startPending()} once the plugin has started.
	 *
	 * @return false when the plugin is off, so the caller should turn it on
	 */
	public static boolean start(String kind)
	{
		MobileMicrobotPlugin current = plugin;
		if (current != null)
		{
			current.requestStart(kind);
			return true;
		}
		pendingStartAt = System.currentTimeMillis();
		pendingStart = kind;
		update("Turning on Microbot Mobile...", "", false);
		return false;
	}

	/** Test hook: ages a queued Start by {@code millis}. */
	static void agePendingStartForTest(long millis)
	{
		pendingStartAt -= millis;
	}

	/** Runs a Start that arrived while the plugin was off; called by the plugin once its runtime is installed. */
	static void startPending()
	{
		MobileMicrobotPlugin current = plugin;
		String kind = pendingStart;
		pendingStart = null;
		boolean fresh = System.currentTimeMillis() - pendingStartAt <= PENDING_START_MILLIS;
		if (current != null && kind != null && fresh)
		{
			current.requestStart(kind);
		}
	}

	public static void stop()
	{
		pendingStart = null;
		MobileMicrobotPlugin current = plugin;
		if (current != null)
		{
			current.requestStop();
		}
	}
}
