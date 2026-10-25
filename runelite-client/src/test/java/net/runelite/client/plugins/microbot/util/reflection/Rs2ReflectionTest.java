package net.runelite.client.plugins.microbot.util.reflection;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import fakegamepack.FakeItems;
import org.junit.Before;
import org.junit.Test;

/** Upstream's ground-item actions read, on fake gamepack layouts: a string list, a bean list, none. */
public class Rs2ReflectionTest
{
	@Before
	public void resetCache()
	{
		Rs2Reflection.resetGroundItemActionCache();
	}

	@Test
	public void readsAStringList()
	{
		assertArrayEquals(new String[]{null, null, "Take", "Light", null},
			Rs2Reflection.getGroundItemActionsFromObject(new FakeItems.StringListItem(null, null, "Take", "Light", null)));
	}

	@Test
	public void readsABeanListAndCachesTheLayout()
	{
		assertArrayEquals(new String[]{null, null, "Take", null, "Examine"},
			Rs2Reflection.getGroundItemActionsFromObject(new FakeItems.BeanListItem(null, null, "Take", null, "Examine")));
		// Second read of the same class goes through the cached fields.
		assertArrayEquals(new String[]{"Lay", null, "Take"},
			Rs2Reflection.getGroundItemActionsFromObject(new FakeItems.BeanListItem("Lay", null, "Take")));
	}

	@Test
	public void unknownLayoutOrBlankActionsFallBackToTake()
	{
		String[] fallback = {null, null, "Take", null, null};
		assertArrayEquals(fallback, Rs2Reflection.getGroundItemActionsFromObject(new FakeItems.NoActionsItem()));
		assertArrayEquals(fallback, Rs2Reflection.getGroundItemActionsFromObject(new FakeItems.StringListItem(null, " ", null)));
		assertEquals(0, Rs2Reflection.getGroundItemActionsFromObject(null).length);
	}
}
