package net.runelite.client.plugins.microbot.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.CollisionData;
import net.runelite.api.GameObject;
import net.runelite.api.MenuAction;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.headless.JavaBridge;
import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real walker, read services and actions; fake engine observes floors independently of the worldview plane. */
public class MobileWalkerIntegrationTest
{
	static
	{
		// JavaBridge captures this flag on first load, including indirect action dispatch.
		System.setProperty("runelite.bridge", "true");
	}

	private ReadApiFixture fixture;
	private ScriptInputSession permission;
	private SceneWalkFixture sceneWalk;
	private final AtomicReference<WorldPoint> position = new AtomicReference<>();
	private final List<String> actions = new CopyOnWriteArrayList<>();
	private final CountDownLatch firstStair = new CountDownLatch(1);
	private boolean changePlane = true;
	/** While true the fake engine accepts WALK but the player does not move (a stall). */
	private volatile boolean frozen;

	@Before
	public void setUp() throws Exception
	{
		JavaBridge.setScriptInputSuspended(false);
		fixture = new ReadApiFixture();
		permission = new ScriptInputSession();
		MobileActions.bind(permission);
		sceneWalk = new SceneWalkFixture(fixture);
		sceneWalk.animate();
		position.set(new WorldPoint(3209, 3220, 2));
		doAnswer(call -> fixture.onClientThread(position.get())).when(fixture.localPlayer).getWorldLocation();
		// Deliberately stale: routing, collision and object capture must use the actual player's floor.
		when(fixture.topLevel.getPlane()).thenReturn(0);
		CollisionData[] maps = new CollisionData[4];
		for (int plane = 0; plane < maps.length; plane++)
		{
			maps[plane] = mock(CollisionData.class);
			when(maps[plane].getFlags()).thenReturn(new int[32][32]);
		}
		when(fixture.topLevel.getCollisionMaps()).thenReturn(maps);
		staircase(16673, 2, new String[]{"Climb-down"});
		staircase(16672, 1, new String[]{"Climb-up", "Climb-down"});
		staircase(16671, 0, new String[]{"Climb-up"});
		doAnswer(call ->
		{
			assertTrue("engine actions must execute on the client thread", fixture.client.isClientThread());
			MenuAction type = call.getArgument(2);
			String option = call.getArgument(5);
			WorldPoint before = position.get();
			if (type == MenuAction.WALK)
			{
				actions.add("walk:" + before.getPlane());
				assertEquals(100, (int) call.getArgument(0));
				assertEquals(120, (int) call.getArgument(1));
				// WALK consumes the renderer's selected tile, not its menu parameters.
				if (!frozen)
				{
					position.set(fixture.topLevel.getSelectedSceneTile().getWorldLocation());
				}
			}
			else if ("Climb-down".equals(option))
			{
				int id = call.getArgument(3);
				assertTrue("must target the live legacy staircase ID", id == 16673 || id == 16672);
				actions.add("stairs:" + before.getPlane() + ">" + (before.getPlane() - 1));
				if (changePlane)
				{
					position.set(new WorldPoint(3205, 3228, before.getPlane() - 1));
				}
				firstStair.countDown();
			}
			else
			{
				fail("Unexpected engine action " + option);
			}
			fixture.tick++;
			return null;
		}).when(fixture.client).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), anyString(), anyString());
	}

	private void staircase(int id, int plane, String[] choices)
	{
		GameObject object = mock(GameObject.class);
		Point local = new Point(5, 29);
		when(object.getId()).thenReturn(id);
		when(object.getPlane()).thenReturn(plane);
		when(object.getWorldView()).thenReturn(fixture.topLevel);
		when(object.getWorldLocation()).thenReturn(new WorldPoint(3205, 3229, plane));
		LocalPoint point = new LocalPoint(5 * 128 + 64, 29 * 128 + 64, fixture.topLevel);
		when(object.getLocalLocation()).thenReturn(point);
		when(object.getSceneMinLocation()).thenReturn(local);
		Tile tile = mock(Tile.class);
		when(tile.getSceneLocation()).thenReturn(local);
		when(tile.getWorldLocation()).thenReturn(new WorldPoint(3205, 3229, plane));
		when(tile.getGameObjects()).thenReturn(new GameObject[]{object});
		fixture.tiles[plane][5][29] = tile;
		ObjectComposition definition = mock(ObjectComposition.class);
		when(definition.getName()).thenReturn("Staircase");
		when(definition.getActions()).thenReturn(choices);
		when(fixture.client.getObjectDefinition(id)).thenReturn(definition);
	}

	@After
	public void tearDown() throws Exception
	{
		Rs2Walker.setStallMillis(0);
		Rs2Walker.setTarget(null);
		MobileActions.unbind(permission);
		JavaBridge.setScriptInputSuspended(false);
		sceneWalk.close();
		fixture.close();
	}

	@Test
	public void startsAtBankAndDescendsBothFloorsBeforeGroundTravel()
	{
		WorldPoint destination = new WorldPoint(3218, 3225, 0);
		boolean arrived = Rs2Walker.walkTo(destination, 0);
		assertTrue(Rs2Walker.getLastStatus(), arrived);
		assertEquals(destination, position.get());
		int upper = actions.indexOf("stairs:2>1");
		int lower = actions.indexOf("stairs:1>0");
		int ground = actions.indexOf("walk:0");
		assertTrue(actions.toString(), upper >= 0);
		assertTrue(actions.toString(), lower > upper);
		assertTrue(actions.toString(), ground > lower);
		assertNull(Rs2Walker.getTarget());
	}

	@Test
	public void acceptedStairClickCannotSucceedWithoutObservedPlaneAndCancellationStopsTravel() throws Exception
	{
		changePlane = false;
		ExecutorService worker = Executors.newSingleThreadExecutor();
		try
		{
			Future<Boolean> walk = worker.submit(() -> Rs2Walker.walkTo(new WorldPoint(3218, 3225, 0), 0));
			assertTrue("real upstairs route must reach a staircase", firstStair.await(5, TimeUnit.SECONDS));
			assertFalse("enqueue must not be mistaken for arrival", walk.isDone());
			permission.close();
			assertFalse(walk.get(3, TimeUnit.SECONDS));
			assertEquals(2, position.get().getPlane());
			assertFalse(actions.contains("stairs:1>0"));
			assertFalse(actions.contains("walk:0"));
			assertNull(Rs2Walker.getTarget());
		}
		finally
		{
			worker.shutdownNow();
		}
	}

	@Test
	public void stalledWalkRetriesInsteadOfStoppingAndThenArrives() throws Exception
	{
		Rs2Walker.setStallMillis(300);
		position.set(new WorldPoint(3209, 3220, 0));
		frozen = true;
		ExecutorService worker = Executors.newSingleThreadExecutor();
		try
		{
			WorldPoint destination = new WorldPoint(3218, 3225, 0);
			Future<Boolean> walk = worker.submit(() -> Rs2Walker.walkTo(destination, 0));
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			while (!Rs2Walker.getLastStatus().startsWith("Walk stalled; retrying") && System.nanoTime() < deadline)
			{
				Thread.sleep(10);
			}
			assertTrue("a stall must be retried, status: " + Rs2Walker.getLastStatus(),
				Rs2Walker.getLastStatus().startsWith("Walk stalled; retrying"));
			assertFalse("a stall must not end the walk", walk.isDone());

			frozen = false;
			assertTrue(Rs2Walker.getLastStatus(), walk.get(10, TimeUnit.SECONDS));
			assertEquals(destination, position.get());
		}
		finally
		{
			worker.shutdownNow();
		}
	}

	@Test
	public void stopEndsAStalledRetryingWalkPromptly() throws Exception
	{
		Rs2Walker.setStallMillis(300);
		position.set(new WorldPoint(3209, 3220, 0));
		frozen = true;
		ExecutorService worker = Executors.newSingleThreadExecutor();
		try
		{
			Future<Boolean> walk = worker.submit(() -> Rs2Walker.walkTo(new WorldPoint(3218, 3225, 0), 0));
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			while (!Rs2Walker.getLastStatus().startsWith("Walk stalled; retrying") && System.nanoTime() < deadline)
			{
				Thread.sleep(10);
			}
			permission.close();
			worker.shutdownNow(); // Stop interrupts the script thread
			assertFalse(walk.get(3, TimeUnit.SECONDS));
			assertNull(Rs2Walker.getTarget());
		}
		finally
		{
			worker.shutdownNow();
		}
	}
}
