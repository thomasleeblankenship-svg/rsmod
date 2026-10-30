package net.runelite.client.plugins.microbot.util.player;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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

/** Util-gap action batch A: combat potions, the separate-thread runner, the inert natural mouse, the run threshold. */
public class HubActionBatchATest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private static final int SUPER_COMBAT = 12695;
	private static final int ATTACK = 2428;
	private static final int RANGING = 2444;
	private Client client;
	private ClientThread thread;
	private final AtomicBoolean clientThread = new AtomicBoolean(true);
	private final EventBus events = new EventBus();
	private volatile int boosted = 70;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> clientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getRealSkillLevel(any(Skill.class))).thenReturn(70);
		when(client.getBoostedSkillLevel(any(Skill.class))).thenAnswer(call -> boosted);
		when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
		thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			clientThread.set(true);
			try { ((Runnable) call.getArgument(0)).run(); }
			finally { clientThread.set(false); }
			return null;
		}).when(thread).invoke(any(Runnable.class));
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class), List.of(new MicrobotReadServices(client, thread, events)));
		define(SUPER_COMBAT, "Super combat potion(4)");
		define(ATTACK, "Attack potion(4)");
		define(RANGING, "Ranging potion(4)");
		ItemContainer container = mock(ItemContainer.class);
		when(container.getItems()).thenReturn(new Item[]{new Item(ATTACK, 1), new Item(SUPER_COMBAT, 1), new Item(RANGING, 1)});
		when(client.getItemContainer(InventoryID.INV)).thenReturn(container);
		events.post(new ItemContainerChanged(InventoryID.INV, container));
		clientThread.set(false);
	}

	@After
	public void tearDown()
	{
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

	@Test
	public void combatPotionsPreferSuperCombatAndRespectTheBoost()
	{
		try (MockedStatic<MobileActions> actions = mockStatic(MobileActions.class, CALLS_REAL_METHODS))
		{
			actions.when(() -> MobileActions.inventory(anyInt(), anyInt(), anyString())).thenReturn(true);
			actions.clearInvocations();
			assertTrue(Rs2Player.drinkCombatPotionAt(Skill.ATTACK));
			actions.verify(() -> MobileActions.inventory(1, SUPER_COMBAT, "Drink"));

			actions.clearInvocations();
			assertTrue("no super combat preference: the skill's own potion", Rs2Player.drinkCombatPotionAt(Skill.ATTACK, false));
			actions.verify(() -> MobileActions.inventory(0, ATTACK, "Drink"));

			actions.clearInvocations();
			assertTrue(Rs2Player.drinkCombatPotionAt(Skill.RANGED));
			actions.verify(() -> MobileActions.inventory(2, RANGING, "Drink"));

			boosted = 74; // 4 over 70: more than 20% of the 15.5 maximum boost
			actions.clearInvocations();
			assertFalse("already boosted", Rs2Player.drinkCombatPotionAt(Skill.ATTACK));
			actions.verify(() -> MobileActions.inventory(anyInt(), anyInt(), anyString()), never());
			assertFalse(Rs2Player.drinkCombatPotionAt(Skill.PRAYER));
		}
	}

	@Test
	public void aSeparateThreadTaskIsSkippedWhileThePreviousOneRuns() throws Exception
	{
		ClientThread real = ReadApiFixture.clientThreadFor(client);
		CountDownLatch release = new CountDownLatch(1);
		CountDownLatch started = new CountDownLatch(1);
		AtomicInteger runs = new AtomicInteger();
		real.runOnSeperateThread(() -> { runs.incrementAndGet(); started.countDown(); release.await(); return null; });
		assertTrue(started.await(2, TimeUnit.SECONDS));
		real.runOnSeperateThread(() -> runs.incrementAndGet());
		release.countDown();
		Thread.sleep(100);
		assertEquals("the second task was skipped while the first ran", 1, runs.get());
		CountDownLatch done = new CountDownLatch(1);
		real.runOnSeperateThread(() -> { runs.incrementAndGet(); done.countDown(); return null; });
		assertTrue(done.await(2, TimeUnit.SECONDS));
		assertEquals(2, runs.get());
	}

	@Test
	public void humanisedMouseStaysOffAndTheRunThresholdHasUpstreamsDefault()
	{
		assertNotNull("the humanised mouse drives the anti-ban's random and off-screen moves", Microbot.naturalMouse);
		assertEquals(1000, Microbot.runEnergyThreshold);
	}
}
