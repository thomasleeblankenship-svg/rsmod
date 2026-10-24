package net.runelite.client.plugins.microbot.util.skills.fletching;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.After;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util action batch AK: fletching while moving and auto-fletching. */
public class UtilActionBatchAKTest
{
	@After
	public void stop()
	{
		Rs2Fletching.stopFletchingWhileMoving();
	}

	@SuppressWarnings("unchecked")
	private static ScheduledExecutorService capturing(ScheduledFuture<?> future)
	{
		ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
		doReturn(future).when(executor).scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
		return executor;
	}

	@Test
	public void theTaskStopsOnceThePlayerStandsStill()
	{
		ScheduledFuture<?> future = mock(ScheduledFuture.class);
		ScheduledExecutorService executor = capturing(future);
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
			MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class))
		{
			inventory.when(() -> Rs2Inventory.hasItem(anyString())).thenReturn(true);
			player.when(Rs2Player::isMoving).thenReturn(false);
			Rs2Fletching.startArrowFletchingWhileMoving(executor);
			Rs2Fletching.startArrowFletchingWhileMoving(executor);
			ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
			verify(executor, times(1)).scheduleWithFixedDelay(task.capture(), eq(0L), eq(1800L), eq(TimeUnit.MILLISECONDS));
			task.getValue().run();
			verify(future).cancel(false);
			inventory.verify(() -> Rs2Inventory.combine(anyString(), anyString()), never());
		}
	}

	@Test
	public void missingMaterialsStartNothing()
	{
		ScheduledExecutorService executor = capturing(mock(ScheduledFuture.class));
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class))
		{
			inventory.when(() -> Rs2Inventory.hasItem(anyString())).thenReturn(false);
			Rs2Fletching.startDartFletchingWhileMoving("Rune dart tip", executor);
			verifyNoInteractions(executor);
		}
	}

	@Test
	public void autoFletchingFindsNothingInAnEmptyInventory()
	{
		try (MockedStatic<Rs2Inventory> inventory = mockStatic(Rs2Inventory.class);
			MockedStatic<Rs2Player> player = mockStatic(Rs2Player.class))
		{
			inventory.when(() -> Rs2Inventory.hasItem(anyString())).thenReturn(false);
			inventory.when(() -> Rs2Inventory.hasItem(anyInt())).thenReturn(false);
			player.when(() -> Rs2Player.getRealSkillLevel(any())).thenReturn(99);
			assertFalse(Rs2Fletching.autoFletchAvailable("All"));
		}
	}
}
