package net.runelite.client.plugins.microbot.util.walker;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/**
 * As upstream's {@code Rs2WalkerTransports.handleMinigameTeleport}: the grouping-tab minigame
 * teleport for a {@code TELEPORTATION_MINIGAME} transport. It opens the grouping tab, selects the
 * minigame in the dropdown, presses Teleport (choosing the pit for "Rat Pits: ..."), and waits for
 * the teleport animation to finish. Every widget read goes to the client thread and every click is
 * a mobile action outside it.
 *
 * <p>By policy no minigame teleport lands in or beside the Wilderness: Clan Wars and Last Man
 * Standing (both at Ferox Enclave) are refused, as is any destination with a Wilderness level.
 */
public final class MinigameTeleports
{
	static final Object[] SELECTED_OP_LISTENER = {489, 0, 0};
	static final List<Integer> TELEPORT_GRAPHICS = List.of(800, 802, 803, 804);
	static final int GROUPING_BUTTON = 46333957; // 707.5
	static final int DROPDOWN_BUTTON = 4980760; // 76.24
	static final int DROPDOWN_SELECTED_SPRITE = 773;
	static final int MINIGAME_LIST = 4980758; // 76.22
	static final int SELECTED_MINIGAME = 4980747; // 76.11
	static final int TELEPORT_BUTTON = 4980768; // 76.32
	private static final int TELEPORT_MILLIS = 20_000;
	/** Ferox Enclave destinations (Wilderness coordinates, level 0), refused like the jewellery box's. */
	private static final Set<String> REFUSED = Set.of("clan wars", "last man standing");

	private MinigameTeleports()
	{
	}

	/** The dropdown entry for {@code displayInfo}: lower case, before any ":" ("Rat Pits: Varrock" is "rat pits"). */
	public static String destinationName(String displayInfo)
	{
		if (displayInfo == null || displayInfo.isBlank())
		{
			return null;
		}
		int colon = displayInfo.indexOf(':');
		return (colon >= 0 ? displayInfo.substring(0, colon) : displayInfo).trim().toLowerCase(Locale.ROOT);
	}

	/** The dialogue option for a rat pits teleport ("varrock" for "Rat Pits: Varrock"), else null. */
	public static String ratPitsOption(String displayInfo)
	{
		if (!"rat pits".equals(destinationName(displayInfo)))
		{
			return null;
		}
		int colon = displayInfo.indexOf(':');
		String option = colon >= 0 ? displayInfo.substring(colon + 1).trim().toLowerCase(Locale.ROOT) : "";
		return option.isEmpty() ? null : option;
	}

	/** Whether policy refuses this teleport: a Ferox Enclave minigame or a Wilderness landing. */
	public static boolean isRefused(String displayInfo, WorldPoint destination)
	{
		String name = destinationName(displayInfo);
		return name == null || REFUSED.contains(name) || PohTeleports.isWildernessDestination(destination);
	}

	/** As upstream: teleports with {@code transport}'s minigame; false when refused or a step fails. */
	public static boolean teleport(Transport transport)
	{
		return transport != null && teleport(transport.getDisplayInfo(), transport.getDestination());
	}

	/** Teleports to the minigame named by {@code displayInfo}, landing at {@code destination}. */
	public static boolean teleport(String displayInfo, WorldPoint destination)
	{
		if (isRefused(displayInfo, destination))
		{
			return false;
		}
		String name = destinationName(displayInfo);
		String ratPit = ratPitsOption(displayInfo);
		if ("rat pits".equals(name) && ratPit == null)
		{
			return false;
		}
		// As upstream: minigame teleports cannot be used while a dialogue is open; a step in place closes it.
		if (Rs2Dialogue.isInDialogue())
		{
			WorldPoint here = Rs2Player.getWorldLocation();
			if (here != null)
			{
				MobileActions.walk(here);
			}
		}
		if (!Rs2Tab.switchTo(InterfaceTab.CHAT) || !openGrouping() || !select(name))
		{
			return false;
		}
		if (!Global.sleepUntil(() -> Rs2Widget.clickWidget(TELEPORT_BUTTON), 2_000))
		{
			return false;
		}
		if (ratPit != null && (!Rs2Dialogue.sleepUntilSelectAnOption() || !Rs2Dialogue.clickOption(ratPit)))
		{
			return false;
		}
		Global.sleepUntil(Rs2Player::isAnimating);
		return Global.sleepUntilTrue(() -> !Rs2Player.isAnimating()
			&& TELEPORT_GRAPHICS.stream().noneMatch(Rs2Player::hasSpotAnimation), 100, TELEPORT_MILLIS);
	}

	private static boolean openGrouping()
	{
		if (groupingSelected())
		{
			return true;
		}
		return Rs2Widget.clickWidget(GROUPING_BUTTON) && Global.sleepUntil(MinigameTeleports::groupingSelected, 2_000);
	}

	private static boolean groupingSelected()
	{
		return MobileActions.read(() ->
		{
			Widget button = Microbot.getClient().getWidget(GROUPING_BUTTON);
			return button != null && Arrays.equals(button.getOnOpListener(), SELECTED_OP_LISTENER);
		}, false);
	}

	private static boolean select(String name)
	{
		if (name.equalsIgnoreCase(selectedText()))
		{
			return true;
		}
		if (!dropdownOpen() && (!Rs2Widget.clickWidget(DROPDOWN_BUTTON)
			|| !Global.sleepUntil(MinigameTeleports::dropdownOpen, 2_000)))
		{
			return false;
		}
		Integer index = MobileActions.read(() ->
		{
			Widget list = Microbot.getClient().getWidget(MINIGAME_LIST);
			if (list == null || list.getDynamicChildren() == null)
			{
				return null;
			}
			List<Widget> entries = Arrays.stream(list.getDynamicChildren()).filter(Objects::nonNull).collect(Collectors.toList());
			Widget entry = Rs2Widget.findWidget(name, entries);
			return entry == null ? null : entry.getIndex();
		}, null);
		if (index == null)
		{
			return false;
		}
		Microbot.doInvoke(new NewMenuEntry().option("Select").target("").identifier(1).type(MenuAction.CC_OP)
			.param0(index).param1(MINIGAME_LIST).forceLeftClick(false), new Rectangle(1, 1));
		return Global.sleepUntil(() -> name.equalsIgnoreCase(selectedText()), 2_000);
	}

	private static String selectedText()
	{
		return MobileActions.read(() ->
		{
			Widget selected = Microbot.getClient().getWidget(SELECTED_MINIGAME);
			return selected == null ? null : selected.getText();
		}, null);
	}

	private static boolean dropdownOpen()
	{
		return MobileActions.read(() ->
		{
			Widget dropdown = Microbot.getClient().getWidget(DROPDOWN_BUTTON);
			return dropdown != null && dropdown.getSpriteId() == DROPDOWN_SELECTED_SPRITE;
		}, false);
	}
}
