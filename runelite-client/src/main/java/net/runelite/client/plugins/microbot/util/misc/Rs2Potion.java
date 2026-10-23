package net.runelite.client.plugins.microbot.util.misc;

import java.util.List;

/**
 * Potion name stems for name-based inventory and bank checks. Names omit the dose suffix
 * ({@code "(4)"}), so they match every dose with a partial-name check. Written for RuneMobile.
 */
public final class Rs2Potion
{
	private static final List<String> RESTORE_ENERGY = List.of("Energy potion", "Super energy");

	private Rs2Potion()
	{
	}

	/** Name stem of the stamina potion. */
	public static String getStaminaPotion()
	{
		return "Stamina potion";
	}

	/** Name stems of the potions that restore run energy (stamina potions not included). */
	public static List<String> getRestoreEnergyPotionsVariants()
	{
		return RESTORE_ENERGY;
	}
}
