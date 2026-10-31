package net.runelite.client.plugins.microbot.dabrutus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/**
 * Quest-style actions, ported from Da-Quester's {@code core.Act} and {@code core.Dialog} (one plugin jar cannot use
 * another's classes): walk, talk with the step's dialogue options, click objects and items, the XP lamp.
 */
final class Act
{
	private Act()
	{
	}

	// ------------------------------------------------------------------ position

	static WorldPoint me()
	{
		return Rs2Player.getWorldLocation();
	}

	static boolean near(WorldPoint p, int d)
	{
		WorldPoint me = me();
		return me != null && p != null && me.getPlane() == p.getPlane() && me.distanceTo(p) <= d;
	}

	/**
	 * True once within {@code d} of {@code p}; otherwise web-walks there (blocking). The bank is closed first: SlugBot's
	 * walker withdraws a teleport item (a Chronicle) when it starts with the bank open and then uses it, which looped
	 * bank, Chronicle, Gillie, bank (user 2026-10-19; the only teleport we want is the cowbell).
	 */
	static boolean walk(WorldPoint p, int d)
	{
		if (near(p, d))
		{
			return true;
		}
		if (Rs2Bank.isOpen())
		{
			Rs2Bank.closeBank();
			Global.sleepUntil(() -> !Rs2Bank.isOpen(), 2_000);
		}
		Rs2Walker.walkTo(p, d);
		return near(p, d);
	}

	// ------------------------------------------------------------------ lookups

	static Rs2NpcModel npc(int... ids)
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getRs2NpcCache().query()
			.where(n -> n.getNpc() != null && !n.getNpc().isDead() && in(n.getId(), ids))
			.nearest()).orElse(null);
	}

	static Rs2TileObjectModel obj(int... ids)
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getRs2TileObjectCache().query()
			.where(o -> in(o.getId(), ids))
			.nearest()).orElse(null);
	}

	static boolean in(int id, int... ids)
	{
		for (int i : ids)
		{
			if (i == id)
			{
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ npcs and objects

	/** Talks to the nearest NPC with {@code npcId}, walking to {@code where} when it is not around. */
	static boolean talk(WorldPoint where, int npcId, String... options)
	{
		expect(options);
		Rs2NpcModel n = npc(npcId);
		if (n == null || n.getWorldLocation() == null || n.getWorldLocation().distanceTo(me()) > 14)
		{
			walk(where, 3);
			return false;
		}
		if (openDoorTo(n.getWorldLocation()) || walkIfBlocked(n.getWorldLocation()))
		{
			return false;
		}
		if (!n.click("Talk-to"))
		{
			return false;
		}
		Global.sleepUntil(Rs2Dialogue::isInDialogue, 6000);
		return true;
	}

	/** Clicks {@code action} on the nearest object with {@code ids}, walking to {@code where} first when it is far. */
	static boolean objAction(WorldPoint where, String action, int... ids)
	{
		Rs2TileObjectModel o = obj(ids);
		if (o == null || o.getWorldLocation() == null || o.getWorldLocation().distanceTo(me()) > 14)
		{
			walk(where, 2);
			return false;
		}
		if (!o.isReachable() && (openDoorTo(o.getWorldLocation()) || walkIfBlocked(o.getWorldLocation())))
		{
			return false;
		}
		boolean ok = action == null ? o.click() : o.click(action);
		if (ok)
		{
			waitArrive();
		}
		return ok;
	}

	/**
	 * Da-Quester's openDoorTo: when {@code target} is close (same floor, 12 tiles) but can't be walked to, opens the
	 * closed door or gate nearest it and returns true (the step is retried next loop). Seth Groats's farmhouse door in
	 * The Ides of Milk (live 2026-10-03) was never opened.
	 */
	static boolean openDoorTo(WorldPoint t)
	{
		WorldPoint me = me();
		if (t == null || me == null || me.getPlane() != t.getPlane() || me.distanceTo(t) > 12
			|| net.runelite.client.plugins.microbot.util.tile.Rs2Tile.isTileReachable(t)
			|| reachableNextTo(t))
		{
			return false;
		}
		List<Rs2TileObjectModel> doors = Microbot.getClientThread().runOnClientThreadOptional(() ->
			Microbot.getRs2TileObjectCache().query().within(t, 7).withNames("Door", "Gate", "Large door").toList())
			.orElse(new ArrayList<>());
		Rs2TileObjectModel best = null;
		for (Rs2TileObjectModel d : doors)
		{
			// Mobile: a door the player can't walk to (say, from inside a fenced pen) is never opened by a click.
			if (d.getPlane() != t.getPlane() || !hasAction(d, "Open") || !reachableAt(d.getWorldLocation()))
			{
				continue;
			}
			if (best == null || d.getWorldLocation().distanceTo(t) < best.getWorldLocation().distanceTo(t))
			{
				best = d;
			}
		}
		if (best == null)
		{
			return false;
		}
		Microbot.log(DaBrutusScript.tag() + "can't reach " + t + "; opening the door at " + best.getWorldLocation());
		if (best.click("Open"))
		{
			waitArrive();
			Global.sleep(600, 1000);
		}
		else
		{
			Microbot.log(DaBrutusScript.tag() + "the door at " + best.getWorldLocation() + " did not take the click");
		}
		return true;
	}

	/**
	 * Mobile: when {@code t} is close but can't be walked to and no reachable door leads to it, walks there with the
	 * web walker, which opens gates and doors on its route. True when it walked (the step is retried next loop).
	 */
	static boolean walkIfBlocked(WorldPoint t)
	{
		WorldPoint me = me();
		if (t == null || me == null || me.getPlane() != t.getPlane() || reachableAt(t))
		{
			return false;
		}
		Microbot.log(DaBrutusScript.tag() + "can't reach " + t + " and no door to open here; walking round");
		Rs2Walker.walkTo(t, 1);
		return true;
	}

	private static boolean reachableAt(WorldPoint t)
	{
		return net.runelite.client.plugins.microbot.util.tile.Rs2Tile.isTileReachable(t) || reachableNextTo(t);
	}

	/** An NPC stands on its tile, which pathing may count as blocked: a free tile next to it is enough. */
	private static boolean reachableNextTo(WorldPoint t)
	{
		int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] o : d)
		{
			if (net.runelite.client.plugins.microbot.util.tile.Rs2Tile.isTileReachable(t.dx(o[0]).dy(o[1])))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean hasAction(Rs2TileObjectModel o, String action)
	{
		net.runelite.api.ObjectComposition c = o.getObjectComposition();
		if (c == null || c.getActions() == null)
		{
			return false;
		}
		for (String a : c.getActions())
		{
			if (action.equalsIgnoreCase(a))
			{
				return true;
			}
		}
		return false;
	}

	/** Clicks an inventory item action ("Drink", "Rub", "Teleport"...). */
	static boolean item(int id, String action)
	{
		if (!Rs2Inventory.hasItem(id))
		{
			return false;
		}
		boolean ok = Rs2Inventory.interact(id, action);
		if (ok)
		{
			Global.sleep(600, 1000);
		}
		return ok;
	}

	static void waitArrive()
	{
		Global.sleepUntil(Rs2Player::isMoving, 1200);
		Global.sleepUntil(() -> !Rs2Player.isMoving(), 15_000);
	}

	// ------------------------------------------------------------------ dialogue

	private static volatile List<String> stepOptions = new ArrayList<>();
	private static final String[] EXITS = {"best get going", "goodbye", "bye", "never mind", "nevermind", "nothing",
		"no thanks", "no, thanks", "that's all", "thank you", "thanks"};
	private static final Map<String, Long> PICKED = new ConcurrentHashMap<>();
	private static int guesses;
	private static long lastGuessAt;

	static void expect(String... options)
	{
		stepOptions = options == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(options));
	}

	static boolean inDialogue()
	{
		return Rs2Dialogue.isInDialogue() || Rs2Dialogue.isInCutScene();
	}

	/**
	 * One dialogue action: the step's options, then {@code always}, then a quest start prompt, then a guess (first option
	 * not picked lately; once a guess was made in this conversation, its way out). As Da-Quester's Dialog.handle.
	 */
	static boolean dialogue(String... always)
	{
		if (Rs2Dialogue.isInCutScene() && !Rs2Dialogue.isInDialogue())
		{
			Global.sleep(600, 900);
			return true;
		}
		if (Rs2Dialogue.hasSelectAnOption())
		{
			Global.sleep(300, 700);
			if (choose(stepOptions) || choose(always == null ? new ArrayList<>() : Arrays.asList(always)))
			{
				Global.sleep(300, 600);
				return true;
			}
			if (Rs2Dialogue.acceptQuestStartDialogue())
			{
				Global.sleep(400, 700);
				return true;
			}
			List<Widget> options = Rs2Dialogue.getDialogueOptions();
			if (!options.isEmpty())
			{
				int pick = fallback(options);
				Microbot.log(DaBrutusScript.tag() + "no scripted option, taking " + (pick + 1) + ": "
					+ text(options.get(pick)));
				Rs2Dialogue.keyPressForDialogueOption(pick + 1);
				Global.sleep(400, 700);
				return true;
			}
			return false;
		}
		if (Rs2Dialogue.hasContinue())
		{
			Global.sleep(250, 900);
			Rs2Dialogue.clickContinue();
			Global.sleep(250, 500);
			return true;
		}
		return false;
	}

	private static boolean choose(List<String> prefs)
	{
		for (boolean exact : new boolean[]{true, false})
		{
			for (String pref : prefs)
			{
				if (pref != null && !pref.isEmpty() && Rs2Dialogue.hasDialogueOption(pref, exact))
				{
					return Rs2Dialogue.clickOption(pref, exact);
				}
			}
		}
		return false;
	}

	private static int fallback(List<Widget> options)
	{
		long now = System.currentTimeMillis();
		if (now - lastGuessAt > 45_000)
		{
			guesses = 0;
		}
		PICKED.entrySet().removeIf(e -> now - e.getValue() > 5 * 60_000L);
		int exit = -1;
		int fresh = -1;
		boolean seen = false;
		for (int i = 0; i < options.size(); i++)
		{
			String t = text(options.get(i));
			boolean isExit = isExit(t);
			if (exit < 0 && isExit)
			{
				exit = i;
			}
			if (PICKED.containsKey(t))
			{
				seen = true;
			}
			else if (fresh < 0 && !isExit)
			{
				fresh = i;
			}
		}
		int pick = exit >= 0 && (guesses > 0 || seen || fresh < 0) ? exit : fresh >= 0 ? fresh : 0;
		guesses++;
		lastGuessAt = now;
		PICKED.put(text(options.get(pick)), now);
		return pick;
	}

	private static boolean isExit(String t)
	{
		String l = t.toLowerCase();
		for (String e : EXITS)
		{
			if (l.contains(e))
			{
				return true;
			}
		}
		return false;
	}

	private static String text(Widget w)
	{
		return w == null || w.getText() == null ? "" : w.getText().replaceAll("<[^>]*>", "").trim();
	}

	// ------------------------------------------------------------------ xp lamp (Da-Quester's XpReward)

	static boolean lampOpen()
	{
		return Rs2Widget.isWidgetVisible(InterfaceID.Xpreward.UNIVERSE);
	}

	/** Picks {@code skill} on the open "choose a skill" interface and confirms. */
	static boolean chooseLampSkill(Skill skill)
	{
		int button;
		switch (skill)
		{
			case STRENGTH:
				button = InterfaceID.Xpreward.STRENGTH;
				break;
			case DEFENCE:
				button = InterfaceID.Xpreward.DEFENCE;
				break;
			case HITPOINTS:
				button = InterfaceID.Xpreward.HITPOINTS;
				break;
			case RANGED:
				button = InterfaceID.Xpreward.RANGED;
				break;
			case MAGIC:
				button = InterfaceID.Xpreward.MAGIC;
				break;
			case PRAYER:
				button = InterfaceID.Xpreward.PRAYER;
				break;
			default:
				button = InterfaceID.Xpreward.ATTACK;
				break;
		}
		if (!lampOpen())
		{
			return false;
		}
		Global.sleep(500, 1100);
		Rs2Widget.clickWidget(button);
		Global.sleep(500, 900);
		Rs2Widget.clickWidget(InterfaceID.Xpreward.CONFIRM);
		return Global.sleepUntil(() -> !lampOpen(), 3000);
	}
}
