package net.runelite.client.plugins.microbot.mobile;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.headless.JavaBridge;
import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

/** Exact live-target engine actions, confined to the client thread and a revocable host session.
 * True means dispatched, never game success. Callers must observe inventory/animation/location changes.
 */
public final class MobileActions
{
	private static volatile ScriptInputSession permission;
	private static volatile String inventoryFailure = "";
	private static int lastTick = Integer.MIN_VALUE;
	private static ScriptInputSession lastPermission;
	private MobileActions() { }

	public static void bind(ScriptInputSession session)
	{
		if (permission != null && permission != session) { permission.close(); }
		permission = session;
	}

	public static void unbind(ScriptInputSession session)
	{
		if (session != null) { session.close(); }
		if (permission == session) { permission = null; }
	}

	public static ScriptInputSession currentPermission() { return permission; }

	public static boolean active()
	{
		ScriptInputSession owner = permission;
		return owner != null && owner.isActive() && MicrobotRuntime.isInstalled()
			&& !Thread.currentThread().isInterrupted();
	}

	public static <T> T read(Callable<T> operation, T fallback)
	{
		MicrobotReadServices services = MicrobotReadServices.current();
		return services == null ? fallback : services.getClientThread()
			.runOnClientThreadOptional(operation, 1500).orElse(fallback);
	}

	private static boolean perform(BooleanSupplier action)
	{
		final ScriptInputSession owner = permission;
		return read(() ->
		{
			MicrobotReadServices services = MicrobotReadServices.current();
			if (services == null || owner == null || permission != owner || !owner.isActive()
				|| !MicrobotRuntime.isInstalled() || MicrobotRuntime.humanOwnsInput()) { return false; }
			Client client = services.getClient();
			if (!client.isClientThread() || client.getGameState() != GameState.LOGGED_IN
				|| client.getLocalPlayer() == null || client.isMenuOpen() || JavaBridge.scriptInputBusy()) { return false; }
			int tick = client.getTickCount();
			if (lastPermission == owner && lastTick == tick) { return false; }
			if (!action.getAsBoolean()) { return false; }
			lastPermission = owner;
			lastTick = tick;
			JavaBridge.refreshScriptIdleTimer();
			return true;
		}, false);
	}

	private static Client client() { return MicrobotReadServices.require().getClient(); }
	private static int actionIndex(String[] actions, String wanted)
	{
		if (actions == null || wanted == null) { return -1; }
		for (int i = 0; i < actions.length; i++)
		{
			if (actions[i] != null && actions[i].equalsIgnoreCase(wanted)) { return i; }
		}
		return -1;
	}

	/**
	 * Opens a side-panel tab by its top-level panel index (upstream {@code Rs2Tab}: client script
	 * 915). One script action; the caller observes the switch.
	 */
	public static boolean openTab(int panelIndex)
	{
		return panelIndex >= 0 && perform(() -> { client().runScript(915, panelIndex); return true; });
	}

	/** Inventory must be opened by the script; a user tap would revoke its permission. */
	public static boolean ensureInventoryVisible()
	{
		return read(() ->
		{
			Widget bank = client().getWidget(WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER);
			if (bank != null && !bank.isHidden()) { return rejectInventory("Bank inventory is open"); }
			Widget inventory = client().getWidget(WidgetInfo.INVENTORY);
			if (inventory != null && !inventory.isHidden()) { return active(); }
			inventoryFailure = "Inventory panel is not visible";
			// Upstream Rs2Tab.switchTo: client script 915, inventory tab index 3.
			perform(() -> { client().runScript(915, 3); return true; });
			// Wait for the rebuilt, visible item widgets before attempting any item action.
			return false;
		}, false);
	}

	public static String inventoryFailure() { return inventoryFailure; }
	private static boolean rejectInventory(String reason)
	{
		inventoryFailure = reason;
		return false;
	}

	public static boolean inventory(int slot, String action)
	{
		return inventory(slot, -1, action);
	}

	/** Expected-id binding prevents a delayed retry from acting on an item that replaced its slot. */
	public static boolean inventory(int slot, int expectedId, String action)
	{
		inventoryFailure = "Input is unavailable, busy or waiting for the next game tick";
		return perform(() -> inventoryNow(slot, expectedId, action));
	}

	private static boolean inventoryNow(int slot, int expectedId, String action)
	{
		Client c = client();
		ItemContainer inventory = c.getItemContainer(InventoryID.INVENTORY);
		if (inventory == null || inventory.getItems() == null || slot < 0 || slot >= inventory.getItems().length
			|| action == null || action.trim().isEmpty()) { return false; }
		Item item = inventory.getItems()[slot];
		if (item == null || item.getId() < 0 || (expectedId >= 0 && item.getId() != expectedId)) { return false; }
		Widget bankInventory = c.getWidget(WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER);
		boolean bankOpen = bankInventory != null && !bankInventory.isHidden();
		Widget parent = bankOpen ? bankInventory : c.getWidget(WidgetInfo.INVENTORY);
		if (parent == null || parent.isHidden()) { return rejectInventory("Inventory panel is not visible"); }
		Widget itemWidget = parent.getChild(slot);
		if (itemWidget == null || itemWidget.isHidden()) { return rejectInventory("Inventory item widget is not ready"); }
		if (itemWidget.getItemId() != item.getId()) { return rejectInventory("Inventory slot changed before the action"); }
		ItemComposition definition = c.getItemDefinition(item.getId());
		if (definition == null) { return false; }
		int widgetId = parent.getId();
		if ("Use".equalsIgnoreCase(action))
		{
			c.menuAction(slot, widgetId, c.isWidgetSelected() ? MenuAction.WIDGET_TARGET_ON_WIDGET : MenuAction.WIDGET_TARGET,
				0, item.getId(), "Use", definition.getName());
			return true;
		}
		// Never turn an intended Drop/Deposit into an item-on-item action.
		if (c.isWidgetSelected()) { return rejectInventory("An item or spell is selected"); }
		if ("Drop".equalsIgnoreCase(action)
			&& net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.isProtectedAxe(item.getId(), definition.getName()))
		{
			return rejectInventory("Axes are protected from dropping");
		}
		String[] actions = itemWidget.getActions();
		int op = actionIndex(actions, action);
		int identifier = op + 1;
		// Item-definition indices are NOT widget action ordinals. The injected client's
		// getItemOp() maps widget 2,3,4,6,7 to definition ops 1..5; widget 5 is Use.
		if (op < 0 && !bankOpen)
		{
			identifier = inventoryWidgetOp(actionIndex(definition.getInventoryActions(), action));
			op = identifier - 1;
		}
		if (op < 0)
		{
			String[][] subops = definition.getSubops();
			String[] baseOps = definition.getInventoryActions();
			if (subops != null && baseOps != null)
			{
				for (int parentOp = 0; parentOp < subops.length && parentOp < baseOps.length; parentOp++)
				{
					int sub = actionIndex(subops[parentOp], action);
					int parentIdentifier = actionIndex(actions, baseOps[parentOp]) + 1;
					if (parentIdentifier == 0 && !bankOpen) { parentIdentifier = inventoryWidgetOp(parentOp); }
					if (sub >= 0 && parentIdentifier > 0)
					{
						op = parentIdentifier - 1;
						identifier = ((sub + 1) << 16) | parentIdentifier;
						break;
					}
				}
			}
		}
		if (op < 0) { return rejectInventory("The item does not expose action: " + action); }
		c.menuAction(slot, widgetId, op < 5 ? MenuAction.CC_OP : MenuAction.CC_OP_LOW_PRIORITY,
			identifier, item.getId(), action, definition.getName());
		return true;
	}

	private static int inventoryWidgetOp(int definitionIndex)
	{
		switch (definitionIndex)
		{
			case 0: return 2;
			case 1: return 3;
			case 2: return 4;
			case 3: return 6;
			case 4: return 7;
			default: return 0;
		}
	}

	public static boolean bank(int slot, String action)
	{
		return bank(slot, -1, action);
	}

	public static boolean bank(int slot, int expectedId, String action)
	{
		return widget(WidgetInfo.BANK_ITEM_CONTAINER.getId(), slot, expectedId, action);
	}

	public static boolean widget(int packedId, int childIndex, String action)
	{
		return widget(packedId, childIndex, -1, action);
	}

	private static boolean widget(int packedId, int childIndex, int expectedId, String action)
	{
		return perform(() ->
		{
			Widget parent = client().getWidget(packedId);
			if (parent == null || parent.isHidden()) { return false; }
			Widget w = childIndex < 0 ? parent : parent.getChild(childIndex);
			if (w == null || w.isHidden()) { return false; }
			if (packedId == WidgetInfo.BANK_ITEM_CONTAINER.getId())
			{
				ItemContainer bank = client().getItemContainer(InventoryID.BANK);
				if (bank == null || bank.getItems() == null || childIndex < 0 || childIndex >= bank.getItems().length) { return false; }
				Item item = bank.getItems()[childIndex];
				if (item == null || item.getId() < 0 || item.getQuantity() <= 0 || w.getItemId() != item.getId()
					|| (expectedId >= 0 && item.getId() != expectedId)) { return false; }
			}
			String[] actions = w.getActions();
			int op = actionIndex(actions, action);
			if ((action == null || action.isEmpty()) && actions != null)
			{
				for (int i = 0; i < actions.length; i++)
				{
					if (actions[i] != null && !actions[i].isEmpty()) { op = i; break; }
				}
			}
			if (op >= 0)
			{
				client().menuAction(childIndex, packedId, op < 5 ? MenuAction.CC_OP : MenuAction.CC_OP_LOW_PRIORITY,
					op + 1, w.getItemId(), actions[op], w.getName());
				return true;
			}
			if (action == null || action.isEmpty())
			{
				java.awt.Rectangle b = w.getBounds();
				return b != null && !b.isEmpty() && JavaBridge.onScriptClick(
					b.x + b.width / 2, b.y + b.height / 2, permission);
			}
			return false;
		});
	}

	public static boolean object(int id, String action) { return objectAt(id, null, action); }
	public static boolean objectAt(int id, WorldPoint position, String action)
	{
		return perform(() ->
		{
			Rs2TileObjectModel model = MicrobotReadServices.require().getRs2TileObjectCache().query()
				.where(x -> x.getId() == id && (position == null || position.equals(x.getWorldLocation())))
				.nearest();
			if (model == null || model.getWorldView() != client().getTopLevelWorldView()
				|| model.getWorldLocation().getPlane() != client().getLocalPlayer().getWorldLocation().getPlane()) { return false; }
			ObjectComposition definition = model.getObjectComposition();
			if (definition == null) { return false; }
			int op = actionIndex(definition.getActions(), action);
			if (op < 0 || op > 4) { return false; }
			WorldPoint at = model.getWorldLocation();
			WorldView view = client().getTopLevelWorldView();
			MenuAction type = op == 4 ? MenuAction.GAME_OBJECT_FIFTH_OPTION
				: MenuAction.of(MenuAction.GAME_OBJECT_FIRST_OPTION.getId() + op);
			client().menuAction(at.getX() - view.getBaseX(), at.getY() - view.getBaseY(), type,
				id, -1, action, definition.getName());
			return true;
		});
	}

	public static boolean npc(int id, String action) { return npcAt(id, -1, action); }
	public static boolean npcAt(int id, int index, String action)
	{
		return perform(() ->
		{
			NPC nearest = null;
			int distance = Integer.MAX_VALUE;
			for (NPC npc : client().getTopLevelWorldView().npcs())
			{
				if (npc.getId() != id || (index >= 0 && index != npc.getIndex())) { continue; }
				int d = npc.getWorldLocation().distanceTo(client().getLocalPlayer().getWorldLocation());
				if (d < distance) { nearest = npc; distance = d; }
			}
			if (nearest == null || distance > 20) { return false; }
			NPCComposition definition = nearest.getTransformedComposition();
			int op = definition == null ? -1 : actionIndex(definition.getActions(), action);
			if (op < 0 || op > 4) { return false; }
			client().menuAction(0, 0, MenuAction.of(MenuAction.NPC_FIRST_OPTION.getId() + op),
				nearest.getIndex(), -1, action, nearest.getName());
			return true;
		});
	}

	/**
	 * Picks the player menu option {@code option} (for example "Attack") on the player with this id,
	 * as one script action. The option's slot is read from the client's current player options, so it
	 * follows the area (Attack moves with PvP rules). "Trade with" is refused: no item transfers.
	 */
	public static boolean player(int playerId, String option)
	{
		if (option == null || "trade with".equalsIgnoreCase(option.trim()))
		{
			return false;
		}
		return perform(() ->
		{
			Player target = null;
			for (Player player : client().getTopLevelWorldView().players())
			{
				if (player != null && player.getId() == playerId) { target = player; break; }
			}
			String[] options = client().getPlayerOptions();
			if (target == null || options == null) { return false; }
			String[] plain = new String[options.length];
			for (int i = 0; i < options.length; i++)
			{
				plain[i] = options[i] == null ? null : net.runelite.client.util.Text.removeTags(options[i]);
			}
			int op = actionIndex(plain, option);
			if (op < 0 || op > 7) { return false; }
			client().menuAction(0, 0, MenuAction.of(MenuAction.PLAYER_FIRST_OPTION.getId() + op),
				playerId, -1, plain[op], target.getName());
			return true;
		});
	}

	public static boolean walk(WorldPoint destination)
	{
		return perform(() ->
		{
			WorldView view = client().getTopLevelWorldView();
			if (destination == null || view == null || destination.getPlane() != client().getLocalPlayer().getWorldLocation().getPlane()) { return false; }
			int x = destination.getX() - view.getBaseX(), y = destination.getY() - view.getBaseY();
			if (x < 0 || y < 0 || x >= 104 || y >= 104) { return false; }
			return JavaBridge.onScriptWalk(destination, permission);
		});
	}

	// ---- Script-initiated world hops. The plugin keeps a session alive through a hop only while
	// this records that the same session asked for it (see MobileMicrobotPlugin). ----

	private static volatile ScriptInputSession hopOwner;
	private static volatile int hopWorld = -1;
	private static volatile long hopDeadlineNanos;

	/** How long a requested hop may take before it no longer counts as the script's own. */
	static final long HOP_GRACE_MILLIS = 30_000L;

	/** Opens the world switcher (upstream's first hop step). One script action. */
	public static boolean openWorldHopper()
	{
		return perform(() -> { client().openWorldHopper(); return true; });
	}

	/** Requests a hop to {@code world} and records it as this session's own. One script action. */
	public static boolean hopToWorld(World world)
	{
		if (world == null) { return false; }
		final ScriptInputSession owner = permission;
		return perform(() ->
		{
			hopOwner = owner;
			hopWorld = world.getId();
			hopDeadlineNanos = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(HOP_GRACE_MILLIS);
			client().hopToWorld(world);
			return true;
		});
	}

	/** True while the bound, still-active session has a hop of its own in flight. */
	public static boolean isExpectedHop()
	{
		ScriptInputSession owner = hopOwner;
		return owner != null && owner == permission && owner.isActive() && System.nanoTime() < hopDeadlineNanos;
	}

	/** The world of the hop in flight, or -1. */
	public static int expectedHopWorld()
	{
		return isExpectedHop() ? hopWorld : -1;
	}

	public static void clearExpectedHop()
	{
		hopOwner = null;
		hopWorld = -1;
	}

	/**
	 * Selects a target spell (alchemy, superheat, cast on an NPC) by its spellbook widget, as
	 * upstream's WIDGET_TARGET menu entry; the next target action applies it. One script action.
	 */
	public static boolean selectSpell(int widgetId, String option, int identifier)
	{
		return perform(() ->
		{
			Widget spell = client().getWidget(widgetId);
			if (spell == null || spell.isHidden()) { return false; }
			client().menuAction(-1, widgetId, MenuAction.WIDGET_TARGET, identifier, -1, option, spell.getName());
			return true;
		});
	}

	/** Applies the selected spell to the NPC with this scene index. One script action. */
	public static boolean spellOnNpc(int npcIndex)
	{
		return perform(() -> client().isWidgetSelected() && selectedOnNpc(npcIndex, "Cast"));
	}

	/**
	 * Uses the item in {@code slot} on the NPC with this scene index: "Use" on the item, then the
	 * selected item on the NPC, checked to still be that item. Two script actions.
	 */
	public static boolean itemOnNpc(int slot, int npcIndex)
	{
		int itemId = inventoryId(slot);
		if (itemId < 0 || !waitFor(() -> inventory(slot, itemId, "Use"), 1800)) { return false; }
		return waitFor(() -> perform(() ->
		{
			Widget selected = client().getSelectedWidget();
			if (!client().isWidgetSelected() || selected == null || selected.getIndex() != slot || selected.getItemId() != itemId) { return false; }
			return selectedOnNpc(npcIndex, "Use");
		}), 1800);
	}

	/** The selected widget (a spell or an item) on the NPC with this scene index; client thread only. */
	private static boolean selectedOnNpc(int npcIndex, String option)
	{
		NPC target = null;
		for (NPC npc : client().getTopLevelWorldView().npcs())
		{
			if (npc.getIndex() == npcIndex) { target = npc; break; }
		}
		if (target == null) { return false; }
		client().menuAction(0, 0, MenuAction.WIDGET_TARGET_ON_NPC, npcIndex, -1, option, target.getName());
		return true;
	}

	/** Applies the selected spell to the player with this id. One script action. */
	public static boolean spellOnPlayer(int playerId)
	{
		return perform(() ->
		{
			if (!client().isWidgetSelected()) { return false; }
			Player target = null;
			for (Player player : client().getTopLevelWorldView().players())
			{
				if (player.getId() == playerId) { target = player; break; }
			}
			if (target == null) { return false; }
			client().menuAction(0, 0, MenuAction.WIDGET_TARGET_ON_PLAYER, playerId, -1, "Cast", target.getName());
			return true;
		});
	}

	/** The Wilderness warning interface (upstream {@code Rs2Widget.isWildernessInterfaceOpen}: 475:11). */
	private static final int WILDERNESS_WARNING_GROUP = 475;
	private static final int WILDERNESS_WARNING_CHILD = 11;

	/**
	 * Sends a script-built menu entry (upstream {@code Microbot.doInvoke}) exactly as given: its option,
	 * target, identifier, type, item id and params, as one script action. A {@link MenuAction#WALK} entry
	 * carries the canvas point upstream clicks, so it is a tap there. Refused while the Wilderness warning
	 * is showing, so no entry can confirm it.
	 */
	public static boolean menu(net.runelite.client.plugins.microbot.util.menu.NewMenuEntry entry)
	{
		if (entry == null || entry.getType() == null)
		{
			return false;
		}
		if (entry.getType() == MenuAction.WALK)
		{
			return tap(entry.getParam0(), entry.getParam1());
		}
		return perform(() ->
		{
			Widget warning = client().getWidget(WILDERNESS_WARNING_GROUP, WILDERNESS_WARNING_CHILD);
			if (warning != null && !warning.isHidden())
			{
				return false;
			}
			client().menuAction(entry.getParam0(), entry.getParam1(), entry.getType(), entry.getIdentifier(),
				entry.getItemId(), entry.getOption() == null ? "" : entry.getOption(),
				entry.getTarget() == null ? "" : entry.getTarget());
			return true;
		});
	}

	/** Holds a key down for the script (released by {@link #keyRelease} or when the session ends); one script action. */
	public static boolean keyHold(int keyCode)
	{
		return perform(() -> JavaBridge.onScriptKeyHold(keyCode, permission));
	}

	/**
	 * Releases a key the script holds. Not one of the per-tick actions: a release only ends a hold,
	 * so it goes out even in a busy tick. False when the script holds no such key.
	 */
	public static boolean keyRelease(int keyCode)
	{
		ScriptInputSession owner = permission;
		return owner != null && read(() -> JavaBridge.onScriptKeyRelease(keyCode, owner), false);
	}

	/** A script drag between two canvas points (upstream {@code Microbot.drag}); one script action. */
	public static boolean drag(int x1, int y1, int x2, int y2)
	{
		return perform(() -> JavaBridge.onScriptDrag(x1, y1, x2, y2, permission));
	}

	/** One script tap at a canvas point (upstream mouse clicks); one script action. */
	public static boolean tap(int x, int y)
	{
		return perform(() -> JavaBridge.onScriptClick(x, y, permission));
	}

	/** Walks by tapping {@code destination} on the minimap; false when it is not safely on the minimap. */
	public static boolean walkMinimap(WorldPoint destination)
	{
		return perform(() -> JavaBridge.onScriptMinimapWalk(destination, permission));
	}

	/**
	 * Uses the selected spell or item on a ground item (for example Telekinetic Grab), as one script
	 * action: {@code option} is the menu option ("Cast", "Use"). False when nothing is selected or
	 * the item is not on that tile.
	 */
	public static boolean targetGroundItem(int id, WorldPoint position, String option)
	{
		return perform(() ->
		{
			if (!client().isWidgetSelected()) { return false; }
			WorldView view = client().getTopLevelWorldView();
			if (view == null || position == null || position.getPlane() != view.getPlane()) { return false; }
			int x = position.getX() - view.getBaseX(), y = position.getY() - view.getBaseY();
			Tile[][] tiles = view.getScene().getTiles()[view.getPlane()];
			if (x < 0 || x >= tiles.length || y < 0 || y >= tiles[x].length || tiles[x][y] == null) { return false; }
			List<TileItem> items = tiles[x][y].getGroundItems();
			if (items == null || items.stream().noneMatch(t -> t.getId() == id)) { return false; }
			ItemComposition def = client().getItemDefinition(id);
			client().menuAction(x, y, MenuAction.WIDGET_TARGET_ON_GROUND_ITEM, id, -1, option == null ? "" : option,
				def == null ? "" : def.getName());
			return true;
		});
	}

	public static boolean groundItem(int id, WorldPoint position, String action)
	{
		return perform(() ->
		{
			WorldView view = client().getTopLevelWorldView();
			if (view == null || position == null || position.getPlane() != view.getPlane()) { return false; }
			int x = position.getX() - view.getBaseX(), y = position.getY() - view.getBaseY();
			Tile[][] tiles = view.getScene().getTiles()[view.getPlane()];
			if (x < 0 || x >= tiles.length || y < 0 || y >= tiles[x].length || tiles[x][y] == null) { return false; }
			List<TileItem> items = tiles[x][y].getGroundItems();
			if (items == null || items.stream().noneMatch(t -> t.getId() == id)) { return false; }
			ItemComposition def = client().getItemDefinition(id);
			int op = def != null && "Take".equalsIgnoreCase(action) ? 2 : -1;
			if (op < 0 || op > 4) { return false; }
			client().menuAction(x, y, MenuAction.of(MenuAction.GROUND_ITEM_FIRST_OPTION.getId() + op), id, -1, action, def.getName());
			return true;
		});
	}

	public static boolean key(int keyCode)
	{
		return perform(() -> JavaBridge.onScriptKey(keyCode, permission));
	}

	/** Types one character into the focused game input (one per game tick, like every action). */
	public static boolean type(char character)
	{
		int keyCode = character == '\n' ? java.awt.event.KeyEvent.VK_ENTER
			: java.awt.event.KeyEvent.getExtendedKeyCodeForChar(character);
		if (keyCode == java.awt.event.KeyEvent.VK_UNDEFINED) { return false; }
		return perform(() -> JavaBridge.onScriptKey(keyCode, character, permission));
	}

	private static boolean waitFor(BooleanSupplier condition, int millis)
	{
		if (MicrobotReadServices.current() == null || MicrobotReadServices.current().getClient().isClientThread()) { return false; }
		long deadline = System.nanoTime() + millis * 1_000_000L;
		while (active() && System.nanoTime() < deadline)
		{
			if (condition.getAsBoolean()) { return true; }
			try { Thread.sleep(40); }
			catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
		}
		return false;
	}

	private static int inventoryId(int slot)
	{
		return read(() ->
		{
			ItemContainer inv = client().getItemContainer(InventoryID.INVENTORY);
			if (inv == null || slot < 0 || slot >= inv.getItems().length || inv.getItems()[slot] == null) { return -1; }
			return inv.getItems()[slot].getId();
		}, -1);
	}

	public static boolean itemOnItem(int firstSlot, int secondSlot)
	{
		int firstId = inventoryId(firstSlot), secondId = inventoryId(secondSlot);
		if (firstId < 0 || secondId < 0 || !waitFor(() -> inventory(firstSlot, firstId, "Use"), 1800)) { return false; }
		return waitFor(() -> perform(() ->
		{
			ItemContainer inv = client().getItemContainer(InventoryID.INVENTORY);
			Widget selected = client().getSelectedWidget();
			if (!client().isWidgetSelected() || selected == null || selected.getIndex() != firstSlot || selected.getItemId() != firstId
				|| inv == null || secondSlot < 0 || secondSlot >= inv.getItems().length) { return false; }
			Item item = inv.getItems()[secondSlot];
			if (item == null || item.getId() != secondId) { return false; }
			client().menuAction(secondSlot, WidgetInfo.INVENTORY.getId(), MenuAction.WIDGET_TARGET_ON_WIDGET,
				0, item.getId(), "Use", "");
			return true;
		}), 1800);
	}

	public static boolean itemOnObject(int slot, int objectId)
	{
		int firstId = inventoryId(slot);
		if (firstId < 0 || !waitFor(() -> inventory(slot, firstId, "Use"), 1800)) { return false; }
		return waitFor(() -> perform(() ->
		{
			Widget selected = client().getSelectedWidget();
			if (!client().isWidgetSelected() || selected == null || selected.getIndex() != slot || selected.getItemId() != firstId) { return false; }
			Rs2TileObjectModel model = MicrobotReadServices.require().getRs2TileObjectCache().query().withId(objectId).nearest();
			if (model == null || model.getWorldView() != client().getTopLevelWorldView()
				|| model.getWorldLocation().getPlane() != client().getLocalPlayer().getWorldLocation().getPlane()) { return false; }
			WorldPoint p = model.getWorldLocation();
			WorldView v = client().getTopLevelWorldView();
			client().menuAction(p.getX() - v.getBaseX(), p.getY() - v.getBaseY(), MenuAction.WIDGET_TARGET_ON_GAME_OBJECT,
				objectId, -1, "Use", model.getName());
			return true;
		}), 1800);
	}
}
