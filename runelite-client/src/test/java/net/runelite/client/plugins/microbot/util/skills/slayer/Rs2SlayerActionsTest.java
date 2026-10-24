package net.runelite.client.plugins.microbot.util.skills.slayer;

import net.runelite.client.plugins.microbot.util.skills.slayer.enums.SlayerMaster;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Travel preserves the existing walker's observed-arrival/failure result and destination policy. */
public class Rs2SlayerActionsTest
{
	@Test
	public void nullMasterDoesNotStartAWalk()
	{
		try (MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class))
		{
			assertFalse(Rs2Slayer.walkToSlayerMaster(null));
			walker.verifyNoInteractions();
		}
	}

	@Test
	public void masterDestinationIsUnchangedAndArrivalMustBeReported()
	{
		try (MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class))
		{
			walker.when(() -> Rs2Walker.walkTo(SlayerMaster.TURAEL.getWorldPoint())).thenReturn(true);
			assertTrue(Rs2Slayer.walkToSlayerMaster(SlayerMaster.TURAEL));
			walker.verify(() -> Rs2Walker.walkTo(SlayerMaster.TURAEL.getWorldPoint()), times(1));
			walker.verifyNoMoreInteractions();
		}
	}

	@Test
	public void cancelledOrUnreachableWalkRemainsFalse()
	{
		try (MockedStatic<Rs2Walker> walker = mockStatic(Rs2Walker.class))
		{
			walker.when(() -> Rs2Walker.walkTo(SlayerMaster.KRYSTILIA.getWorldPoint())).thenReturn(false);
			assertFalse(Rs2Slayer.walkToSlayerMaster(SlayerMaster.KRYSTILIA));
			walker.verify(() -> Rs2Walker.walkTo(SlayerMaster.KRYSTILIA.getWorldPoint()), times(1));
		}
	}
}
