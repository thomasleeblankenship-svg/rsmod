package net.runelite.client.plugins.microbot.util.player;

import net.runelite.client.plugins.microbot.util.shop.Actions;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Read batch 16o: upstream 2.6.38's shop option names and random name helper. */
public class HubReadBatchSixteenOTest
{
	@Test
	public void namesAlternateConsonantsAndVowels()
	{
		for (int i = 0; i < 50; i++)
		{
			String name = new NameGenerator(6).getName();
			assertEquals(6, name.length());
			assertTrue(Character.isUpperCase(name.charAt(0)));
			for (int c = 0; c < name.length(); c++)
			{
				boolean vowel = "aeiou".indexOf(Character.toLowerCase(name.charAt(c))) >= 0;
				assertEquals("position " + c + " of " + name, c % 2 == 1, vowel);
			}
		}
		assertEquals("out of range falls back to 7", 7, new NameGenerator(12).getName().length());
	}

	@Test
	public void shopOptionTexts()
	{
		assertEquals("Value", Actions.VALUE.getAction());
		assertEquals("Sell 50", Actions.SELL_FIFTY.getAction());
		assertEquals(5, Actions.values().length);
	}
}
