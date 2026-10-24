package net.runelite.client.plugins.microbot.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.headless.JavaBridge;
import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Item on NPC through the real dispatch boundary: select the item, then target the NPC, one action per tick. */
public class ItemOnNpcDispatchTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private static final int GRAPES = 6749;
	private static final int SLOT = 1;

	private ReadApiFixture f;
	private ScriptInputSession session;
	private final List<String> dispatched = new CopyOnWriteArrayList<>();
	private final AtomicBoolean selected = new AtomicBoolean();
	private NPC leprechaun;

	@Before
	public void setUp() throws Exception
	{
		JavaBridge.setScriptInputSuspended(false);
		f = new ReadApiFixture();
		session = new ScriptInputSession();
		MobileActions.bind(session);

		ItemContainer inventory = mock(ItemContainer.class);
		when(inventory.getItems()).thenReturn(new Item[]{new Item(-1, 0), new Item(GRAPES, 1)});
		when(f.client.getItemContainer(net.runelite.api.InventoryID.INVENTORY)).thenReturn(inventory);
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getName()).thenReturn("Zamorak grapes");
		when(f.client.getItemDefinition(GRAPES)).thenReturn(definition);
		Widget panel = mock(Widget.class);
		Widget item = mock(Widget.class);
		when(f.client.getWidget(WidgetInfo.INVENTORY)).thenReturn(panel);
		when(panel.getId()).thenReturn(WidgetInfo.INVENTORY.getId());
		when(panel.getChild(SLOT)).thenReturn(item);
		when(item.getItemId()).thenReturn(GRAPES);
		when(item.getActions()).thenReturn(new String[]{"Use", null, null, null, "Drop"});

		Widget selection = mock(Widget.class);
		when(selection.getIndex()).thenReturn(SLOT);
		when(selection.getItemId()).thenReturn(GRAPES);
		when(f.client.isWidgetSelected()).thenAnswer(call -> selected.get());
		when(f.client.getSelectedWidget()).thenAnswer(call -> selected.get() ? selection : null);

		leprechaun = f.npc(0, "Tool leprechaun", new WorldPoint(ReadApiFixture.BASE_X + 6, ReadApiFixture.BASE_Y + 5, 0));
		when(leprechaun.getIndex()).thenReturn(42);

		doAnswer(call ->
		{
			assertTrue("engine actions run on the client thread", f.client.isClientThread());
			MenuAction type = call.getArgument(2);
			dispatched.add(type + ":" + call.getArgument(5) + ":" + call.getArgument(3));
			if (type == MenuAction.WIDGET_TARGET)
			{
				selected.set(true);
			}
			f.tick++;
			return null;
		}).when(f.client).menuAction(anyInt(), anyInt(), any(), anyInt(), anyInt(), anyString(), anyString());
	}

	@After
	public void tearDown() throws Exception
	{
		MobileActions.unbind(session);
		f.close();
	}

	@Test
	public void theItemIsSelectedThenUsedOnTheNpcByItsIndex()
	{
		assertTrue(MobileActions.itemOnNpc(SLOT, 42));
		assertEquals(List.of("WIDGET_TARGET:Use:0", "WIDGET_TARGET_ON_NPC:Use:42"), dispatched);
	}

	@Test
	public void aMissingNpcDispatchesNoTarget()
	{
		assertFalse(MobileActions.itemOnNpc(SLOT, 7));
		assertEquals("only the selection went out", List.of("WIDGET_TARGET:Use:0"), dispatched);
	}

	@Test
	public void spellsStillCastOnNpcsThroughTheSameTarget()
	{
		selected.set(true);
		assertTrue(MobileActions.spellOnNpc(42));
		assertEquals(List.of("WIDGET_TARGET_ON_NPC:Cast:42"), dispatched);
	}
}
