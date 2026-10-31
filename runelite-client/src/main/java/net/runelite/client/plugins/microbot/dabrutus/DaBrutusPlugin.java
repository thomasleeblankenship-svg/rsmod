package net.runelite.client.plugins.microbot.dabrutus;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.NPC;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "[S] Da-Brutus",
	description = "Kills Brutus, the cow boss (Lumbridge cow field): does The Ides of Milk first, dodges his charge and "
		+ "slam, Protect from Melee, eats, loots, cowbell teleport and fast respawn, Lumbridge banking. F2P",
	tags = {"brutus", "cow", "boss", "ides of milk", "lumbridge", "pvm", "microbot"},
	authors = {"Da-Trainer"},
	version = DaBrutusPlugin.version,
	minClientVersion = "2.6.19",
	enabledByDefault = false,
	isExternal = true
)
@Slf4j
public class DaBrutusPlugin extends Plugin
{
	static final String version = "0.6.0";

	@Inject
	private DaBrutusConfig config;
	@Inject
	private ConfigManager configManager;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private DaBrutusScript script;

	@Inject
	private BrutusPaint overlay;

	@Provides
	DaBrutusConfig provideConfig(ConfigManager configManager)
	{
		DaBrutusConfig config = configManager.getConfig(DaBrutusConfig.class);
		// Settings with no saved value yet get their default here, when the plugin loads (never overwrites the user's).
		// RuneLite only does this at client start: a hot-swapped build with a new setting (0.5.15's Combat mode) had no
		// value, and its config panel threw "Name is null" in ConfigPanel.createComboBox (live 2026-10-05 13:48).
		configManager.setDefaultConfiguration(config, false);
		return config;
	}

	@Override
	protected void startUp()
	{
		configManager.setDefaultConfiguration(config, false); // again, in case the defaults changed since load
		overlayManager.add(overlay);
		script.store(new PersonaStore(configManager));
		script.run(config);
	}

	@Override
	protected void shutDown()
	{
		script.shutdown();
		overlayManager.remove(overlay);
	}

	@Subscribe
	public void onOverheadTextChanged(OverheadTextChanged event)
	{
		if (event.getActor() instanceof NPC)
		{
			BrutusEvents.onOverhead((NPC) event.getActor(), event.getOverheadText());
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (event.getActor() instanceof NPC)
		{
			BrutusEvents.onAnimation((NPC) event.getActor());
		}
	}

	@Subscribe
	public void onGraphicsObjectCreated(GraphicsObjectCreated event)
	{
		if (BrutusEvents.SLAM_IMPACTS.contains(event.getGraphicsObject().getId()))
		{
			BrutusEvents.onSlamImpact();
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		if (BrutusEvents.isBrutus(npc) && npc.isDead())
		{
			BrutusEvents.countKill(npc);
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM
			|| event.getType() == ChatMessageType.ENGINE || event.getType() == ChatMessageType.MESBOX)
		{
			BrutusEvents.onGameMessage(Text.removeTags(event.getMessage()));
		}
		if (event.getType() == ChatMessageType.GAMEMESSAGE && event.getMessage().equalsIgnoreCase("Oh dear, you are dead!"))
		{
			// As Da-Scurrius: don't stop; the script walks back, loots the grave and carries on.
			DaBrutusScript.markDeath();
		}
	}
}
