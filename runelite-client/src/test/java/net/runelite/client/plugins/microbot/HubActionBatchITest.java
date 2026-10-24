package net.runelite.client.plugins.microbot;

import java.util.stream.Stream;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util-gap action batch I: Microbot lookups, bank containers and the attack style. */
@SuppressWarnings("deprecation")
public class HubActionBatchITest
{
	@After
	public void clear()
	{
		Microbot.unbind();
	}

	@Test
	public void supplyMessagesAreNormalisedAsUpstream()
	{
		assertEquals("Ran out of supplies: Shark", Microbot.supplyMessage("out of Shark"));
		assertEquals("Ran out of supplies: Prayer potion(4)", Microbot.supplyMessage("Prayer potion(4) not found in bank."));
		assertEquals("Ran out of supplies: Lobster", Microbot.supplyMessage("Ran out of supplies: Lobster"));
		assertEquals("Ran out of supplies: required supplies", Microbot.supplyMessage(null));
		assertEquals("Ran out of supplies: required supplies", Microbot.supplyMessage("  "));
	}

	@Test
	public void theInfoBoxManagerIsBoundAndCleared()
	{
		InfoBoxManager manager = mock(InfoBoxManager.class);
		Microbot.bindInfoBoxManager(manager);
		assertSame(manager, Microbot.getInfoBoxManager());
		Microbot.unbind();
		assertNull(Microbot.getInfoBoxManager());
	}

	@Test
	public void cacheLookupsAreNullWithoutAClient()
	{
		assertNull(Microbot.getEnum(1));
		assertNull(Microbot.getStructComposition(1));
	}

	@Test
	public void aCarriedSeedBoxIsEmptied()
	{
		Rs2ItemModel box = mock(Rs2ItemModel.class);
		when(box.getId()).thenReturn(ItemID.SEED_BOX_OPEN);
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class))
		{
			inventory.when(() -> Rs2Inventory.items(any())).thenAnswer(a -> Stream.of(box).filter(a.getArgument(0)));
			inventory.when(() -> Rs2Inventory.interact(any(Rs2ItemModel.class), anyString())).thenReturn(true);
			assertTrue(Rs2Bank.emptySeedBox());
			inventory.verify(() -> Rs2Inventory.interact(box, "Empty"));

			inventory.when(() -> Rs2Inventory.items(any())).thenReturn(Stream.empty());
			assertFalse("no seed box carried", Rs2Bank.emptySeedBox());
		}
	}

	@Test
	public void theLootingBagIsViewedDepositedAndClosed()
	{
		Widget deposit = mock(Widget.class);
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
			MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class);
			MockedStatic<Rs2Bank> bank = mockStatic(Rs2Bank.class, CALLS_REAL_METHODS))
		{
			bank.when(Rs2Bank::isOpen).thenReturn(true);
			inventory.when(() -> Rs2Inventory.contains(ItemID.LOOTING_BAG_OPEN)).thenReturn(true);
			inventory.when(() -> Rs2Inventory.interact(ItemID.LOOTING_BAG_OPEN, "View")).thenReturn(true);
			inventory.when(() -> Rs2Inventory.waitFor(any(), anyInt())).thenAnswer(a -> ((java.util.function.BooleanSupplier) a.getArgument(0)).getAsBoolean());
			widget.when(() -> Rs2Widget.getWidget(983046)).thenReturn(deposit);
			widget.when(() -> Rs2Widget.clickWidget(anyInt())).thenReturn(true);
			assertTrue(Rs2Bank.depositLootingBag());
			widget.verify(() -> Rs2Widget.clickWidget(983046));
			widget.verify(() -> Rs2Widget.clickWidget(983048));

			widget.clearInvocations();
			bank.when(Rs2Bank::isOpen).thenReturn(false);
			assertFalse("the bank must be open", Rs2Bank.depositLootingBag());
			widget.verify(() -> Rs2Widget.clickWidget(anyInt()), never());
		}
	}

	@Test
	public void theAttackStyleButtonIsClickedOnlyWhenLoaded()
	{
		int id = WidgetInfo.COMBAT_STYLE_TWO.getId();
		try (MockedStatic<Rs2Widget> widget = mockStatic(Rs2Widget.class))
		{
			widget.when(() -> Rs2Widget.clickWidget(anyInt())).thenReturn(true);
			assertFalse("not loaded", Rs2Combat.setAttackStyle(WidgetInfo.COMBAT_STYLE_TWO));
			widget.verify(() -> Rs2Widget.clickWidget(anyInt()), never());

			widget.when(() -> Rs2Widget.getWidget(id)).thenReturn(mock(Widget.class));
			assertTrue(Rs2Combat.setAttackStyle(WidgetInfo.COMBAT_STYLE_TWO));
			widget.verify(() -> Rs2Widget.clickWidget(id));
			assertFalse(Rs2Combat.setAttackStyle(null));
		}
	}
}
