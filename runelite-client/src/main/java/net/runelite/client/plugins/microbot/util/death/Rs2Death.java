package net.runelite.client.plugins.microbot.util.death;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/**
 * Death recovery (upstream Microbot 2.6.38 {@code Rs2Death}). Reads: which recovery interface is
 * open and the items it offers, read on the client thread. Actions (util action batch V): the
 * death-state tracking (fed by the mobile plugin), walking to and looting the grave within a fee
 * budget, and Death's Office (opt-in, as upstream). Ferox Enclave's office entrance is never chosen.
 */
public final class Rs2Death
{
	private Rs2Death()
	{
	}

	/** As upstream: a gravestone's contents interface is open. */
	public static boolean isGraveOpen()
	{
		return Rs2Widget.isWidgetVisible(InterfaceID.GravestoneGeneric.CONTENT);
	}

	/** As upstream: the grave's free items (empty when it is not open). */
	public static List<Rs2ItemModel> getGraveFreeItems()
	{
		return readItemContainer(InterfaceID.GravestoneGeneric.FREEITEMS);
	}

	/** As upstream: the grave's items that cost a fee (empty when it is not open). */
	public static List<Rs2ItemModel> getGravePaidItems()
	{
		return readItemContainer(InterfaceID.GravestoneGeneric.PAYITEMS);
	}

	/** As upstream: Death's Office (or the gravestone retrieval) item interface is open. */
	public static boolean isDeathsOfficeOpen()
	{
		return Rs2Widget.isWidgetVisible(InterfaceID.DeathOffice.ITEMS_CONTAINER)
			|| Rs2Widget.isWidgetVisible(InterfaceID.GravestoneRetrieval.ITEMS_CONTAINER);
	}

	/** As upstream: the items Death holds, from whichever retrieval interface is open. */
	public static List<Rs2ItemModel> getDeathsOfficeItems()
	{
		return readItemContainer(Rs2Widget.isWidgetVisible(InterfaceID.DeathOffice.ITEMS_CONTAINER)
			? InterfaceID.DeathOffice.ITEMS : InterfaceID.GravestoneRetrieval.ITEMS);
	}

	/** The items shown in container {@code componentId}'s dynamic children (slot = child index). */
	private static List<Rs2ItemModel> readItemContainer(int componentId)
	{
		return MobileActions.read(() ->
		{
			Widget container = Microbot.getClient().getWidget(componentId);
			Widget[] children = container == null ? null : container.getDynamicChildren();
			List<Rs2ItemModel> items = new ArrayList<>();
			for (int slot = 0; children != null && slot < children.length; slot++)
			{
				Widget child = children[slot];
				int itemId = child == null ? -1 : child.getItemId();
				if (itemId > 0)
				{
					ItemComposition definition = Microbot.getClient().getItemDefinition(itemId);
					items.add(new Rs2ItemModel(new Item(itemId, Math.max(1, child.getItemQuantity())), definition, null, slot));
				}
			}
			return items;
		}, Collections.emptyList());
	}

	// ---- Read batch 16j (upstream 2.6.38): the Items Kept on Death interface ----

	/** Upstream's grave fee caption pattern ("fee: 12,345"). */
	private static final java.util.regex.Pattern FEE_LABEL = java.util.regex.Pattern.compile("(?i)fee:\\s*([^)<]+)");

	/** As upstream: the Items Kept on Death interface is showing. */
	public static boolean isItemsKeptOnDeathOpen()
	{
		return Rs2Widget.isWidgetVisible(InterfaceID.Deathkeep.KEPT);
	}

	/** As upstream: the items that interface says are kept. */
	public static List<Rs2ItemModel> getItemsKeptOnDeath()
	{
		return readItemContainer(InterfaceID.Deathkeep.KEPT);
	}

	/** As upstream: the items that interface says go to the grave. */
	public static List<Rs2ItemModel> getItemsSentToGrave()
	{
		return readItemContainer(InterfaceID.Deathkeep.GRAVE);
	}

	/** As upstream: the grave fee from the grave panel's caption, or 0. */
	public static int getPredictedGraveFee()
	{
		String label = MobileActions.read(() ->
		{
			Widget container = Microbot.getClient().getWidget(InterfaceID.Deathkeep.GRAVE);
			Widget[] children = container == null ? null : container.getDynamicChildren();
			for (int i = 0; children != null && i < children.length; i++)
			{
				String text = children[i] == null ? null : children[i].getText();
				if (text != null && !text.isEmpty())
				{
					return text;
				}
			}
			return null;
		}, null);
		if (label == null)
		{
			return 0;
		}
		java.util.regex.Matcher fee = FEE_LABEL.matcher(label);
		if (!fee.find())
		{
			return 0;
		}
		java.util.regex.Matcher digits = DIGITS.matcher(fee.group(1));
		try
		{
			return digits.find() ? Integer.parseInt(digits.group().replace(",", "")) : 0;
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	/** As upstream: the risked value shown by the interface, or 0. */
	public static int getRiskValue()
	{
		return parseFee(InterfaceID.Deathkeep.VALUE);
	}

	// ---- Util action batch V (Claude 1): death state, grave, Death's Office, recovery ----
	//
	// As upstream, nothing here runs on its own: a script polls hasDeathToHandle() and calls
	// recoverItems(budget[, includeDeathsOffice]). Death's Office is opt-in, because its fee is
	// uncapped and never shown before it is charged. The first-death tutorial is not handled.

	/** Grave NPC ids run contiguously over every cosmetic variant. */
	private static final int GRAVE_NPC_ID_MIN = net.runelite.api.gameval.NpcID.GRAVESTONE_DEFAULT;
	private static final int GRAVE_NPC_ID_MAX = net.runelite.api.gameval.NpcID.GRAVESTONE_ANGEL_255;
	private static final String GRAVE_LOOT_ACTION = "Loot";
	private static final String GRAVE_TAKE_ACTION = "Take";
	private static final String DEATH_OFFICE_SELECT_ACTION = "Select";
	private static final String DEATH_RECLAIM_OPTION = "have you got anything for me";
	private static final String ENTER_DEATHS_DOMAIN_ACTION = "Enter Death's Domain";
	private static final int DEATH_DOMAIN_REGION_ID = 12633;
	private static final int ENTER_TIMEOUT_MS = 10_000;
	private static final long GAME_TICK_MS = 600L;
	private static final int GRAVE_INTERACT_DISTANCE = 7;
	private static final int SCENE_RADIUS = 104;
	private static final int INTERFACE_TIMEOUT_MS = 5_000;
	private static final int LOOT_TIMEOUT_MS = 3_000;
	private static final java.util.regex.Pattern DIGITS = java.util.regex.Pattern.compile("[\\d,]+");

	private static volatile net.runelite.api.coords.WorldPoint lastDeathLocation;
	private static volatile java.time.Instant lastDeathTime;
	/** A grave was seen standing since the last death (a PvP death may leave none). */
	private static volatile boolean graveSeen;

	/** As upstream: where the player last died, or null. */
	public static net.runelite.api.coords.WorldPoint getLastDeathLocation()
	{
		return lastDeathLocation;
	}

	/** As upstream: when the player last died, or null. */
	public static java.time.Instant getLastDeathTime()
	{
		return lastDeathTime;
	}

	/** As upstream: records the local player's death (forwarded by the mobile plugin). */
	public static void handleActorDeath(net.runelite.api.events.ActorDeath event)
	{
		net.runelite.api.Player local = Microbot.getClient() == null ? null : Microbot.getClient().getLocalPlayer();
		if (event == null || local == null || event.getActor() != local)
		{
			return;
		}
		lastDeathLocation = local.getWorldLocation();
		lastDeathTime = java.time.Instant.now();
		graveSeen = false;
	}

	/** As upstream: notes that a grave appeared (the varbit is non-zero, typically 133, while one stands). */
	public static void onVarbitChanged(net.runelite.api.events.VarbitChanged event)
	{
		if (event != null && event.getVarbitId() == net.runelite.api.gameval.VarbitID.GRAVESTONE_VISIBLE && event.getValue() != 0)
		{
			graveSeen = true;
		}
	}

	/** As upstream: forgets the recorded death (done automatically after a recovery). */
	public static void clearDeathState()
	{
		lastDeathLocation = null;
		lastDeathTime = null;
		graveSeen = false;
	}

	/** As upstream: the player is in the death animation (only briefly true before the respawn). */
	public static boolean isDead()
	{
		return MobileActions.read(() ->
		{
			net.runelite.api.Player local = Microbot.getClient().getLocalPlayer();
			return local != null && local.isDead();
		}, false);
	}

	/** As upstream: a death was recorded within the last {@code withinMs}. */
	public static boolean hasDiedRecently(long withinMs)
	{
		java.time.Instant died = lastDeathTime;
		return died != null && java.time.Duration.between(died, java.time.Instant.now()).toMillis() <= withinMs;
	}

	/** As upstream: an uncollected grave stands somewhere (the visible varbit is non-zero). */
	public static boolean hasGrave()
	{
		return Microbot.getVarbitValue(net.runelite.api.gameval.VarbitID.GRAVESTONE_VISIBLE) != 0;
	}

	/** As upstream: the grave's remaining time (its varbit counts game ticks). */
	public static java.time.Duration getGraveTimeRemaining()
	{
		int ticks = Math.max(0, Microbot.getVarbitValue(net.runelite.api.gameval.VarbitID.GRAVESTONE_DURATION));
		return java.time.Duration.ofMillis(ticks * GAME_TICK_MS);
	}

	/** As upstream: a grave was seen after the last death but is gone, so the items moved to Death's Office. */
	public static boolean hasGraveExpired()
	{
		return graveSeen && !hasGrave();
	}

	/** As upstream: the Wilderness level of the last death spot, 0 outside it or when none is recorded. */
	public static int getDeathWildernessLevel()
	{
		net.runelite.api.coords.WorldPoint at = lastDeathLocation;
		return at == null ? 0 : net.runelite.client.plugins.microbot.util.player.Rs2Pvp.getWildernessLevelFrom(at);
	}

	/** As upstream: a grave still stands, or its items wait at Death's Office. */
	public static boolean hasDeathToHandle()
	{
		return hasGrave() || hasGraveExpired();
	}

	/** As upstream: the player is inside Death's Domain. */
	public static boolean isInDeathsOffice()
	{
		net.runelite.api.coords.WorldPoint at = net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation();
		return at != null && at.getRegionID() == DEATH_DOMAIN_REGION_ID;
	}

	/** As upstream: the grave in the scene nearest the death spot (else the player), or null. */
	public static net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel getGrave()
	{
		net.runelite.api.coords.WorldPoint anchor = lastDeathLocation != null ? lastDeathLocation
			: net.runelite.client.plugins.microbot.util.player.Rs2Player.getWorldLocation();
		if (anchor == null || Microbot.getRs2NpcCache() == null)
		{
			return null;
		}
		return Microbot.getRs2NpcCache().query()
			.where(npc -> npc.getId() >= GRAVE_NPC_ID_MIN && npc.getId() <= GRAVE_NPC_ID_MAX)
			.nearest(anchor, SCENE_RADIUS);
	}

	/** As upstream: walks to within 7 tiles of the grave, or of the death spot before it loads. */
	public static boolean walkToGrave()
	{
		net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel grave = getGrave();
		net.runelite.api.coords.WorldPoint target = grave != null ? grave.getWorldLocation() : lastDeathLocation;
		return target != null && net.runelite.client.plugins.microbot.util.walker.Rs2Walker.walkTo(target, GRAVE_INTERACT_DISTANCE);
	}

	/** As upstream: "Loot" on the grave, then waits for its interface. */
	public static boolean openGrave()
	{
		if (isGraveOpen()) return true;
		net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel grave = getGrave();
		return grave != null && grave.click(GRAVE_LOOT_ACTION)
			&& net.runelite.client.plugins.microbot.util.Global.sleepUntil(Rs2Death::isGraveOpen, INTERFACE_TIMEOUT_MS);
	}

	/** As upstream: the fee for the paid half, or 0 when nothing is owed (the text can read "Fee: Paid"). */
	public static int getGraveFee()
	{
		return parseFee(InterfaceID.GravestoneGeneric.FEE);
	}

	/** As upstream: takes the whole free half; true once it is confirmed gone (or the grave is). */
	public static boolean lootGraveFreeItems()
	{
		if (!isGraveOpen()) return false;
		if (getGraveFreeItems().isEmpty()) return true;
		clickAndSettle(InterfaceID.GravestoneGeneric.FREEBUTTON);
		net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> !hasGrave()
			|| (isGraveOpen() && getGraveFreeItems().isEmpty())
			|| net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.isFull(), LOOT_TIMEOUT_MS);
		return !hasGrave() || (isGraveOpen() && getGraveFreeItems().isEmpty());
	}

	/**
	 * As upstream: takes only the grave items matching {@code filter} from both halves, highest slot
	 * first. Taking any paid item pays the whole fee. The number of slots clicked.
	 */
	public static int lootGraveItems(java.util.function.Predicate<Rs2ItemModel> filter)
	{
		if (!isGraveOpen()) return 0;
		return takeMatchingSlots(InterfaceID.GravestoneGeneric.FREEITEMS, filter, GRAVE_TAKE_ACTION)
			+ takeMatchingSlots(InterfaceID.GravestoneGeneric.PAYITEMS, filter, GRAVE_TAKE_ACTION);
	}

	/**
	 * As upstream: pays for and takes the paid half when the fee is within {@code budget} (charged to
	 * Death's Coffer, then the bank). False when it was left (it keeps in Death's Office) or not emptied.
	 */
	public static boolean lootGravePaidItems(int budget)
	{
		if (!isGraveOpen()) return false;
		int fee = getGraveFee();
		if (fee > 0 && fee > budget) return false;
		clickAndSettle(InterfaceID.GravestoneGeneric.PAYBUTTON);
		return net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> !hasGrave(), LOOT_TIMEOUT_MS);
	}

	/** As upstream: walks to the nearest Death's Office entrance (never the Wilderness one). */
	public static boolean walkToDeathsOffice()
	{
		DeathsOfficeLocation location = DeathsOfficeLocation.getNearest();
		return location != null && net.runelite.client.plugins.microbot.util.walker.Rs2Walker.walkTo(location.getEntrance(), 6);
	}

	/** As upstream: "Enter Death's Domain" on the entrance object, then waits to be inside. */
	public static boolean enterDeathsOffice()
	{
		if (isInDeathsOffice()) return true;
		if (Microbot.getRs2TileObjectCache() == null) return false;
		net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel entrance = Microbot.getRs2TileObjectCache().query()
			.withId(net.runelite.api.gameval.ObjectID1.DEATH_OFFICE_ACCESS_GRAVE).nearest();
		return entrance != null && entrance.click(ENTER_DEATHS_DOMAIN_ACTION)
			&& net.runelite.client.plugins.microbot.util.Global.sleepUntil(Rs2Death::isInDeathsOffice, ENTER_TIMEOUT_MS);
	}

	/**
	 * As upstream: inside Death's Domain, steps Death's dialogue (continue, then the reclaim option,
	 * matched by text) until the retrieval interface opens.
	 */
	public static boolean openDeathsOffice()
	{
		if (isDeathsOfficeOpen()) return true;
		if (!isInDeathsOffice()) return false;
		return net.runelite.client.plugins.microbot.util.Global.sleepUntil(Rs2Death::isDeathsOfficeOpen,
			Rs2Death::advanceReclaimDialogue, INTERFACE_TIMEOUT_MS, 600);
	}

	private static void advanceReclaimDialogue()
	{
		if (net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.hasContinue())
		{
			net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.clickContinue();
		}
		else if (net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.hasSelectAnOption())
		{
			net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.clickOption(DEATH_RECLAIM_OPTION);
		}
	}

	/**
	 * As upstream: reclaims only the office items matching {@code filter} (Select, then All), highest
	 * slot first; the office charges per item. Only in the Death's Office variant. The number taken.
	 */
	public static int reclaimItems(java.util.function.Predicate<Rs2ItemModel> filter)
	{
		if (!isDeathsOfficeOpen() || !Rs2Widget.isWidgetVisible(InterfaceID.DeathOffice.ITEMS_CONTAINER)) return 0;
		List<Rs2ItemModel> items = getDeathsOfficeItems();
		int taken = 0;
		for (int i = items.size() - 1; i >= 0; i--)
		{
			Rs2ItemModel item = items.get(i);
			if (filter != null && !filter.test(item)) continue;
			if (net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.isFull()) break;
			clickItemSlot(InterfaceID.DeathOffice.ITEMS, item, DEATH_OFFICE_SELECT_ACTION);
			if (!net.runelite.client.plugins.microbot.util.Global.sleepUntil(
				() -> Rs2Widget.isWidgetVisible(InterfaceID.DeathOffice.ALL), INTERFACE_TIMEOUT_MS))
			{
				break;
			}
			clickAndSettle(InterfaceID.DeathOffice.ALL);
			taken++;
		}
		return taken;
	}

	/**
	 * As upstream: takes everything Death holds into the inventory. There is no spending limit: the
	 * fee is never shown before it is charged. False when items remain (for example, a full inventory).
	 */
	public static boolean reclaimAll()
	{
		if (!isDeathsOfficeOpen()) return false;
		clickAndSettle(Rs2Widget.isWidgetVisible(InterfaceID.DeathOffice.ITEMS_CONTAINER)
			? InterfaceID.DeathOffice.TAKEALL : InterfaceID.GravestoneRetrieval.BUTTON);
		net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> !isDeathsOfficeOpen()
			|| net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.isFull(), LOOT_TIMEOUT_MS);
		return !isDeathsOfficeOpen();
	}

	/** As upstream: recovers from the grave paying whatever it asks; Death's Office is left alone. */
	public static boolean recoverItems()
	{
		return recoverItems(Integer.MAX_VALUE, false);
	}

	/** As upstream: recovers from the grave, paying at most {@code budget} (0 takes only the free half). */
	public static boolean recoverItems(int budget)
	{
		return recoverItems(budget, false);
	}

	/**
	 * As upstream: walks to the grave and empties it, or (only when asked) reclaims everything at
	 * Death's Office once the grave has expired. True when the death was dealt with, including the
	 * deliberate choices to leave an over-budget paid half or the office untouched.
	 */
	public static boolean recoverItems(int budget, boolean includeDeathsOffice)
	{
		if (!hasDeathToHandle()) return true;
		if (!hasGrave() && !includeDeathsOffice)
		{
			clearDeathState();
			return true;
		}
		boolean collected = hasGrave() ? collectFromGrave(budget) : collectFromDeathsOffice();
		if (collected)
		{
			clearDeathState();
		}
		return collected;
	}

	private static boolean collectFromGrave(int budget)
	{
		if (!walkToGrave() || !openGrave() || !lootGraveFreeItems()) return false;
		int fee = getGraveFee();
		boolean paid = lootGravePaidItems(budget);
		closeInterfaces();
		return fee > budget || paid;
	}

	private static boolean collectFromDeathsOffice()
	{
		if (!walkToDeathsOffice() || !enterDeathsOffice() || !openDeathsOffice()) return false;
		boolean reclaimed = reclaimAll();
		closeInterfaces();
		return reclaimed;
	}

	/** As upstream: closes whichever retrieval interface is still up (the grave timer pauses while open). */
	public static void closeInterfaces()
	{
		if (isGraveOpen())
		{
			Rs2Widget.clickWidget(InterfaceID.GravestoneGeneric.CLOSE);
			net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> !isGraveOpen(), INTERFACE_TIMEOUT_MS);
		}
		if (isDeathsOfficeOpen())
		{
			Rs2Widget.findWidgetsWithAction("Close", InterfaceID.DEATH_OFFICE, true);
			if (net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> !isDeathsOfficeOpen(), INTERFACE_TIMEOUT_MS)) return;
			if (net.runelite.client.plugins.microbot.util.settings.Rs2Settings.isEscCloseInterfaceSettingEnabled())
			{
				net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard.keyPress(java.awt.event.KeyEvent.VK_ESCAPE);
				net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> !isDeathsOfficeOpen(), INTERFACE_TIMEOUT_MS);
			}
		}
	}

	private static int takeMatchingSlots(int containerId, java.util.function.Predicate<Rs2ItemModel> filter, String action)
	{
		List<Rs2ItemModel> items = readItemContainer(containerId);
		int taken = 0;
		for (int i = items.size() - 1; i >= 0; i--)
		{
			Rs2ItemModel item = items.get(i);
			if (filter != null && !filter.test(item)) continue;
			if (net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.isFull()) break;
			clickItemSlot(containerId, item, action);
			taken++;
		}
		return taken;
	}

	/** One item slot's option in a death interface (slot = child index), then a tick to settle. */
	private static void clickItemSlot(int containerId, Rs2ItemModel item, String action)
	{
		MobileActions.widget(containerId, item.getSlot(), action);
		net.runelite.client.plugins.microbot.util.Global.sleepUntilNextTick();
	}

	private static void clickAndSettle(int componentId)
	{
		Rs2Widget.clickWidget(componentId);
		net.runelite.client.plugins.microbot.util.Global.sleepUntilNextTick();
	}

	private static int parseFee(int componentId)
	{
		String text = MobileActions.read(() ->
		{
			Widget widget = Microbot.getClient().getWidget(componentId);
			return widget == null ? null : widget.getText();
		}, null);
		if (text == null) return 0;
		java.util.regex.Matcher matcher = DIGITS.matcher(text);
		if (!matcher.find()) return 0;
		try
		{
			return Integer.parseInt(matcher.group().replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}
}
