package net.runelite.client.plugins.microbot;

import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.agility.AgilityPlugin;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.thralls.Rs2Thrall;
import net.runelite.client.plugins.microbot.util.mouse.BotEventGuard;
import net.runelite.client.ui.overlay.components.ButtonComponent;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util-gap action batch H: mouse manager binding, overlay buttons, thrall casting and the agility stick tile. */
public class HubActionBatchHTest
{
	@After
	public void clear()
	{
		Microbot.unbind();
	}

	@Test
	public void overlayButtonsRegisterOnTheBoundMouseManager()
	{
		MouseManager manager = mock(MouseManager.class);
		Microbot.bindMouseManager(manager);
		assertSame(manager, Microbot.getMouseManager());

		ButtonComponent button = new ButtonComponent("Start");
		button.hookMouseListener();
		verify(manager).registerMouseListener(any(MouseListener.class));
		button.unhookMouseListener();
		verify(manager).unregisterMouseListener(any(MouseListener.class));
		assertFalse(button.isHovered());

		Microbot.unbind();
		assertNull("cleared on uninstall", Microbot.getMouseManager());
	}

	@Test
	public void theBotEventGuardNests()
	{
		assertFalse(BotEventGuard.isSynthetic());
		BotEventGuard.begin();
		BotEventGuard.begin();
		BotEventGuard.end();
		assertTrue(BotEventGuard.isSynthetic());
		BotEventGuard.end();
		assertFalse(BotEventGuard.isSynthetic());
	}

	@Test
	public void aThrallIsCastOnlyWhenItCanBe()
	{
		try (MockedStatic<Rs2Magic> magic = mockStatic(Rs2Magic.class))
		{
			magic.when(() -> Rs2Magic.cast(any(Rs2Thrall.class))).thenReturn(true);
			magic.clearInvocations();
			try (MockedStatic<Rs2Thrall> thrall = mockStatic(Rs2Thrall.class, CALLS_REAL_METHODS))
			{
				thrall.when(() -> Rs2Thrall.canCast(any())).thenReturn(false);
				assertFalse(Rs2Thrall.cast(Rs2Thrall.GREATER_GHOST));
				magic.verify(() -> Rs2Magic.cast(any(Rs2Thrall.class)), never());

				thrall.when(() -> Rs2Thrall.canCast(any())).thenReturn(true);
				assertTrue(Rs2Thrall.cast(Rs2Thrall.GREATER_GHOST));
				magic.verify(() -> Rs2Magic.cast(Rs2Thrall.GREATER_GHOST));
			}
		}
	}

	@Test
	public void theStickTileIsReadableStatically()
	{
		assertNull("no stick on the ground", AgilityPlugin.getStickTile());
	}
}
