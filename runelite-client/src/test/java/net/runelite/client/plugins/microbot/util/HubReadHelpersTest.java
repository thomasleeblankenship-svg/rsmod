package net.runelite.client.plugins.microbot.util;

import java.awt.Rectangle;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.inventory.RunePouchType;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The Hub read-gap helpers: food, potions, time, rune pouches, areas, NPC reads, player model. */
public class HubReadHelpersTest
{
	static
	{
		// JavaBridge captures this flag on first load; set it before any test reaches the bridge.
		System.setProperty("runelite.bridge", "true");
	}

	private static final int BASE_X = 3200;
	private static final int BASE_Y = 3200;

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private final int[][] flags = new int[32][32];
	private Client client;
	private Player local;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		CollisionData collision = mock(CollisionData.class);
		when(collision.getFlags()).thenReturn(flags);
		WorldView view = mock(WorldView.class);
		when(view.getId()).thenReturn(-1);
		when(view.isTopLevel()).thenReturn(true);
		when(view.getBaseX()).thenReturn(BASE_X);
		when(view.getBaseY()).thenReturn(BASE_Y);
		when(view.getCollisionMaps()).thenReturn(new CollisionData[]{collision, null, null, null});
		when(client.getTopLevelWorldView()).thenReturn(view);
		when(client.getWorldView(anyInt())).thenAnswer(i -> (int) i.getArgument(0) == -1 ? view : null);
		local = mock(Player.class);
		when(local.getName()).thenReturn("Me");
		when(local.getWorldView()).thenReturn(view);
		when(local.getWorldLocation()).thenReturn(at(0, 0));
		when(client.getLocalPlayer()).thenReturn(local);
		@SuppressWarnings("unchecked")
		IndexedObjectSet<Player> players = mock(IndexedObjectSet.class);
		when(players.iterator()).thenAnswer(i -> List.of(local).iterator());
		doAnswer(i -> players).when(view).players();

		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			onClientThread.set(true);
			try { ((Runnable) call.getArgument(0)).run(); }
			finally { onClientThread.set(false); }
			return null;
		}).when(thread).invoke(any(Runnable.class));
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	private static WorldPoint at(int dx, int dy)
	{
		return new WorldPoint(BASE_X + 5 + dx, BASE_Y + 5 + dy, 0);
	}

	@Test
	public void foodPotionAndTimeData()
	{
		assertEquals(ItemID.MONKFISH, Rs2Food.MONKFISH.getId());
		assertEquals(16, Rs2Food.MONKFISH.getHeal());
		assertEquals("Salmon (+9)", Rs2Food.SALMON.toString());
		assertEquals("Potato with Cheese", Rs2Food.POTATO_WITH_CHEESE.getName());
		assertEquals("corrected from upstream's 27", 22, Rs2Food.Dark_Crab.getHeal());
		assertTrue(Rs2Food.getIds().contains(ItemID.ANGLERFISH));

		assertEquals("Stamina potion", Rs2Potion.getStaminaPotion());
		assertTrue(Rs2Potion.getRestoreEnergyPotionsVariants().contains("Super energy"));

		Instant start = Instant.parse("2026-10-23T00:00:00Z");
		assertEquals("1:02:03", TimeUtils.getFormattedDurationBetween(start, start.plusSeconds(3723)));
		assertEquals("26:00:00", TimeUtils.getFormattedDurationBetween(start, start.plusSeconds(26 * 3600)));
		assertEquals("-0:00:05", TimeUtils.getFormattedDurationBetween(start, start.minusSeconds(5)));
	}

	@Test
	public void runePouchesAndAreas()
	{
		assertEquals(ItemID.DIVINE_RUNE_POUCH, RunePouchType.DIVINE.getItemId());
		assertEquals(5, RunePouchType.values().length);

		Rs2WorldArea mound = new Rs2WorldArea(3573, 3296, 3, 3, 0);
		assertTrue(mound.contains(new WorldPoint(3575, 3298, 0)));
		assertFalse(mound.contains(new WorldPoint(3576, 3298, 0)));
		assertEquals(9, mound.toWorldPointList().size());
	}

	@Test
	public void colourTagsStripAndClickboxFallsBackToEmpty()
	{
		assertEquals("Fire rune", Rs2UiHelper.stripColTags("<col=ff9040>Fire rune</col>"));
		assertEquals("", Rs2UiHelper.stripColTags(null));

		NPC npc = mock(NPC.class);
		when(npc.getConvexHull()).thenReturn(new Rectangle(4, 5, 6, 7));
		assertEquals(new Rectangle(4, 5, 6, 7), Rs2UiHelper.getActorClickbox(npc));
		assertEquals(new Rectangle(), Rs2UiHelper.getActorClickbox(mock(NPC.class)));
	}

	@Test
	public void npcActionsAndWalkability()
	{
		NPCComposition mage = mock(NPCComposition.class);
		when(mage.getActions()).thenReturn(new String[]{"Talk-to", null, "Repair", null, null});
		when(client.getNpcDefinition(7000)).thenReturn(mage);
		assertTrue(Rs2Npc.hasAction(7000, "repair"));
		assertFalse(Rs2Npc.hasAction(7000, "Trade"));
		assertFalse(Rs2Npc.hasAction(7001, "Repair"));

		NPC near = mock(NPC.class);
		when(near.getWorldLocation()).thenReturn(at(3, 0));
		NPC walled = mock(NPC.class);
		when(walled.getWorldLocation()).thenReturn(at(10, 10));
		for (int ox = -1; ox <= 1; ox++)
		{
			for (int oy = -1; oy <= 1; oy++)
			{
				flags[15 + ox][15 + oy] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
			}
		}
		assertTrue(Rs2Npc.canWalkTo(near, 10));
		assertFalse("too far for the allowed distance", Rs2Npc.canWalkTo(near, 1));
		assertFalse("walled in", Rs2Npc.canWalkTo(walled, 20));
	}

	@Test
	public void playerQueriesReturnTheMicrobotPlayerModel()
	{
		Rs2PlayerModel me = Rs2Player.getLocalPlayer();
		assertEquals("Me", me.getName());
		assertEquals(1, Rs2Player.getPlayers(p -> true, true).count());
		assertEquals(0, Rs2Player.getPlayers(p -> true).count());
	}
}
