package net.runelite.client.plugins.microbot.util.player;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Prayer and anti-poison drinking on top of the potion-effect reads. */
public class PotionActionTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private Client client;
	private final AtomicBoolean clientThread = new AtomicBoolean(true);
	private final EventBus events = new EventBus();
	private volatile int prayer = 10;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> clientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getBoostedSkillLevel(Skill.PRAYER)).thenAnswer(call -> prayer);
		when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			clientThread.set(true);
			try { ((Runnable) call.getArgument(0)).run(); }
			finally { clientThread.set(false); }
			return null;
		}).when(thread).invoke(any(Runnable.class));
		MicrobotReadServices services = new MicrobotReadServices(client, thread, events);
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class), List.of(services));
		define(139, "Prayer potion(3)");
		define(2446, "Antipoison(4)");
		define(995, "Coins");
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(new Item[]{new Item(995, 10), new Item(139, 1), new Item(2446, 1)});
		when(client.getItemContainer(InventoryID.INV)).thenReturn(container);
		events.post(new ItemContainerChanged(InventoryID.INV, container));
		clientThread.set(false);
		Rs2Player.antiPoisonTime = -1;
		Rs2Player.antiVenomTime = -1;
	}

	@After
	public void tearDown()
	{
		Rs2Player.antiPoisonTime = -1;
		Rs2Player.antiVenomTime = -1;
		MicrobotRuntime.uninstall();
	}

	private void define(int id, String name)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn(name);
		when(definition.getNote()).thenReturn(-1);
		when(definition.getInventoryActions()).thenReturn(new String[]{"Drink", null, null, null, "Drop"});
		when(client.getItemDefinition(id)).thenReturn(definition);
	}

	private MockedStatic<MobileActions> actions()
	{
		MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS);
		actions.when(() -> MobileActions.inventory(anyInt(), anyInt(), anyString())).thenReturn(true);
		actions.clearInvocations();
		return actions;
	}

	@Test
	public void aPrayerPotionIsDrunkAtOrBelowTheThresholdOnly()
	{
		try (MockedStatic<MobileActions> actions = actions();
			MockedStatic<Microbot> microbot = mockStatic(Microbot.class, CALLS_REAL_METHODS))
		{
			microbot.when(() -> Microbot.getVarbitValue(anyInt())).thenReturn(0);
			prayer = 30;
			assertFalse("above the threshold", Rs2Player.drinkPrayerPotionAt(20));
			actions.verify(() -> MobileActions.inventory(anyInt(), anyInt(), anyString()), never());

			prayer = 20;
			assertTrue(Rs2Player.drinkPrayerPotionAt(20));
			actions.verify(() -> MobileActions.inventory(1, 139, "Drink"));
		}
	}

	@Test
	public void antiPoisonIsDrunkOnlyWithoutProtection()
	{
		try (MockedStatic<MobileActions> actions = actions())
		{
			Rs2Player.antiPoisonTime = 30;
			assertTrue("already protected: nothing to drink", Rs2Player.drinkAntiPoisonPotion());
			actions.verify(() -> MobileActions.inventory(anyInt(), anyInt(), anyString()), never());

			Rs2Player.antiPoisonTime = -1;
			assertTrue(Rs2Player.drinkAntiPoisonPotion());
			actions.verify(() -> MobileActions.inventory(2, 2446, "Drink"));
		}
	}
}
