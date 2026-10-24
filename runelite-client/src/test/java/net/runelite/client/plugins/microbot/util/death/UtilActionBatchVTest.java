package net.runelite.client.plugins.microbot.util.death;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Util action batch V: death state, the grave and Death's Office. */
public class UtilActionBatchVTest
{
	@After
	public void clear()
	{
		Rs2Death.clearDeathState();
	}

	@Test
	public void anExpiredGraveNeedsOneSeenFirst()
	{
		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class, CALLS_REAL_METHODS))
		{
			microbot.when(() -> Microbot.getVarbitValue(VarbitID.GRAVESTONE_VISIBLE)).thenReturn(0);
			assertFalse("no grave was ever seen (a PvP death leaves none)", Rs2Death.hasGraveExpired());
			assertFalse(Rs2Death.hasDeathToHandle());

			VarbitChanged seen = new VarbitChanged();
			seen.setVarbitId(VarbitID.GRAVESTONE_VISIBLE);
			seen.setValue(133);
			Rs2Death.onVarbitChanged(seen);
			assertTrue(Rs2Death.hasGraveExpired());
			assertTrue(Rs2Death.hasDeathToHandle());

			microbot.when(() -> Microbot.getVarbitValue(VarbitID.GRAVESTONE_VISIBLE)).thenReturn(133);
			assertTrue(Rs2Death.hasGrave());
			assertFalse(Rs2Death.hasGraveExpired());
		}
	}

	@Test
	public void declinedOfficeRecoveryClearsTheDeathWithoutTravelling()
	{
		try (MockedStatic<Rs2Death> death = mockStatic(Rs2Death.class, CALLS_REAL_METHODS))
		{
			death.when(Rs2Death::hasGrave).thenReturn(false);
			death.when(Rs2Death::hasGraveExpired).thenReturn(true);
			assertTrue(Rs2Death.recoverItems(0));
			death.verify(Rs2Death::walkToDeathsOffice, never());
			death.verify(Rs2Death::clearDeathState);
		}
		assertTrue("nothing to handle", Rs2Death.recoverItems());
	}

	@Test
	public void aGraveFeeOverBudgetIsLeft()
	{
		try (MockedStatic<Rs2Death> death = mockStatic(Rs2Death.class, CALLS_REAL_METHODS))
		{
			death.when(Rs2Death::isGraveOpen).thenReturn(true);
			death.when(Rs2Death::getGraveFee).thenReturn(50_000);
			assertFalse(Rs2Death.lootGravePaidItems(10_000));
		}
	}

	@Test
	public void theWildernessOfficeIsNeverTheNearest()
	{
		assertTrue(DeathsOfficeLocation.FEROX_ENCLAVE.isInWilderness());
		assertNotEquals(DeathsOfficeLocation.FEROX_ENCLAVE, DeathsOfficeLocation.getNearest(new WorldPoint(3127, 3631, 0)));
		assertNull(DeathsOfficeLocation.getNearest((WorldPoint) null));
	}
}
