package net.runelite.client.plugins.microbot.api;

import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Upstream's queryable {@code interact} overloads: nearest reachable match, dispatched through MobileActions. */
public class EntityInteractTest
{
	static
	{
		// Rs2Walker and MobileActions reach JavaBridge; set the flag before it loads.
		System.setProperty("runelite.bridge", "true");
	}

	private ReadApiFixture f;
	private int[][] flags;
	private ScriptInputSession session;

	@Before
	public void setUp() throws Exception
	{
		f = new ReadApiFixture();
		flags = new int[32][32];
		CollisionData collision = mock(CollisionData.class);
		when(collision.getFlags()).thenAnswer(i -> flags);
		when(f.topLevel.getCollisionMaps()).thenReturn(new CollisionData[]{collision, null, null, null});
		when(f.topLevel.isInstance()).thenReturn(false);
	}

	@After
	public void tearDown() throws Exception
	{
		if (session != null)
		{
			MobileActions.unbind(session);
		}
		f.close();
	}

	private WorldPoint at(int dx, int dy)
	{
		return new WorldPoint(ReadApiFixture.BASE_X + 5 + dx, ReadApiFixture.BASE_Y + 5 + dy, 0);
	}

	private NPC npc(int id, int index, String name, WorldPoint location)
	{
		NPC npc = f.npc(id, name, location);
		when(npc.getIndex()).thenReturn(index);
		NPCComposition definition = mock(NPCComposition.class);
		when(definition.getActions()).thenReturn(new String[]{"Talk-to", "Attack", null, null, null});
		when(npc.getTransformedComposition()).thenReturn(definition);
		return npc;
	}

	private void bind()
	{
		session = new ScriptInputSession();
		MobileActions.bind(session);
	}

	@Test
	public void interactActsOnTheNearestReachableNpc()
	{
		npc(7, 11, "Caged", at(2, 0));
		npc(7, 12, "Free", at(6, 0));
		for (int ox = -1; ox <= 1; ox++)
		{
			for (int oy = -1; oy <= 1; oy++)
			{
				flags[7 + ox][5 + oy] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
			}
		}
		bind();

		assertTrue(f.services.getRs2NpcCache().query().interact(7, "Attack"));
		verify(f.client).menuAction(eq(0), eq(0), eq(MenuAction.NPC_SECOND_OPTION), eq(12), eq(-1), eq("Attack"), eq("Free"));
		verify(f.client, never()).menuAction(anyInt(), anyInt(), any(), eq(11), anyInt(), any(), any());
	}

	@Test
	public void interactWithoutAnActionUsesTheFirstAction()
	{
		npc(3, 20, "Banker", at(1, 0));
		bind();

		assertTrue(f.services.getRs2NpcCache().query().withName("Banker").interact());
		verify(f.client).menuAction(eq(0), eq(0), eq(MenuAction.NPC_FIRST_OPTION), eq(20), eq(-1), eq("Talk-to"), eq("Banker"));
	}

	@Test
	public void interactIsRefusedWithoutABoundScriptSessionOrAMatch()
	{
		npc(3, 20, "Banker", at(1, 0));
		assertFalse(f.services.getRs2NpcCache().query().interact("Talk-to"));
		bind();
		assertFalse(f.services.getRs2NpcCache().query().interact(999, "Talk-to"));
		verify(f.client, never()).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), any(), any());
	}
}
