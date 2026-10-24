package net.runelite.client.plugins.microbot.api;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.mobilemicrobot.MobileMicrobotConfig;
import net.runelite.client.plugins.mobilemicrobot.MobileMicrobotPlugin;
import net.runelite.client.headless.JavaBridge;
import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real adapters across a complete bank trip; renderer, game state and item transfers are simulated. */
public class MobileBankRecoveryIntegrationTest
{
	static { System.setProperty("runelite.bridge", "true"); }
	private static final WorldPoint TREES = new WorldPoint(3218, 3225, 0);
	private ReadApiFixture fixture;
	private SceneWalkFixture renderer;
	private ScriptInputSession owner;
	private ExecutorService worker;
	private final AtomicReference<WorldPoint> position = new AtomicReference<>(TREES);
	private final AtomicBoolean bankOpen = new AtomicBoolean();
	private final List<String> actions = new CopyOnWriteArrayList<>();
	private final List<Item> items = new ArrayList<>();
	private final CountDownLatch firstWalk = new CountDownLatch(1);
	private volatile boolean frozen = true;
	private ItemContainer inventory;

	@Before
	public void setUp() throws Exception
	{
		JavaBridge.setScriptInputSuspended(false);
		Rs2Walker.setUsePathfinder(false);
		fixture = new ReadApiFixture();
		owner = new ScriptInputSession();
		MobileActions.bind(owner);
		renderer = new SceneWalkFixture(fixture);
		worker = Executors.newSingleThreadExecutor();
		when(fixture.localPlayer.getWorldLocation()).thenAnswer(call -> fixture.onClientThread(position.get()));
		// Deliberately stale even upstairs: the real adapters must use the player's floor.
		when(fixture.topLevel.getPlane()).thenReturn(0);
		CollisionData[] maps = new CollisionData[4];
		for (int plane = 0; plane < maps.length; plane++)
		{
			maps[plane] = mock(CollisionData.class);
			when(maps[plane].getFlags()).thenReturn(new int[32][32]);
		}
		when(fixture.topLevel.getCollisionMaps()).thenReturn(maps);
		object(16671, "Staircase", 3205, 3229, 0, "Climb-up");
		object(16672, "Staircase", 3205, 3229, 1, "Climb-up", "Climb-down");
		object(16673, "Staircase", 3205, 3229, 2, "Climb-down");
		object(1000, "Bank booth", 3209, 3220, 2, "Bank");
		define(1351, "Bronze axe");
		define(1511, "Logs");
		items.add(new Item(1351, 1));
		for (int slot = 1; slot < 28; slot++) { items.add(new Item(1511, 1)); }
		inventory = mock(ItemContainer.class);
		when(inventory.getItems()).thenAnswer(call -> items.toArray(new Item[0]));
		when(fixture.client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);
		when(fixture.client.getItemContainer(net.runelite.api.gameval.InventoryID.INV)).thenReturn(inventory);
		Widget bank = mock(Widget.class);
		when(bank.isHidden()).thenAnswer(call -> !bankOpen.get());
		when(fixture.client.getWidget(12, 1)).thenReturn(bank);
		Widget bankInventory = mock(Widget.class);
		when(bankInventory.getId()).thenReturn(WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId());
		when(bankInventory.isHidden()).thenAnswer(call -> !bankOpen.get());
		when(fixture.client.getWidget(WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER)).thenReturn(bankInventory);
		when(bankInventory.getChild(anyInt())).thenAnswer(call ->
		{
			int slot = call.getArgument(0);
			if (slot < 0 || slot >= items.size()) { return null; }
			Widget item = mock(Widget.class);
			when(item.getItemId()).thenReturn(items.get(slot).getId());
			when(item.getActions()).thenReturn(new String[]{"Deposit-All"});
			return item;
		});
		Widget closeParent = mock(Widget.class);
		Widget close = mock(Widget.class);
		when(close.getActions()).thenReturn(new String[]{"Close"});
		when(close.getName()).thenReturn("");
		when(closeParent.getChild(11)).thenReturn(close);
		when(fixture.client.getWidget((12 << 16) | 2)).thenReturn(closeParent);
		doAnswer(call ->
		{
			assertTrue(fixture.client.isClientThread());
			String option = call.getArgument(5);
			WorldPoint before = position.get();
			if (call.getArgument(2) == MenuAction.WALK)
			{
				assertFalse("the bank must close before returning", bankOpen.get());
				actions.add("walk:" + before.getPlane());
				if (!frozen) { position.set(fixture.topLevel.getSelectedSceneTile().getWorldLocation()); }
				firstWalk.countDown();
			}
			else if (option.startsWith("Climb-"))
			{
				int next = before.getPlane() + (option.equals("Climb-up") ? 1 : -1);
				actions.add("stairs:" + before.getPlane() + ">" + next);
				position.set(new WorldPoint(3206, 3229, next));
			}
			else if (option.equals("Bank"))
			{
				assertEquals("must observe upstairs arrival before opening", 2, before.getPlane());
				assertTrue(before.distanceTo(BankLocation.LUMBRIDGE_TOP.getWorldPoint()) <= 8);
				actions.add("bank:open");
				bankOpen.set(true);
			}
			else if (option.equals("Deposit-All"))
			{
				assertTrue(bankOpen.get());
				assertEquals("never deposit the axe", 1511, (int) call.getArgument(4));
				actions.add("bank:deposit");
				items.removeIf(item -> item.getId() == 1511);
				inventoryChanged();
			}
			else if (option.equals("Close"))
			{
				actions.add("bank:close");
				bankOpen.set(false);
			}
			else { fail("Unexpected engine action " + option); }
			fixture.tick++;
			return null;
		}).when(fixture.client).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), anyString(), anyString());
		fixture.clientExecutor.submit(this::inventoryChanged).get();
		renderer.animate();
	}

	private void inventoryChanged()
	{
		fixture.eventBus.post(new ItemContainerChanged(net.runelite.api.gameval.InventoryID.INV, inventory));
	}

	private void define(int id, String name)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn(name);
		when(fixture.client.getItemDefinition(id)).thenReturn(definition);
	}

	private void object(int id, String name, int x, int y, int plane, String... options)
	{
		GameObject object = mock(GameObject.class);
		Point scene = new Point(x - 3200, y - 3200);
		LocalPoint local = new LocalPoint(scene.getX() * 128 + 64, scene.getY() * 128 + 64, fixture.topLevel);
		when(object.getId()).thenReturn(id);
		when(object.getPlane()).thenReturn(plane);
		when(object.getWorldView()).thenReturn(fixture.topLevel);
		when(object.getWorldLocation()).thenReturn(new WorldPoint(x, y, plane));
		when(object.getLocalLocation()).thenReturn(local);
		when(object.getSceneMinLocation()).thenReturn(scene);
		Tile tile = mock(Tile.class);
		when(tile.getSceneLocation()).thenReturn(scene);
		when(tile.getWorldLocation()).thenReturn(new WorldPoint(x, y, plane));
		when(tile.getGameObjects()).thenReturn(new GameObject[]{object});
		fixture.tiles[plane][scene.getX()][scene.getY()] = tile;
		ObjectComposition definition = mock(ObjectComposition.class);
		when(definition.getName()).thenReturn(name);
		when(definition.getActions()).thenReturn(options);
		when(fixture.client.getObjectDefinition(id)).thenReturn(definition);
	}

	private boolean roundTrip()
	{
		if (!Rs2Bank.walkToBankAndUseBank(BankLocation.LUMBRIDGE_TOP)) { actions.add("failed:approach"); return false; }
		if (!Rs2Bank.depositAllExcept("axe")) { actions.add("failed:deposit logs=" + Rs2Inventory.count(1511)); return false; }
		if (!Rs2Bank.closeBank()) { actions.add("failed:close"); return false; }
		return Rs2Walker.walkTo(TREES, 0);
	}

	private void touchDuringApproach() throws Exception
	{
		assertTrue("walking must start", firstWalk.await(10, TimeUnit.SECONDS));
		fixture.clientExecutor.submit(() ->
		{
			// Start a real native contact, then cancel its arm (e.g. the contact became a drag).
			// This exercises the public manual-input path, not a test-only session mutation.
			JavaBridge.armTap(100, 120);
			JavaBridge.cancelArmedTap();
		}).get();
		fixture.clientExecutor.submit(() -> {}).get();
		assertTrue("a touch must not end the script session", owner.isActive());
		assertTrue("the script must yield to human input", MicrobotRuntime.humanOwnsInput());
	}

	@After
	public void tearDown() throws Exception
	{
		MobileActions.unbind(owner);
		worker.shutdownNow();
		worker.awaitTermination(3, TimeUnit.SECONDS);
		Rs2Walker.setTarget(null);
		Rs2Walker.setUsePathfinder(true);
		renderer.close();
		fixture.close();
		JavaBridge.setScriptInputSuspended(false);
	}

	@Test
	public void manualTouchDuringApproachResumesBanksAllLogsKeepsAxeAndReturns() throws Exception
	{
		Future<Boolean> trip = worker.submit(this::roundTrip);
		touchDuringApproach();
		assertFalse("the approach must still be active", trip.isDone());
		assertFalse(bankOpen.get());
		assertEquals(TREES, position.get());
		frozen = false;
		boolean complete = trip.get(30, TimeUnit.SECONDS);
		assertTrue("trip failed: " + Rs2Walker.getLastStatus() + " " + actions + " owner=" + owner.isActive() + " bank=" + bankOpen.get() + " position=" + position.get(), complete);
		assertEquals(TREES, position.get());
		assertTrue(owner.isActive());
		assertFalse(bankOpen.get());
		assertEquals(1, Rs2Inventory.count(1351));
		assertEquals(0, Rs2Inventory.count(1511));
		assertEquals(1, actions.stream().filter("bank:deposit"::equals).count());
		assertBefore("stairs:0>1", "stairs:1>2");
		assertBefore("stairs:1>2", "bank:open");
		assertBefore("bank:open", "bank:deposit");
		assertBefore("bank:deposit", "bank:close");
		assertBefore("bank:close", "stairs:2>1");
		assertBefore("stairs:2>1", "stairs:1>0");
	}

	private void assertBefore(String first, String second)
	{
		assertTrue(actions.toString(), actions.contains(first) && actions.indexOf(first) < actions.indexOf(second));
	}

	@Test
	public void stopDuringManualInputPauseNeverOpensBankOrDeposits() throws Exception
	{
		Future<Boolean> trip = worker.submit(this::roundTrip);
		touchDuringApproach();
		owner.close();
		worker.shutdownNow();
		assertFalse(trip.get(3, TimeUnit.SECONDS));
		assertFalse(actions.contains("bank:open"));
		assertFalse(actions.contains("bank:deposit"));
		assertEquals(27, Rs2Inventory.count(1511));
		assertEquals(1, Rs2Inventory.count(1351));
		assertNull(Rs2Walker.getTarget());
	}

	@Test
	public void backgroundingEndsTheApproachAndForegroundDoesNotRenewIt() throws Exception
	{
		Future<Boolean> trip = worker.submit(this::roundTrip);
		assertTrue("walking must start", firstWalk.await(10, TimeUnit.SECONDS));
		JavaBridge.setScriptInputSuspended(true);
		assertFalse("backgrounding must close the old input permission", owner.isActive());
		assertFalse("the approach must end without banking", trip.get(3, TimeUnit.SECONDS));
		JavaBridge.setScriptInputSuspended(false);
		frozen = false;
		assertFalse("foregrounding must not reactivate the old session", owner.isActive());
		int dispatched = actions.size();
		assertFalse(MobileActions.walk(BankLocation.LUMBRIDGE_TOP.getWorldPoint()));
		renderer.settle();
		assertEquals("no queued input may run after foregrounding", dispatched, actions.size());
		assertFalse(actions.contains("bank:open"));
		assertFalse(actions.contains("bank:deposit"));
		assertEquals(27, Rs2Inventory.count(1511));
		assertEquals(1, Rs2Inventory.count(1351));
		assertNull(Rs2Walker.getTarget());
	}

	@Test
	public void logoutHookEndsTheApproachAndReconnectDoesNotRestartIt() throws Exception
	{
		// Exercise the real plugin's logout handler with this trip's owning session.
		// The host scheduler is not started; the trip runs on its actual script worker.
		MobileMicrobotPlugin plugin = new MobileMicrobotPlugin();
		AtomicBoolean run = new AtomicBoolean(true);
		MobileMicrobotConfig config = mock(MobileMicrobotConfig.class);
		when(config.runScript()).thenAnswer(call -> run.get());
		ConfigManager manager = mock(ConfigManager.class);
		doAnswer(call -> { run.set(call.getArgument(2)); return null; })
			.when(manager).setConfiguration(eq("mobilemicrobot"), eq("runScript"), any(Object.class));
		inject(plugin, "inputSession", owner);
		inject(plugin, "config", config);
		inject(plugin, "configManager", manager);
		fixture.eventBus.register(plugin);
		try
		{
			Future<Boolean> trip = worker.submit(this::roundTrip);
			assertTrue("walking must start", firstWalk.await(10, TimeUnit.SECONDS));
			fixture.clientExecutor.submit(() -> fixture.gameState(GameState.LOGIN_SCREEN)).get();
			assertFalse("logout must end the owning session", owner.isActive());
			assertFalse("logout must disable Run script", run.get());
			assertFalse(trip.get(3, TimeUnit.SECONDS));
			fixture.clientExecutor.submit(() -> fixture.gameState(GameState.LOGGED_IN)).get();
			frozen = false;
			assertFalse("reconnect must not renew the old permission", owner.isActive());
			assertFalse("reconnect must leave Run script disabled", run.get());
			int dispatched = actions.size();
			assertFalse(MobileActions.walk(TREES));
			renderer.settle();
			assertEquals(dispatched, actions.size());
			assertFalse(actions.contains("bank:open"));
			assertFalse(actions.contains("bank:deposit"));
			// Logout clears read caches; inspect the simulated engine inventory, not stale snapshots.
			assertEquals(28, (int) fixture.clientExecutor.submit(items::size).get());
			assertNull(Rs2Walker.getTarget());
		}
		finally { fixture.eventBus.unregister(plugin); }
	}

	private static void inject(MobileMicrobotPlugin plugin, String name, Object value) throws Exception
	{
		Field field = MobileMicrobotPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(plugin, value);
	}

}
