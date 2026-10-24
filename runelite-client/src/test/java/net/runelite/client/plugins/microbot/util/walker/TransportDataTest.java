package net.runelite.client.plugins.microbot.util.walker;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.shortestpath.MobileRouteTestAccess;
import net.runelite.client.plugins.microbot.shortestpath.PathResult;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Webwalker C2-4: what the catalogue gives the executors still to be written (quetzals, magic
 * mushtrees, canoes), as {@link PathResult.TransportStep}s. No game client.
 */
public class TransportDataTest
{
	private static List<PathResult.TransportStep> steps(TransportType type)
	{
		return MobileRouteTestAccess.catalogue().values().stream()
			.flatMap(Set::stream)
			.filter(t -> t.getType() == type)
			.map(MobileRouteTestAccess::step)
			.collect(Collectors.toList());
	}

	private static List<Transport> transports(TransportType type)
	{
		return MobileRouteTestAccess.catalogue().values().stream()
			.flatMap(Set::stream)
			.filter(t -> t.getType() == type)
			.collect(Collectors.toList());
	}

	@Test
	public void quetzalsAreRenusTravelOptionWithADestinationName()
	{
		List<PathResult.TransportStep> quetzals = steps(TransportType.QUETZAL);
		assertFalse(quetzals.isEmpty());
		for (PathResult.TransportStep step : quetzals)
		{
			assertEquals(step.toString(), "Travel", step.getAction());
			assertEquals("Renu", step.getName());
			assertTrue(step.getObjectId() > 0);
			assertNotNull(step.getOrigin());
			assertNotNull(step.getDestination());
			assertFalse("the destination name picks the map button", step.getDisplayInfo() == null || step.getDisplayInfo().isBlank());
		}
		// Unlocked landing sites are varplayer bits (4182); the planner checks them, the executor need not.
		assertTrue(transports(TransportType.QUETZAL).stream().anyMatch(t -> !t.getVarplayers().isEmpty()));
	}

	@Test
	public void mushtreesNameTheirDestinationWithANumberedMenuEntry()
	{
		List<PathResult.TransportStep> trees = steps(TransportType.MAGIC_MUSHTREE);
		assertFalse(trees.isEmpty());
		for (PathResult.TransportStep step : trees)
		{
			assertEquals("Use", step.getAction());
			assertEquals("Magic Mushtree", step.getName());
			assertTrue(step.getObjectId() > 0);
			assertTrue(step.getDisplayInfo(), step.getDisplayInfo().matches("\\d\\. .+"));
		}
		// "4. Mushroom Meadow": a dot, not the "4: ..." colon that menuShortcut and menuDestinationName
		// parse for spirit trees, so a mushtree executor needs its own split (number = key, rest = name).
		assertEquals(0, Rs2Walker.menuShortcut("4. Mushroom Meadow"));
		assertEquals("the dot prefix is stripped for matching the menu text (Claude 1, mushtree executor)",
			"Mushroom Meadow", Rs2Walker.menuDestinationName("4. Mushroom Meadow"));
	}

	@Test
	public void canoesCarryStationDestinationLevelAndAxe()
	{
		List<PathResult.TransportStep> canoes = steps(TransportType.CANOE);
		assertFalse(canoes.isEmpty());
		Set<Integer> canoeLevels = Set.of(12, 27, 42, 57);
		for (PathResult.TransportStep step : canoes)
		{
			assertEquals("Paddle Canoe", step.getAction());
			assertEquals("Canoe Station", step.getName());
			assertTrue(step.getObjectId() > 0);
			assertFalse(step.getDisplayInfo() == null || step.getDisplayInfo().isBlank());
			assertTrue("the trip's level names the canoe: " + step,
				canoeLevels.contains(step.getSkillLevel(Skill.WOODCUTTING)));
			assertTrue("any axe", step.getItemIds().contains(ItemID.BRONZE_AXE));
		}
		assertTrue(canoes.stream().anyMatch(step -> step.getSkillLevel(Skill.WOODCUTTING) == 57));
	}

	@Test
	public void skillLevelsAreCopiedPerCall()
	{
		PathResult.TransportStep canoe = steps(TransportType.CANOE).get(0);
		int level = canoe.getSkillLevel(Skill.WOODCUTTING);
		canoe.getSkillLevels()[Skill.WOODCUTTING.ordinal()] = 99;
		assertEquals(level, canoe.getSkillLevel(Skill.WOODCUTTING));
		assertEquals(0, canoe.getSkillLevel(Skill.ATTACK));
	}
}
