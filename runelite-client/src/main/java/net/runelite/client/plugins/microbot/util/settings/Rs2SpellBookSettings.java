package net.runelite.client.plugins.microbot.util.settings;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/**
 * As upstream: turns spellbook filters on or off from the spellbook's "Filters" list (interface
 * 218). Each filter's state is its varbit (0 shown, 1 hidden); a toggle already in the wanted state
 * clicks nothing. Display settings only: no confirmation or safety prompt is affected.
 */
@Slf4j
public final class Rs2SpellBookSettings
{
	private static final int SPELLBOOK = 218;
	private static final int FILTER_TIMEOUT_MILLIS = 2000;

	private Rs2SpellBookSettings()
	{
	}

	/**
	 * As upstream: sets the filter labelled {@code text} (varbit {@code varbitId}) to shown when
	 * {@code toggle}, else hidden; turns spell filtering on first and opens the filter list when
	 * needed. True once the varbit shows the wanted state.
	 */
	public static boolean toggleSpellFilter(String text, boolean toggle, int varbitId, boolean closeInterface)
	{
		int expectedValue = toggle ? 0 : 1;
		if (Microbot.getVarbitValue(varbitId) == expectedValue)
		{
			return true;
		}
		if (!Rs2Settings.isSpellFilteringEnabled())
		{
			Rs2Settings.enableSpellFiltering();
		}
		Rs2Tab.switchTo(InterfaceTab.MAGIC);
		if (!Global.sleepUntilTrue(() -> Rs2Tab.getCurrentTab() == InterfaceTab.MAGIC, 300, FILTER_TIMEOUT_MILLIS))
		{
			return false;
		}
		log.info("{} following spell filter: {}", toggle ? "Turning on" : "Turning off", text);
		if (!Rs2Widget.hasWidgetText(text, SPELLBOOK, 0, true))
		{
			Rs2Widget.clickWidget("Filters", Optional.of(SPELLBOOK), 0, true);
			if (!Rs2Widget.sleepUntilHasWidgetText(text, SPELLBOOK, 0, true, FILTER_TIMEOUT_MILLIS))
			{
				log.error("did not find widget with text: {}", text);
				return false;
			}
		}
		Rs2Widget.clickWidget(text, Optional.of(SPELLBOOK), 0, true);
		Global.sleepUntil(() -> Microbot.getVarbitValue(varbitId) == expectedValue, FILTER_TIMEOUT_MILLIS);
		if (closeInterface)
		{
			closeSpellBookFilter();
		}
		return Microbot.getVarbitValue(varbitId) == expectedValue;
	}

	/** As upstream: {@link #toggleSpellFilter(String, boolean, int, boolean)}, closing the filter list. */
	public static boolean toggleSpellFilter(String text, boolean toggle, int varbitId)
	{
		return toggleSpellFilter(text, toggle, varbitId, true);
	}

	private static void closeSpellBookFilter()
	{
		if (Rs2Widget.hasWidgetText("Spell Filters", SPELLBOOK, 0, true))
		{
			Rs2Widget.clickWidget("Filters", Optional.of(SPELLBOOK), 0, true);
			Rs2Widget.sleepUntilHasNotWidgetText("Spell Filters", SPELLBOOK, 0, true, FILTER_TIMEOUT_MILLIS);
		}
	}

	/** As upstream: shows or hides combat spells; closes the filter list when {@code closeInterface}. */
	public static boolean toggleCombatSpells(boolean toggle, boolean closeInterface)
	{
		return toggleSpellFilter("Show Combat spells", toggle, 6605, closeInterface);
	}

	/** As upstream: {@link #toggleCombatSpells(boolean, boolean)}, closing the filter list. */
	public static boolean toggleCombatSpells(boolean toggle)
	{
		return toggleSpellFilter("Show Combat spells", toggle, 6605);
	}

	/** As upstream: shows or hides teleport spells; closes the filter list when {@code closeInterface}. */
	public static boolean toggleTeleportSpells(boolean toggle, boolean closeInterface)
	{
		return toggleSpellFilter("Show Teleport spells", toggle, 6609, closeInterface);
	}

	/** As upstream: {@link #toggleTeleportSpells(boolean, boolean)}, closing the filter list. */
	public static boolean toggleTeleportSpells(boolean toggle)
	{
		return toggleSpellFilter("Show Teleport spells", toggle, 6609);
	}

	/** As upstream: shows or hides utility spells; closes the filter list when {@code closeInterface}. */
	public static boolean toggleUtilitySpells(boolean toggle, boolean closeInterface)
	{
		return toggleSpellFilter("Show Utility spells", toggle, 6606, closeInterface);
	}

	/** As upstream: {@link #toggleUtilitySpells(boolean, boolean)}, closing the filter list. */
	public static boolean toggleUtilitySpells(boolean toggle)
	{
		return toggleSpellFilter("Show Utility spells", toggle, 6606);
	}

	/** As upstream: shows or hides spells you lack the Magic level for; closes the filter list when {@code closeInterface}. */
	public static boolean toggleLackMagicLevel(boolean toggle, boolean closeInterface)
	{
		return toggleSpellFilter("Show spells you lack the Magic level to cast", toggle, 6607, closeInterface);
	}

	/** As upstream: {@link #toggleLackMagicLevel(boolean, boolean)}, closing the filter list. */
	public static boolean toggleLackMagicLevel(boolean toggle)
	{
		return toggleSpellFilter("Show spells you lack the Magic level to cast", toggle, 6607);
	}

	/** As upstream: shows or hides spells you lack the runes for; closes the filter list when {@code closeInterface}. */
	public static boolean toggleLackRunes(boolean toggle, boolean closeInterface)
	{
		return toggleSpellFilter("Show spells you lack the runes to cast", toggle, 6608, closeInterface);
	}

	/** As upstream: {@link #toggleLackRunes(boolean, boolean)}, closing the filter list. */
	public static boolean toggleLackRunes(boolean toggle)
	{
		return toggleSpellFilter("Show spells you lack the runes to cast", toggle, 6608);
	}

	/** As upstream: shows or hides spells you lack the requirements for; closes the filter list when {@code closeInterface}. */
	public static boolean toggleLackRequirements(boolean toggle, boolean closeInterface)
	{
		return toggleSpellFilter("Show spells you lack the requirements to cast", toggle, 12137, closeInterface);
	}

	/** As upstream: {@link #toggleLackRequirements(boolean, boolean)}, closing the filter list. */
	public static boolean toggleLackRequirements(boolean toggle)
	{
		return toggleSpellFilter("Show spells you lack the requirements to cast", toggle, 12137);
	}

	/** As upstream: shows or hides icon resizing outside PvP areas; closes the filter list when {@code closeInterface}. */
	public static boolean toggleIconResizing(boolean toggle, boolean closeInterface)
	{
		return toggleSpellFilter("Enable icon resizing (outside PvP areas)", toggle, 6548, closeInterface);
	}

	/** As upstream: {@link #toggleIconResizing(boolean, boolean)}, closing the filter list. */
	public static boolean toggleIconResizing(boolean toggle)
	{
		return toggleSpellFilter("Enable icon resizing (outside PvP areas)", toggle, 6548);
	}

	/** As upstream: every filter shown, icon resizing on; closes the list once all succeed. */
	public static boolean setAllFiltersOn()
	{
		return finish(toggleCombatSpells(true, false) && toggleTeleportSpells(true, false) && toggleUtilitySpells(true, false)
			&& toggleLackMagicLevel(true, false) && toggleLackRunes(true, false) && toggleLackRequirements(true, false)
			&& toggleIconResizing(true, false));
	}

	/** As upstream: every filter shown, icon resizing off (the layout scripts expect). */
	public static boolean configureSpellbookSettings()
	{
		return finish(toggleCombatSpells(true, false) && toggleTeleportSpells(true, false) && toggleUtilitySpells(true, false)
			&& toggleLackMagicLevel(true, false) && toggleLackRunes(true, false) && toggleLackRequirements(true, false)
			&& toggleIconResizing(false, false));
	}

	/** As upstream: every filter hidden, icon resizing off. */
	public static boolean setAllFiltersOff()
	{
		return finish(toggleCombatSpells(false, false) && toggleTeleportSpells(false, false) && toggleUtilitySpells(false, false)
			&& toggleLackMagicLevel(false, false) && toggleLackRunes(false, false) && toggleLackRequirements(false, false)
			&& toggleIconResizing(false, false));
	}

	private static boolean finish(boolean success)
	{
		if (success)
		{
			closeSpellBookFilter();
		}
		return success;
	}
}
