package net.runelite.client.plugins.microbot.util.tabs;

import net.runelite.api.gameval.VarClientID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;

/**
 * Side-panel tabs.
 * <p>
 * Adapted from chsami/Microbot {@code util/tabs/Rs2Tab.java} at
 * {@code a2114401bbbc35e9762db9e8aa666d70ffcd24d8} (BSD 2-Clause). Mobile: the current tab is
 * read from the top-level panel varc on the client thread (upstream caches it from
 * {@code VarClientIntChanged}); switching runs upstream's client script 915 as one script action
 * through {@link MobileActions}. The hotkey fallback for tabs without a panel index is not ported.
 */
public final class Rs2Tab
{
	private Rs2Tab() { }

	public static InterfaceTab getCurrentTab()
	{
		int panel = MobileActions.read(() -> Microbot.getClient().getVarcIntValue(VarClientID.TOPLEVEL_PANEL), -1);
		for (InterfaceTab tab : InterfaceTab.values())
		{
			if (tab.getVarcIntIndex() >= 0 && tab.getVarcIntIndex() == panel) { return tab; }
		}
		return InterfaceTab.NOTHING_SELECTED;
	}

	public static boolean isCurrentTab(InterfaceTab tab)
	{
		return getCurrentTab() == tab;
	}

	/** As upstream: true once {@code tab} is open (waits up to 2 seconds). */
	public static boolean switchTo(InterfaceTab tab)
	{
		if (tab == null) { return false; }
		if (isCurrentTab(tab)) { return true; }
		int index = tab.getVarcIntIndex();
		return index >= 0 && Global.sleepUntil(() -> MobileActions.openTab(index), 2_000)
			&& Global.sleepUntil(() -> isCurrentTab(tab), 2_000);
	}

	public static boolean switchToInventoryTab() { return switchTo(InterfaceTab.INVENTORY); }
	public static boolean switchToCombatOptionsTab() { return switchTo(InterfaceTab.COMBAT); }
	public static boolean switchToSkillsTab() { return switchTo(InterfaceTab.SKILLS); }
	public static boolean switchToEquipmentTab() { return switchTo(InterfaceTab.EQUIPMENT); }
	public static boolean switchToPrayerTab() { return switchTo(InterfaceTab.PRAYER); }
	public static boolean switchToMagicTab() { return switchTo(InterfaceTab.MAGIC); }
	public static boolean switchToSettingsTab() { return switchTo(InterfaceTab.SETTINGS); }
	public static boolean switchToLogout() { return switchTo(InterfaceTab.LOGOUT); }
}
