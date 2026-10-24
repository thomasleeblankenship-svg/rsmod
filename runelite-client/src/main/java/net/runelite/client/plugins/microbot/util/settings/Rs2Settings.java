package net.runelite.client.plugins.microbot.util.settings;

import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/**
 * Game settings: varbit reads, and the upstream toggles that open the settings interface and
 * flip one option (shift-click drop, hide roofs, level-up messages, sound, spell filtering, bank
 * slot locks). Ported from upstream Microbot 2.6.38 for RuneMobile: reads run on the client
 * thread, every click is one {@link MobileActions#widget} dispatch (upstream's
 * {@code Microbot.doInvoke(NewMenuEntry)} calls map to the same CC_OP), keys go through
 * {@link Rs2Keyboard}, and waits use {@link Global#sleepUntil}.
 *
 * <p>Safety (agreed with Claude 1): nothing here turns off a Wilderness, PvP or confirmation
 * warning. {@link #disableWorldSwitcherConfirmation} therefore never clicks; it only reports
 * whether the confirmation is already off.
 */
@Slf4j
public final class Rs2Settings
{
	static final int SETTINGS_INTERFACE = InterfaceID.Settings.UNIVERSE;
	static final int SETTINGS_CLICKABLE = 8781844;
	static final int SETTINGS_CATEGORIES = 8781848;
	static final int ALL_SETTINGS_BUTTON = 7602208;
	/** Child of {@link #SETTINGS_CLICKABLE} holding "Shift click to drop" on the Controls tab (upstream param0). */
	static final int SHIFT_DROP_CHILD = 8;
	/** Upstream's varbit for the alchemy value warning threshold. */
	static final int ALCHEMY_WARNING_VARBIT = 6091;
	/** Upstream's settings-side sound tab button (group 116, child 67). */
	static final int SOUND_TAB_BUTTON = (116 << 16) | 67;
	/** Upstream's three sound widgets, in upstream's order and with upstream's ids. */
	static final int[] SOUND_ICONS = {
		InterfaceID.SettingsSide.MASTER_ICON, InterfaceID.SettingsSide.MUSIC_ICON, InterfaceID.SettingsSide.SOUND_ICON,
	};

	private static final Map<String, Integer> SETTINGS_TABS = Map.of(
		"Activities", 0,
		"Audio", 1,
		"Chat", 2,
		"Controls", 3,
		"Display", 4,
		"Gameplay", 5,
		"Warnings", 7);

	private Rs2Settings()
	{
	}

	/** True once the full settings interface is open (opens it from the settings tab if needed). */
	public static boolean openSettings()
	{
		if (Rs2Widget.isWidgetVisible(SETTINGS_INTERFACE))
		{
			return true;
		}
		Rs2Tab.switchTo(InterfaceTab.SETTINGS);
		if (!Global.sleepUntil(() -> Rs2Widget.isWidgetVisible(ALL_SETTINGS_BUTTON), 3000))
		{
			return false;
		}
		Global.sleepGaussian(400, 100);
		Rs2Widget.clickWidget("All Settings", true);
		return Global.sleepUntil(() -> Rs2Widget.isWidgetVisible(SETTINGS_INTERFACE), 5000);
	}

	public static boolean isDropShiftSettingEnabled()
	{
		return Microbot.getVarbitValue(VarbitID.DESKTOP_SHIFTCLICKDROP_ENABLED) == 1;
	}

	public static boolean isEscCloseInterfaceSettingEnabled()
	{
		return Microbot.getVarbitValue(VarbitID.KEYBINDING_ESC_TO_CLOSE) == 1;
	}

	public static boolean isWorldSwitcherConfirmationEnabled()
	{
		return Microbot.getVarbitValue(VarbitID.WORLDSWITCHER_DISABLE_CONFIRMATION) == 0;
	}

	public static boolean isHideRoofsEnabled()
	{
		return Microbot.getVarbitValue(VarbitID.OPTION_HIDE_ROOFTOPS) == 1;
	}

	public static boolean isLevelUpNotificationsEnabled()
	{
		return Microbot.getVarbitValue(VarbitID.OPTION_LEVEL_UP_MESSAGE_DISABLED) == 0;
	}

	/** The item value above which the game warns before an alchemy cast (upstream varbit 6091). */
	public static int getMinimumItemValueAlchemyWarning()
	{
		return Microbot.getVarbitValue(ALCHEMY_WARNING_VARBIT);
	}

	/** Whether spell filtering is enabled in the magic spellbook. */
	public static boolean isSpellFilteringEnabled()
	{
		return Microbot.getVarbitValue(VarbitID.MAGIC_SPELLBOOK_HIDEFILTERBUTTON) == 0;
	}

	/** Whether bank slot locking is on (the lock menu option shown and inventory locks honoured). */
	public static boolean isBankSlotLockingEnabled()
	{
		return Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_SHOWOP) == 1
			&& Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_IGNOREINVLOCKS) == 0;
	}

	/** Turns on shift-click drop from the Controls tab; closes the interface when asked. */
	public static boolean enableDropShiftSetting(boolean closeInterface)
	{
		if (isDropShiftSettingEnabled())
		{
			return true;
		}
		if (!openSettings() || !switchToSettingsTab("Controls"))
		{
			return false;
		}
		Global.sleepGaussian(800, 100);
		if (!MobileActions.widget(SETTINGS_CLICKABLE, SHIFT_DROP_CHILD, "Toggle"))
		{
			return false;
		}
		boolean success = Global.sleepUntil(Rs2Settings::isDropShiftSettingEnabled);
		finish(closeInterface);
		return success;
	}

	public static boolean enableDropShiftSetting()
	{
		return enableDropShiftSetting(true);
	}

	/** Turns on "Hide roofs" through the settings search. */
	public static boolean hideRoofs(boolean closeInterface)
	{
		if (isHideRoofsEnabled())
		{
			return true;
		}
		return toggleBySearch("roof", Rs2Settings::isHideRoofsEnabled, closeInterface);
	}

	public static boolean hideRoofs()
	{
		return hideRoofs(true);
	}

	public static boolean disableLevelUpNotifications(boolean closeInterface)
	{
		if (!isLevelUpNotificationsEnabled())
		{
			return true;
		}
		return setLevelUpNotificationOption("Disabled", () -> !isLevelUpNotificationsEnabled(), closeInterface);
	}

	public static boolean disableLevelUpNotifications()
	{
		return disableLevelUpNotifications(true);
	}

	public static boolean enableLevelUpNotifications(boolean closeInterface)
	{
		if (isLevelUpNotificationsEnabled())
		{
			return true;
		}
		return setLevelUpNotificationOption("Show level only", Rs2Settings::isLevelUpNotificationsEnabled, closeInterface);
	}

	public static boolean enableLevelUpNotifications()
	{
		return enableLevelUpNotifications(true);
	}

	/**
	 * Upstream turns the world switcher confirmation off. That is a confirmation safety prompt,
	 * so on RuneMobile this never clicks: it returns {@code true} only if the confirmation is
	 * already off, and {@code false} (logged once per call) otherwise.
	 */
	public static boolean disableWorldSwitcherConfirmation(boolean closeInterface)
	{
		if (!isWorldSwitcherConfirmationEnabled())
		{
			return true;
		}
		log.info("Rs2Settings: not turning off the world switcher confirmation (safety prompts stay on)");
		return false;
	}

	public static boolean disableWorldSwitcherConfirmation()
	{
		return disableWorldSwitcherConfirmation(true);
	}

	/** Mutes the three sound sliders that are on, as upstream (opens the sound tab first). */
	public static void turnOffMusic()
	{
		Rs2Tab.switchTo(InterfaceTab.SETTINGS);
		Rs2Widget.clickWidget(SOUND_TAB_BUTTON);
		Global.sleepGaussian(800, 100);
		for (int icon : SOUND_ICONS)
		{
			int button = MobileActions.read(() -> unmutedButton(Microbot.getClient(), icon), -1);
			if (button != -1)
			{
				MobileActions.widget(button, -1, "Mute");
				Global.sleepGaussian(600, 150);
			}
		}
	}

	/** Turns on spell filtering from the spellbook tab stone, as upstream. */
	public static void enableSpellFiltering()
	{
		if (isSpellFilteringEnabled())
		{
			return;
		}
		int stone = MobileActions.read(() -> spellbookStone(Microbot.getClient()), -1);
		if (stone == -1)
		{
			log.info("Spellbook interface widget not found, cannot toggle spell filter.");
			return;
		}
		if (MobileActions.widget(stone, -1, "Enable spell filtering"))
		{
			Global.sleepUntil(Rs2Settings::isSpellFilteringEnabled, 2000);
		}
	}

	/** Turns on bank slot locking from the open bank's menu; true once it is on. */
	public static boolean enableBankSlotLocking()
	{
		if (isBankSlotLockingEnabled())
		{
			return true;
		}
		Rs2Widget.clickWidget(InterfaceID.Bankmain.MENU_BUTTON);
		if (!Global.sleepUntil(() -> Rs2Widget.isWidgetVisible(InterfaceID.Bankmain.MENU_CONTAINER), 2000))
		{
			log.debug("Bank menu did not open within timeout.");
			return false;
		}
		Rs2Widget.clickWidget(InterfaceID.Bankmain.LOCKS);
		if (!Global.sleepUntil(() -> Rs2Widget.isWidgetVisible(InterfaceID.BankSideLocks.DONE), 2000))
		{
			log.debug("Bank Locks panel did not appear.");
			return false;
		}
		if (Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_IGNOREINVLOCKS) != 0)
		{
			Rs2Widget.clickWidget(InterfaceID.BankSideLocks.IGNORELOCKS);
			if (!Global.sleepUntil(() -> Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_IGNOREINVLOCKS) == 0, 2000))
			{
				log.debug("Failed to disable 'ignore inventory locks' setting.");
				return false;
			}
		}
		if (Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_SHOWOP) != 1)
		{
			Rs2Widget.clickWidget(InterfaceID.BankSideLocks.EXTRAOPTIONS);
			if (!Global.sleepUntil(() -> Microbot.getVarbitValue(VarbitID.BANK_SIDE_SLOT_SHOWOP) == 1, 2000))
			{
				log.debug("Failed to enable 'show inventory locks menu option' setting.");
				return false;
			}
		}
		Rs2Widget.clickWidget(InterfaceID.BankSideLocks.DONE);
		if (!Global.sleepUntil(() -> !Rs2Widget.isWidgetVisible(InterfaceID.BankSideLocks.DONE), 2000))
		{
			log.debug("Locks panel did not close after clicking DONE.");
			return false;
		}
		if (Rs2Widget.isWidgetVisible(InterfaceID.Bankmain.MENU_CONTAINER))
		{
			Rs2Widget.clickWidget(InterfaceID.Bankmain.MENU_BUTTON);
		}
		return isBankSlotLockingEnabled();
	}

	private static boolean toggleBySearch(String term, BooleanSupplier done, boolean closeInterface)
	{
		int[] toggle = openSettingsSearch(term, "Toggle");
		if (toggle == null)
		{
			closeSettingsMenu();
			return false;
		}
		MobileActions.widget(toggle[0], toggle[1], "Toggle");
		boolean success = Global.sleepUntil(done, 3000);
		finish(closeInterface);
		return success;
	}

	private static boolean setLevelUpNotificationOption(String optionText, BooleanSupplier successCondition, boolean closeInterface)
	{
		int[] dropdown = openSettingsSearch("level-up", "Select", "Toggle");
		if (dropdown == null)
		{
			closeSettingsMenu();
			return false;
		}
		MobileActions.widget(dropdown[0], dropdown[1], "");
		Global.sleepGaussian(600, 100);
		if (!Global.sleepUntil(() -> Rs2Widget.isWidgetVisible(InterfaceID.Settings.DROPDOWN_PANEL), 3000)
			|| !Rs2Widget.clickWidget(optionText, true))
		{
			closeSettingsMenu();
			return false;
		}
		boolean success = Global.sleepUntil(successCondition, 3000);
		finish(closeInterface);
		return success;
	}

	/**
	 * Opens the settings search, types {@code searchTerm} and waits for a visible result with one
	 * of {@code matchingActions}. Returns its {@code {packed id, child index}}, or {@code null}.
	 */
	private static int[] openSettingsSearch(String searchTerm, String... matchingActions)
	{
		if (!openSettings() || !Rs2Widget.clickWidget(InterfaceID.Settings.SEARCHBAR_IMAGE))
		{
			return null;
		}
		if (!Global.sleepUntil(() -> Rs2Widget.isWidgetVisible(InterfaceID.Settings.SEARCH_TEXT), 3000))
		{
			return null;
		}
		Global.sleepGaussian(300, 50);
		Rs2Keyboard.typeString(searchTerm);
		Global.sleepGaussian(800, 100);
		int[][] holder = new int[1][];
		boolean found = Global.sleepUntil(() ->
		{
			holder[0] = MobileActions.read(() -> findSearchResult(Microbot.getClient(), matchingActions), null);
			return holder[0] != null;
		}, 3000);
		return found ? holder[0] : null;
	}

	/** Client thread: the first visible search result with one of {@code matchingActions}. */
	static int[] findSearchResult(Client client, String... matchingActions)
	{
		Widget clickable = client.getWidget(SETTINGS_CLICKABLE);
		Widget[] children = clickable == null ? null : clickable.getDynamicChildren();
		if (children == null)
		{
			return null;
		}
		for (Widget child : children)
		{
			String[] actions = child == null || child.isHidden() ? null : child.getActions();
			if (actions != null && hasAny(actions, matchingActions))
			{
				return new int[]{SETTINGS_CLICKABLE, child.getIndex()};
			}
		}
		return null;
	}

	private static boolean hasAny(String[] actions, String[] wanted)
	{
		for (String a : actions)
		{
			for (String w : wanted)
			{
				if (w.equals(a))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** Client thread: the packed id of {@code icon}'s first static child while it still offers "Mute", else -1. */
	static int unmutedButton(Client client, int icon)
	{
		Widget parent = client.getWidget(icon);
		Widget[] children = parent == null ? null : parent.getStaticChildren();
		Widget button = children == null || children.length == 0 ? null : children[0];
		String[] actions = button == null ? null : button.getActions();
		return actions != null && actions.length > 0 && "mute".equalsIgnoreCase(actions[0]) ? button.getId() : -1;
	}

	/** Client thread: the spellbook tab stone for the current layout (upstream {@code Rs2Tab.getSpellBookTab}). */
	static int spellbookStone(Client client)
	{
		int id;
		if (!client.isResized())
		{
			id = InterfaceID.Toplevel.STONE6;
		}
		else if (client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 1)
		{
			id = InterfaceID.ToplevelPreEoc.STONE6;
		}
		else
		{
			id = InterfaceID.ToplevelOsrsStretch.STONE6;
		}
		return client.getWidget(id) == null ? -1 : id;
	}

	private static void finish(boolean closeInterface)
	{
		if (closeInterface)
		{
			closeSettingsMenu();
			Rs2Tab.switchTo(InterfaceTab.INVENTORY);
		}
	}

	private static boolean closeSettingsMenu()
	{
		if (isEscCloseInterfaceSettingEnabled())
		{
			Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
		}
		else
		{
			Rs2Widget.clickWidget(InterfaceID.Settings.CLOSE);
		}
		return Global.sleepUntil(() -> !Rs2Widget.isWidgetVisible(SETTINGS_INTERFACE));
	}

	private static boolean switchToSettingsTab(String tabName)
	{
		Integer index = SETTINGS_TABS.get(tabName);
		return index != null && MobileActions.widget(SETTINGS_CATEGORIES, index, "");
	}
}
