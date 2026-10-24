package net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns;

import java.time.Instant;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.timetracking.Tab;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Read batch 16h: Quest Helper 2.6.38's farm-run model (patch table, state mapping and growth prediction). */
public class HubReadBatchSixteenHTest
{
	private static final String PROFILE = "profile";

	@Test
	public void worldGroupsPatchesByTab()
	{
		FarmingWorld world = new FarmingWorld();
		Set<FarmingPatch> herbs = world.getTabs().get(Tab.HERB);
		assertNotNull(herbs);
		assertFalse(herbs.isEmpty());
		assertTrue(herbs.stream().allMatch(patch -> patch.getImplementation() == PatchImplementation.HERB));
		assertTrue("the Catherby herb patch", herbs.stream().anyMatch(patch -> patch.getLocation() != null && patch.getLocation().getX() == 2815));
	}

	@Test
	public void herbVarbitValuesMapToStates()
	{
		PatchState weeds = PatchImplementation.HERB.forVarbitValue(2);
		assertEquals(CropState.GROWING, weeds.getCropState());
		PatchState guam = PatchImplementation.HERB.forVarbitValue(9);
		assertEquals(CropState.HARVESTABLE, guam.getCropState());
		assertEquals(1, guam.getStage());
	}

	@Test
	public void predictionReadsTheStoredTimeTrackingValue()
	{
		Client client = mock(Client.class);
		ConfigManager config = mock(ConfigManager.class);
		FarmingHandler handler = new FarmingHandler(client, config);
		FarmingPatch patch = new FarmingWorld().getTabs().get(Tab.HERB).iterator().next();
		assertNull("nothing stored", handler.predictPatch(patch, PROFILE));

		when(config.getConfiguration(eq("timetracking"), eq(PROFILE), anyString())).thenReturn("2:" + Instant.now().getEpochSecond());
		assertEquals("weeds count as empty", CropState.EMPTY, handler.predictPatch(patch, PROFILE));
		when(config.getConfiguration(eq("timetracking"), eq(PROFILE), anyString())).thenReturn("9:" + Instant.now().getEpochSecond());
		assertEquals(CropState.HARVESTABLE, handler.predictPatch(patch, PROFILE));
		when(config.getConfiguration(eq("timetracking"), eq(PROFILE), anyString())).thenReturn("4:" + Instant.now().getEpochSecond());
		assertEquals("guam just planted", CropState.GROWING, handler.predictPatch(patch, PROFILE));
		when(config.getConfiguration(eq("timetracking"), eq(PROFILE), anyString())).thenReturn("4:1");
		assertEquals("planted long ago, not yet seen grown", CropState.UNCHECKED, handler.predictPatch(patch, PROFILE));
	}

	@Test
	public void tickTimeRoundsToTheFarmingTick()
	{
		FarmingHandler handler = new FarmingHandler(mock(Client.class), mock(ConfigManager.class));
		// 20-minute ticks: 60030 s falls in the tick starting at 60000 s; two ticks later is 62400 s.
		assertEquals(62_400L, handler.getTickTime(20, 2, 60_030L, PROFILE));
	}
}
