package net.runelite.client.plugins.microbot.util.menu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import org.junit.Test;

/** Checks that {@link NewMenuEntry} keeps upstream's constructor mapping, setters and equality. */
public class NewMenuEntryTest
{
	@Test
	public void widgetConstructorPutsTextInOption()
	{
		NewMenuEntry e = new NewMenuEntry(-1, 1234, MenuAction.CC_OP.getId(), 1, 995, "Withdraw-1");
		assertEquals("Withdraw-1", e.getOption());
		assertEquals("", e.getTarget());
		assertEquals(MenuAction.CC_OP, e.getType());
		assertEquals(995, e.getItemId());
		assertEquals(WorldView.TOPLEVEL, e.getWorldViewId());
	}

	@Test
	public void actorAndObjectConstructorsKeepTheirTarget()
	{
		NPC npc = mock(NPC.class);
		NewMenuEntry onNpc = new NewMenuEntry(0, 0, MenuAction.NPC_FIRST_OPTION.getId(), 7, -1, "Banker", npc);
		assertEquals("Use", onNpc.getOption());
		assertSame(npc, onNpc.getNpc());
		assertNull(onNpc.getPlayer());

		TileObject tree = mock(TileObject.class);
		NewMenuEntry onTree = new NewMenuEntry(50, 60, MenuAction.GAME_OBJECT_FIRST_OPTION.getId(), 1276, -1, "Chop down", "Tree", tree, 3);
		assertSame(tree, onTree.getGameObject());
		assertEquals(3, onTree.getWorldViewId());
		assertEquals(50, onTree.getParam0());
		assertEquals(60, onTree.getParam1());
	}

	@Test
	public void fluentSettersAndEquality()
	{
		NewMenuEntry a = new NewMenuEntry().option("Walk here").target("").opcode(MenuAction.WALK.getId()).param0(1).param1(2);
		NewMenuEntry b = new NewMenuEntry("Walk here", "", 0, MenuAction.WALK, 1, 2, false);
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertNotEquals(a, b.forceLeftClick(true));
	}

	@Test
	public void findIdentifierUsesDefaultOffset()
	{
		assertEquals(65542, NewMenuEntry.findIdentifier(1));
		assertEquals(131070, NewMenuEntry.findIdentifier(2, -2));
	}
}
