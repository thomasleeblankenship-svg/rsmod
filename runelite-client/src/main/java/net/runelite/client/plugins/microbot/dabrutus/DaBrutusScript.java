package net.runelite.client.plugins.microbot.dabrutus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID1;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.models.RS2Item;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Da-Brutus: kills Brutus, the cow boss in Lumbridge's cow field (research: wiki Brutus + Brutus/Strategies, and the
 * KSP Mad Cow, Niri Brutus and osrsx-brutus scripts).
 *
 * <p>Every loop works out what to do from the game, so a restart anywhere carries on:
 * <ul>
 *   <li>The Ides of Milk not done: do it ({@link IdesOfMilk}), the Bull fight included.</li>
 *   <li>In the pen: dodge his Charge and Slam ({@link BrutusEvents} plans the tile when he growls/snorts, a fast thread
 *   clicks it), eat, Protect from Melee, attack. Between kills: ring the cowbell, bury bones, loot, rotate the style.
 *   Out of food or room: Quick-escape.</li>
 *   <li>Outside: Lumbridge bank (food, cowbell, air runes), the church altar for Prayer, charge the cowbell, teleport or
 *   walk to the pen, Release him.</li>
 * </ul>
 * One game action per loop.
 */
@Slf4j
@Singleton
public class DaBrutusScript extends Script
{
	static final int[] BRUTUS_IDS = {NpcID.COWBOSS, NpcID.COWBOSS_ROUTEFIND};
	static final int[] PEN_GATES = {ObjectID1.FENCEGATE_L_COWBOSS_START, ObjectID1.FENCEGATE_R_COWBOSS_START};
	static final int[] EXIT_GATES = {ObjectID1.FENCEGATE_L_COWBOSS_EXIT, ObjectID1.FENCEGATE_R_COWBOSS_EXIT};
	static final WorldPoint PEN = new WorldPoint(3262, 3292, 0);
	static final WorldPoint ALTAR = new WorldPoint(3243, 3207, 0);
	static final WorldPoint LUMBRIDGE_BANK = BankLocation.LUMBRIDGE_TOP.getWorldPoint();
	static final WorldPoint GE = BankLocation.GRAND_EXCHANGE.getWorldPoint();
	static final int ALTAR_ID = 409;
	static final int AIR_RUNE = ItemID.AIRRUNE;
	static final int COWBELL = ItemID.COWBELL_AMULET;
	static final int COWBELL_EMPTY = ItemID.COWBELL_AMULET_EMPTY;
	static final int MOOLETA = ItemID.MOOLETA;
	static final int LAMP = ItemID.COWBOSS_REWARD_LAMP;
	static final int COINS = ItemID.COINS;
	private static final int LOOP_MS = 300;

	private DaBrutusConfig config;
	private IdesOfMilk quest;
	/** 0.6.0: the setup window's choices (null until Start) and this account's persona. */
	private PersonaStore store;
	private volatile Setup setup;
	private volatile Persona persona;
	private boolean goalNoted;
	private ScheduledFuture<?> dodgeFuture;
	@Getter
	private volatile String status = "starting";
	@Getter
	private volatile long startedMs;
	@Getter
	private volatile int lootPicked;
	/** For the paint: loot picked up this session (GE guide prices), kill durations, kills since the last bank. */
	@Getter
	private volatile long lootGp;
	@Getter
	private volatile int tripKills;
	/** The trip being made: 1 at the start, +1 each time it leaves the pen for the bank (user 2026-10-09). */
	@Getter
	private volatile int trip = 1;
	private final List<Long> killTimes = new java.util.concurrent.CopyOnWriteArrayList<>();
	private volatile long fightStartMs;
	private final java.util.Map<Skill, Integer> startXp = new java.util.concurrent.ConcurrentHashMap<>();
	private final StyleBlocks blocks = new StyleBlocks();
	/** GE shopping (Da-Scurrius's GeBuyer): the list being bought, or null. */
	private final GeBuyer buyer = new GeBuyer(m -> Microbot.log(tag() + m));
	private final LootSeller seller = new LootSeller(m -> Microbot.log(tag() + m));
	/** Selling loot for food money (see {@link #startLootSale}). */
	private volatile boolean selling;
	private long lastSaleMs;
	private volatile List<GeBuyer.Order> orders;
	/** Items the GE did not fill {@link GeBuyer#MAX_OFFERS} times: not ordered again this run. */
	private final Set<String> geGaveUp = new HashSet<>();
	/** When a GE plan last could not buy the runes the level's best spell needs: no leaving for them for 10 min. */
	private volatile long runeBuyFailedMs;
	/** Coins when the last plan could afford nothing: not planned again until the coins or {@link #geNeeds} change. */
	private long geSkipCoins = -1;
	/** {@link #geNeeds} when that plan found nothing. */
	private String geSkipNeeds = "";
	/** The last GE plan left something out because the coins did not cover it (see {@link #noGold}). */
	private boolean shortOfGold;
	/** All coins (bank and bag) of the GE plan being made: the buy sizes scale with them (see {@link GeBuyer#byGold}). */
	private long planCoins;
	/** GE batch sizes by the coins (user 2026-10-09): food 150-600, arrows 1000-3000. */
	static final int FOOD_MIN = 150, FOOD_MAX = 600, ARROWS_MIN = 1000, ARROWS_MAX = 3000;
	/** Whether that plan included food: a gear-only plan that found nothing must not stop the food plan. */
	private boolean geSkipFood;
	/** Armour that failed to wear (a quest lock): skipped. */
	private final Set<String> cannotWear = new HashSet<>();
	/** Leaving the pen (Quick-escape) and why. */
	private String leaving;
	private long leaveSinceMs;
	/** Banked this trip: the next stop is the pen. */
	private boolean stocked;
	/** Bank had no cowbell last visit: get one from Gillie. */
	private boolean cowbellMissing;
	private int gillieTries;
	private boolean ringPending;
	/** The last cowbell teleport did not move us: charge it at the next chance. */
	private volatile boolean bellFailed;
	/** The last cowbell try at the GE: once a minute at most, then walk. */
	private long geTeleportMs;
	/** Cowbell teleports that did not move us this run (3: walk from then on); the "walking instead" note was logged. */
	private int teleportFails;
	private boolean teleportNote;
	/** Food in the bag when the current kill started (-1: none yet), and the food the last kills took (newest last). */
	private int fightStartFood = -1;
	private final java.util.Deque<Integer> foodPerKill = new java.util.ArrayDeque<>();
	private int lastKills;
	private long lastAttackMs;
	private long lastEatMs;
	/** The special the fast thread last acted on. */
	private BrutusEvents.Special handled;
	private final Set<String> lootMisses = new HashSet<>();
	/** Bank withdraws already tried this visit. */
	private final Set<String> bankTried = new HashSet<>();
	private int lootTries;
	private String lastLootKey;
	static volatile boolean inPenCached;
	private static volatile String playerName;

	/** One click at a time between the dodge thread and the main loop. */
	static final Object INPUT = new Object();

	// ------------------------------------------------------------------ log

	static String tag()
	{
		String n = playerName;
		return n == null ? "[Da-Brutus] " : "[Da-Brutus] [" + n + "] ";
	}

	void status(String s)
	{
		if (playerName == null && Microbot.isLoggedIn())
		{
			playerName = Microbot.getClientThread().runOnClientThreadOptional(() ->
				Microbot.getClient().getLocalPlayer() == null ? null : Microbot.getClient().getLocalPlayer().getName())
				.orElse(null);
		}
		if (!s.equals(status))
		{
			Microbot.log(tag() + s);
		}
		status = s;
		Microbot.status = s;
	}

	// ------------------------------------------------------------------ lifecycle

	public boolean run(DaBrutusConfig config)
	{
		this.config = config;
		this.quest = new IdesOfMilk(this);
		startedMs = System.currentTimeMillis();
		lootPicked = 0;
		lootGp = 0;
		tripKills = 0;
		trip = 1;
		killTimes.clear();
		fightStartMs = 0;
		startXp.clear();
		blocks.reset();
		orders = null;
		geGaveUp.clear();
		runeBuyFailedMs = 0;
		geSkipCoins = -1;
		cannotWear.clear();
		leaving = null;
		stocked = false;
		cowbellMissing = false;
		gillieTries = 0;
		ringPending = false;
		bellFailed = false;
		teleportFails = 0;
		teleportNote = false;
		fightStartFood = -1;
		foodPerKill.clear();
		lastWorn = -1;
		lowWornSinceMs = 0;
		handled = null;
		lootMisses.clear();
		BrutusEvents.reset();
		lastKills = 0;
		setup = null;
		persona = null;
		RangedGear.lag = GearLag.NONE;
		MagicGear.lag = GearLag.NONE;
		goalNoted = false;
		Microbot.enableAutoRunOn = true;
		mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
		{
			try
			{
				if (!Microbot.isLoggedIn() || !super.run() || !setupReady())
				{
					return;
				}
				BrutusEvents.slamStandBack = config.slamWait();
				BrutusEvents.ranged = ranged() || magic(); // both fight from range: keep the 2-tile gap
				LootSeller.keepArrows = ranged();
				LootSeller.keepRunes = magic();
				loop();
			}
			catch (Throwable ex)
			{
				log.error(tag() + "loop error in '{}'", status, ex);
				Microbot.log(tag() + "loop error in '" + status + "': " + ex);
			}
		}, 0, LOOP_MS, TimeUnit.MILLISECONDS);
		// Dodges on their own fast thread: the main loop can be busy eating or looting when he growls.
		dodgeFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
		{
			try
			{
				if (Microbot.isLoggedIn() && isRunning() && inPenCached)
				{
					dodge();
				}
			}
			catch (Throwable ex)
			{
				log.error(tag() + "dodge thread error", ex);
			}
		}, 0, 40, TimeUnit.MILLISECONDS);
		return true;
	}

	@Override
	public void shutdown()
	{
		if (dodgeFuture != null)
		{
			dodgeFuture.cancel(false);
		}
		if (Microbot.isLoggedIn() && !inPenCached && Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE))
		{
			Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
		}
		inPenCached = false;
		super.shutdown();
	}

	void store(PersonaStore store)
	{
		this.store = store;
	}

	// ------------------------------------------------------------------ setup window and persona (0.6.0)

	/**
	 * Mobile: the setup comes from the "Goal and persona" settings (Android has no Swing window), once the account is
	 * known so its persona can be made. Saved like the window's choices, so a desktop window opens with them.
	 */
	private boolean setupReady()
	{
		if (setup != null)
		{
			return true;
		}
		if (store == null)
		{
			begin(Setup.fromConfig(config), Persona.of(0, 0));
			return true;
		}
		if (!store.ready())
		{
			status("waiting for the account profile");
			return false;
		}
		Setup s = Setup.fromConfig(config);
		store.saveSetup(s);
		begin(s, store.persona(Microbot.getClient().getAccountHash()));
		return true;
	}

	/** The choices take effect. */
	private void begin(Setup s, Persona p)
	{
		persona = p;
		blocks.reset();
		blocks.configure(s.personaStrength == 0 ? null : p.styleOrder(), p.styleSlack(s.personaStrength), s.meleeTargets());
		GearLag lag = GearLag.of(p, s.personaStrength);
		RangedGear.lag = lag;
		MagicGear.lag = lag;
		setup = s;
		Microbot.log(tag() + "persona " + p.name + " (day " + p.day + ", strength " + s.personaStrength + "%), goal "
			+ goalText() + ", then " + s.whenReached.label.toLowerCase() + "; lamp " + lampSkill().getName());
	}

	/** Food per trip: the setting, moved by this account's share and this trip's roll ({@link Amounts}). */
	int foodPerTrip()
	{
		return perTrip(Amounts.Kind.FOOD, config.foodAmount(), 1, 26);
	}

	/** Arrows worn when leaving the bank, per account and trip. */
	int arrowsPerTrip()
	{
		return perTrip(Amounts.Kind.ARROWS, config.arrowsPerTrip(), 50, 5000);
	}

	/** Casts of runes taken each trip, per account and trip. */
	int castsPerTrip()
	{
		return perTrip(Amounts.Kind.CASTS, config.castsPerTrip(), 50, 5000);
	}

	private int perTrip(Amounts.Kind kind, int base, int min, int max)
	{
		Persona p = persona;
		Setup s = setup;
		if (p == null || s == null)
		{
			return base;
		}
		return Amounts.perTrip(base, Amounts.factor(p.seed, kind, false), Amounts.roll(p.seed, trip, kind, false), kind,
			s.personaStrength, p.trait(Persona.Trait.TIDINESS) >= 0.5, min, max);
	}

	/** A GE batch of {@code base} (the gold-scaled size or a setting), per account and trip; at least {@code min}. */
	private int geBatch(Amounts.Kind kind, int base, int min)
	{
		Persona p = persona;
		Setup s = setup;
		if (p == null || s == null)
		{
			return base;
		}
		return Amounts.batch(base, Amounts.factor(p.seed, kind, true), Amounts.roll(p.seed, trip, kind, true), kind,
			s.personaStrength, p.trait(Persona.Trait.TIDINESS) >= 0.5, min);
	}

	/** The paint's persona name, or null before Start. */
	String personaName()
	{
		Persona p = persona;
		return setup == null || p == null ? null : p.name;
	}

	/** The lamp skill: the one picked in the setup window, else the persona's. */
	private Skill lampSkill()
	{
		Setup s = setup;
		Persona p = persona;
		if (s != null && s.lamp.skill != null)
		{
			return s.lamp.skill;
		}
		return p == null ? Skill.ATTACK : p.lampSkill();
	}

	private boolean goalReached()
	{
		Setup s = setup;
		return s != null && s.reached(config.combatMode(), Rs2Player.getRealSkillLevel(Skill.ATTACK),
			Rs2Player.getRealSkillLevel(Skill.STRENGTH), Rs2Player.getRealSkillLevel(Skill.DEFENCE),
			Rs2Player.getRealSkillLevel(Skill.RANGED), Rs2Player.getRealSkillLevel(Skill.MAGIC));
	}

	/** Whether reaching the goal ends the run (not "Keep farming loot"). */
	private boolean goalEndsRun()
	{
		Setup s = setup;
		return s != null && s.whenReached != Setup.WhenReached.KEEP_GOING && goalReached();
	}

	private String goalText()
	{
		Setup s = setup;
		if (s == null)
		{
			return "none";
		}
		switch (config.combatMode())
		{
			case RANGED:
				return "Ranged " + s.ranged;
			case MAGIC:
				return "Magic " + s.magic;
			default:
				return s.attack + "/" + s.strength + "/" + s.defence + " melee";
		}
	}

	/** Out of the pen with the goal reached: log out or stop, as chosen. */
	private void finishGoal()
	{
		if (setup.whenReached == Setup.WhenReached.LOG_OUT)
		{
			status("goal reached (" + goalText() + "): logging out");
			Rs2Player.logout();
		}
		stop("goal reached (" + goalText() + ")");
	}

	private void stop(String why)
	{
		status("stopped: " + why);
		Microbot.showMessage("Da-Brutus stopped: " + why);
		shutdown();
	}

	// ------------------------------------------------------------------ main loop

	// ------------------------------------------------------------------ death and grave (as Da-Scurrius)

	/** Touch this file to stop every running copy (hot-load practice). */
	static final java.nio.file.Path STOP_FILE = java.nio.file.Paths.get(System.getProperty("user.home"), ".runelite",
		"da-brutus.stop");
	/** Grave lasts 15 minutes; a little less so we never walk to an empty spot. */
	static final long GRAVE_MS = 14 * 60_000L;
	private long noGraveSinceMs;

	/** Per account: written on death, so the grave is looted even after a restart. */
	static java.nio.file.Path graveFile()
	{
		net.runelite.api.Player p = Microbot.getClient().getLocalPlayer();
		String name = p == null || p.getName() == null ? "unknown" : p.getName().replaceAll("[^A-Za-z0-9_ -]", "_");
		return java.nio.file.Paths.get(System.getProperty("user.home"), ".runelite", "da-brutus.grave-" + name);
	}

	/** Client thread, on "Oh dear, you are dead!". */
	static void markDeath()
	{
		markDeath(System.currentTimeMillis(), "");
	}

	/** The grave file for a death at {@code atMs}: its content and mtime (the reclaim window runs from the mtime). */
	static void markDeath(long atMs, String how)
	{
		try
		{
			java.nio.file.Path f = graveFile();
			java.nio.file.Files.write(f, String.valueOf(atMs).getBytes());
			java.nio.file.Files.setLastModifiedTime(f, java.nio.file.attribute.FileTime.fromMillis(atMs));
			Microbot.log(tag() + "died" + how + "; going back for the grave (" + f.getFileName() + ")");
		}
		catch (Exception e)
		{
			Microbot.log(tag() + "died" + how + "; could not write the grave file: " + e.getMessage());
		}
	}

	// ------------------------------------------------------------------ death without the message (as Da-Scurrius 0.15.5)

	static final EquipmentInventorySlot[] GEAR_SLOTS = {EquipmentInventorySlot.HEAD, EquipmentInventorySlot.CAPE,
		EquipmentInventorySlot.AMULET, EquipmentInventorySlot.WEAPON, EquipmentInventorySlot.BODY,
		EquipmentInventorySlot.SHIELD, EquipmentInventorySlot.LEGS, EquipmentInventorySlot.GLOVES,
		EquipmentInventorySlot.BOOTS, EquipmentInventorySlot.RING};
	/** Pieces worn at the last look (-1: not looked yet this run) and when that was, and since when 0-1 are worn. */
	private int lastWorn = -1;
	private long lastWornMs;
	private long lowWornSinceMs;
	private long gearSavedMs;

	/** Per account: pieces worn, with the last time they were seen as mtime, so a death while unloaded is noticed. */
	static java.nio.file.Path gearFile()
	{
		java.nio.file.Path grave = graveFile();
		return grave.resolveSibling(grave.getFileName().toString().replace("da-brutus.grave-", "da-brutus.gear-"));
	}

	static int wornCount()
	{
		int n = 0;
		for (EquipmentInventorySlot s : GEAR_SLOTS)
		{
			n += Rs2Equipment.get(s) != null ? 1 : 0;
		}
		return n;
	}

	/**
	 * A death the chat message missed: most worn gear (3+ pieces down to 0-1) gone outside the bank, for 3 s (the
	 * equipment can read empty for a moment after a login). Live 12:30: the plugin was unloaded mid-fight, the account
	 * died, and with no message seen there was no grave file; the saved count covers that. Death time = gear last seen.
	 */
	private void watchGear()
	{
		long now = System.currentTimeMillis();
		if (lastWorn < 0)
		{
			lastWornMs = now;
			java.nio.file.Path f = gearFile();
			try
			{
				if (java.nio.file.Files.exists(f) && now - java.nio.file.Files.getLastModifiedTime(f).toMillis() < GRAVE_MS)
				{
					lastWorn = Integer.parseInt(new String(java.nio.file.Files.readAllBytes(f)).trim());
					lastWornMs = java.nio.file.Files.getLastModifiedTime(f).toMillis();
				}
			}
			catch (Exception ignored)
			{
			}
		}
		int worn = wornCount();
		if (lastWorn >= 3 && worn <= 1 && !Rs2Bank.isOpen() && !java.nio.file.Files.exists(graveFile()))
		{
			lowWornSinceMs = lowWornSinceMs == 0 ? now : lowWornSinceMs;
			if (now - lowWornSinceMs < 3_000)
			{
				return; // keep the baseline until it is sure
			}
			markDeath(lastWornMs, " (" + lastWorn + " worn pieces down to " + worn + " without a death message)");
		}
		lowWornSinceMs = 0;
		if (worn != lastWorn || now - gearSavedMs > 30_000)
		{
			try
			{
				java.nio.file.Files.write(gearFile(), String.valueOf(worn).getBytes());
			}
			catch (java.io.IOException ignored)
			{
			}
			gearSavedMs = now;
		}
		lastWorn = worn;
		lastWornMs = now;
	}

	private boolean graveDue()
	{
		java.nio.file.Path f = graveFile();
		if (!java.nio.file.Files.exists(f))
		{
			return false;
		}
		try
		{
			if (System.currentTimeMillis() - java.nio.file.Files.getLastModifiedTime(f).toMillis() < GRAVE_MS)
			{
				return true;
			}
			java.nio.file.Files.deleteIfExists(f);
		}
		catch (java.io.IOException ignored)
		{
		}
		Microbot.log(tag() + "the grave ran out; carrying on from the bank");
		stocked = false;
		return false;
	}

	private void clearGrave()
	{
		try
		{
			java.nio.file.Files.deleteIfExists(graveFile());
		}
		catch (java.io.IOException ignored)
		{
		}
		noGraveSinceMs = 0;
		stocked = false;
		leaving = null;
	}

	/** Gear back from the grave is in the bag: wear it on the spot (user 2026-10-05), not bank it and take it out again. */
	private boolean regear;
	private final Set<String> regearTried = new HashSet<>();

	/**
	 * One piece of gear from the bag back on per call: anything with Wield/Wear/Equip except the cowbell (kept in the bag
	 * to teleport and ring). Each name is tried once; false (and re-gearing ends) when nothing is left, so the bank step
	 * then banks only what did not go on (before, needsBank saw it all as spare gear: bank, withdraw, wear again).
	 */
	private boolean wearFromBag()
	{
		Rs2ItemModel it = Rs2Inventory.get(i -> i != null && i.getName() != null && !regearTried.contains(i.getName())
			&& i.getId() != COWBELL && i.getId() != COWBELL_EMPTY && wearAction(i) != null);
		if (it == null)
		{
			regear = false;
			Microbot.log(tag() + "grave gear back on (" + wornCount() + " pieces worn)");
			return false;
		}
		regearTried.add(it.getName());
		status("putting " + it.getName() + " back on");
		int id = it.getId();
		Rs2Inventory.interact(it, wearAction(it));
		Global.sleepUntil(() -> !Rs2Inventory.hasItem(id), 1_500);
		return true;
	}

	private static String wearAction(Rs2ItemModel i)
	{
		String[] actions = i.getInventoryActions();
		for (String want : new String[]{"Wield", "Wear", "Equip"})
		{
			for (String a : actions == null ? new String[0] : actions)
			{
				if (want.equalsIgnoreCase(a))
				{
					return a;
				}
			}
		}
		return null;
	}

	/** The grave from a death in the pen sits outside the instance, by the pen gate: walk there and Loot it. */
	private void reclaimGrave()
	{
		if (Rs2Dialogue.hasContinue())
		{
			Rs2Dialogue.clickContinue();
			return;
		}
		if (inPen())
		{
			// Died elsewhere and came back in? Leave first: the grave is outside.
			if (leaving == null)
			{
				leave("grave outside"); // also starts the one-minute timer (a stale one stopped the script at once)
			}
			quickEscape();
			return;
		}
		var grave = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getRs2NpcCache().query()
			.where(n -> n.getNpc() != null && n.getNpc().getName() != null
				&& n.getNpc().getName().toLowerCase(Locale.ROOT).contains("grave"))
			.nearest()).orElse(null);
		if (grave == null)
		{
			if (!Act.near(PEN, 6))
			{
				status("grave: walking to Brutus's pen");
				Act.walk(PEN, 3);
				return;
			}
			long now = System.currentTimeMillis();
			noGraveSinceMs = noGraveSinceMs == 0 ? now : noGraveSinceMs;
			if (now - noGraveSinceMs > 20_000)
			{
				Microbot.log(tag() + "grave: none by the pen after 20 s; carrying on from the bank");
				clearGrave();
				return;
			}
			status("grave: looking by the pen");
			Global.sleep(2_000);
			return;
		}
		noGraveSinceMs = 0;
		status("grave: looting");
		Rs2Npc.interact(new net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel(grave.getNpc()), "Loot");
		Global.sleep(2_500, 3_500);
		boolean gone = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getRs2NpcCache().query()
			.where(n -> n.getNpc() != null && n.getNpc().getName() != null
				&& n.getNpc().getName().toLowerCase(Locale.ROOT).contains("grave")).nearest() == null).orElse(false);
		if (gone || Rs2Equipment.get(EquipmentInventorySlot.WEAPON) != null)
		{
			clearGrave();
			Microbot.log(tag() + "grave reclaimed; putting the gear back on");
			regear = true;
			regearTried.clear();
			status("grave reclaimed; banking");
		}
	}

	private void loop()
	{
		if (java.nio.file.Files.exists(STOP_FILE))
		{
			try
			{
				java.nio.file.Files.deleteIfExists(STOP_FILE);
			}
			catch (java.io.IOException ignored)
			{
			}
			stop("stop file " + STOP_FILE);
			return;
		}
		watchGear();
		if (graveDue())
		{
			inPenCached = false;
			reclaimGrave();
			return;
		}
		if (Rs2Inventory.hasItem(LAMP) || Act.lampOpen())
		{
			useLamp();
			return;
		}
		boolean inPen = inPen();
		inPenCached = inPen;
		if (inPen)
		{
			// A new special freezes everything else until the dodge is done.
			if (dodging())
			{
				return;
			}
			if (Act.inDialogue())
			{
				answerDialogue();
				return;
			}
			pen(!IdesOfMilk.done());
			return;
		}
		if (leaving != null)
		{
			// Out of the pen: the leave is done even when Quick-escape took longer than quickEscape() waited. A leave left
			// set made the next trip escape again on entry and stop as "could not leave for a minute" (live 13:24, soviet).
			leftPen();
		}
		if (Act.inDialogue())
		{
			answerDialogue();
			return;
		}
		if (!IdesOfMilk.done())
		{
			if (!config.doQuest())
			{
				stop("The Ides of Milk is not done (turn on 'Do The Ides of Milk')");
				return;
			}
			quest.step();
			return;
		}
		if (config.maxKills() > 0 && BrutusEvents.kills >= config.maxKills())
		{
			stop(BrutusEvents.kills + " kills done");
			return;
		}
		if (goalEndsRun())
		{
			finishGoal();
			return;
		}
		if (!goalNoted && goalReached())
		{
			goalNoted = true;
			Microbot.log(tag() + "goal reached (" + goalText() + "); farming on as chosen");
		}
		outside();
	}

	/** The pen-entry warning ("Yes, and don't ask again"), quest talk, anything else: Da-Quester's handling. */
	private void answerDialogue()
	{
		if (Rs2Dialogue.hasSelectAnOption() && Rs2Dialogue.hasDialogueOption("don't ask again", false))
		{
			Rs2Dialogue.clickOption("don't ask again", false);
			return;
		}
		Act.dialogue(IdesOfMilk.OPTIONS);
	}

	private void useLamp()
	{
		if (Act.lampOpen())
		{
			status("lamp: " + lampSkill().getName());
			Act.chooseLampSkill(lampSkill());
			return;
		}
		// The cow lamp can ask in a chat menu instead of the skill grid (live 09:55: all three kept re-rubbing it while
		// the menu sat open). Answer the menu: the skill, else "More"/"Next" to page, else continue.
		String skill = lampSkill().getName();
		if (Rs2Dialogue.hasSelectAnOption())
		{
			status("lamp: choosing " + skill);
			if (Rs2Dialogue.hasDialogueOption(skill, false))
			{
				Rs2Dialogue.clickOption(skill, false);
			}
			else if (!Rs2Dialogue.clickOption("More", false) && !Rs2Dialogue.clickOption("Next", false))
			{
				Act.dialogue("Yes", "Confirm");
			}
			Global.sleep(600, 1000);
			return;
		}
		if (Rs2Dialogue.hasContinue())
		{
			Rs2Dialogue.clickContinue();
			Global.sleep(400, 700);
			return;
		}
		if (Rs2Bank.isOpen())
		{
			Rs2Bank.closeBank();
			return;
		}
		if (!Rs2Inventory.hasItem(LAMP))
		{
			return;
		}
		status("rubbing the xp lamp");
		Act.item(LAMP, "Rub");
		Global.sleepUntil(() -> Act.lampOpen() || Rs2Dialogue.isInDialogue(), 3000);
	}

	// ------------------------------------------------------------------ where are we

	/** In Brutus's pen: an instance with the Quick-escape gate or Brutus himself in it. */
	static boolean inPen()
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			var wv = Microbot.getClient().getTopLevelWorldView();
			if (wv == null || !wv.isInstance())
			{
				return false;
			}
			return Microbot.getRs2NpcCache().query().where(n -> Act.in(n.getId(), BRUTUS_IDS)).nearest() != null
				|| Microbot.getRs2TileObjectCache().query().where(o -> Act.in(o.getId(), EXIT_GATES)).nearest() != null;
		}).orElse(false);
	}

	/** Alive Brutus, client-thread read: npc, or null. */
	private static NPC boss()
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			var m = Microbot.getRs2NpcCache().query()
				.where(n -> Act.in(n.getId(), BRUTUS_IDS) && n.getNpc() != null && !n.getNpc().isDead()
					&& n.getNpc().getAnimation() != BrutusEvents.ANIM_DEATH
					// A despawning NPC has no composition: attacking it errored (live 10:0x, "NPCComposition is null").
					&& n.getNpc().getName() != null)
				.nearest();
			return m == null ? null : m.getNpc();
		}).orElse(null);
	}

	// ------------------------------------------------------------------ dodging

	/** True while a special is being dodged (the main loop stays off the mouse). */
	private boolean dodging()
	{
		BrutusEvents.Special s = BrutusEvents.latest;
		if (s == null)
		{
			return false;
		}
		return System.currentTimeMillis() < holdUntil(s);
	}

	/** End of the dodge window: Charge 3 ticks; Slam 4 ticks then the slams (until impacts stop) when staying back. */
	private long holdUntil(BrutusEvents.Special s)
	{
		if (s.kind == BrutusEvents.Kind.CHARGE)
		{
			return s.atMs + 2_400;
		}
		long base = s.atMs + 3_000;
		if (!config.slamWait() && !ranged())
		{
			return base;
		}
		long lastSlam = Math.max(BrutusEvents.lastSlamImpactMs, BrutusEvents.lastSlamAnimMs);
		long afterSlams = lastSlam > s.atMs ? lastSlam + 1_500 : base + 1_800;
		return Math.min(s.atMs + 8_000, Math.max(base, afterSlams));
	}

	/** Fast thread: clicks the planned dodge tile once (twice when the first click did not move us). */
	private void dodge()
	{
		BrutusEvents.Special s = BrutusEvents.latest;
		long now = System.currentTimeMillis();
		if (s == null || now > holdUntil(s) || s.to == null || s.alreadySafe())
		{
			if (s != null && s != handled && (s.to == null || s.alreadySafe()))
			{
				handled = s;
				if (s.to == null)
				{
					Microbot.log(tag() + "no safe tile for " + s.kind + ", tanking it");
				}
			}
			return;
		}
		LocalPoint me = Rs2Player.getLocalLocation();
		if (me == null)
		{
			return;
		}
		boolean there = me.getSceneX() == s.to[0] && me.getSceneY() == s.to[1];
		if (there)
		{
			handled = s;
			return;
		}
		boolean retry = s.clicked && now - s.atMs > 1_000 && !Rs2Player.isMoving() && s.clicks < 2;
		if (s.clicked && !retry)
		{
			return;
		}
		synchronized (INPUT)
		{
			if (!Rs2Player.isRunEnabled())
			{
				Rs2Player.toggleRunEnergy(true);
			}
			Rs2Walker.walkFastLocal(me.plus((s.to[0] - me.getSceneX()) * 128, (s.to[1] - me.getSceneY()) * 128));
			s.clicked = true;
			s.clicks++;
		}
		handled = s;
		status("dodging " + (s.kind == BrutusEvents.Kind.CHARGE ? "charge" : "slam"));
	}

	// ------------------------------------------------------------------ in the pen

	/** In the pen. {@code questFight}: the quest's Bull fight (no looting, no leaving for supplies). */
	private void pen(boolean questFight)
	{
		NPC npc = boss();
		boolean alive = npc != null;
		int hp = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = Math.max(1, Rs2Player.getRealSkillLevel(Skill.HITPOINTS));
		int food = food();

		if (alive && fightStartMs == 0)
		{
			fightStartMs = System.currentTimeMillis();
			fightStartFood = food;
		}
		if (BrutusEvents.kills != lastKills)
		{
			if (fightStartMs > 0 && BrutusEvents.kills > lastKills)
			{
				killTimes.add(System.currentTimeMillis() - fightStartMs);
				tripKills++;
				if (fightStartFood >= 0 && !questFight)
				{
					foodPerKill.addLast(Math.max(0, fightStartFood - food));
					while (foodPerKill.size() > 5)
					{
						foodPerKill.pollFirst();
					}
				}
			}
			fightStartFood = -1;
			fightStartMs = 0;
			lastKills = BrutusEvents.kills;
			ringPending = config.ringCowbell() && hasCowbell();
			ringKillMs = System.currentTimeMillis();
			ringTries = 0;
			stocked = false;
		}

		if (hp * 100 / maxHp <= config.eatAtPercent() && food > 0 && eat())
		{
			return;
		}

		if (leaving == null)
		{
			if (food <= config.leaveFood() && hp * 100 / maxHp <= config.eatAtPercent() && !questFight)
			{
				leave("out of food");
			}
			else if (food == 0 && hp <= 20)
			{
				leave("no food and " + hp + " HP");
			}
			else if (ranged() && !questFight && wornBow() != null && arrowsCarried() == 0)
			{
				leave("out of arrows");
			}
			else if (ranged() && !questFight && wornBow() != null && BrutusEvents.noAmmoRecent(15_000) >= 3)
			{
				// The game says the bow has nothing to fire although the counts say otherwise (wrong arrows, or a stale
				// read): leave and restock rather than standing there clicking Attack.
				leave("out of arrows (game: no ammo the bow can fire)");
			}
			else if (magic() && !questFight && staffWorn() && castable() == null)
			{
				leave("out of runes");
			}
			else if (magic() && !questFight && staffWorn() && BrutusEvents.noRunesRecent(15_000) >= 3)
			{
				leave("out of runes (game: not enough runes to cast)");
			}
			else if (magic() && !questFight && staffWorn() && bestSpellMissing() != null)
			{
				// User 2026-10-09: no runes for the level's best spell (a fallback is being cast, or it was just
				// unlocked): leave, and the bank or the GE stocks them.
				leave("no runes for " + bestSpellMissing().name);
			}
			else if (!alive && !questFight && config.maxKills() > 0 && BrutusEvents.kills >= config.maxKills())
			{
				leave(BrutusEvents.kills + " kills done");
			}
			else if (!alive && !questFight && goalEndsRun())
			{
				leave("goal reached (" + goalText() + ")");
			}
		}
		if (leaving != null)
		{
			quickEscape();
			return;
		}

		prayer(alive);

		// Ranged: refill the quiver from the bag before anything else, mid-fight too (live 2026-10-05 15:08: it ran dry
		// mid-fight and kept clicking Attack while looted arrows sat in the bag; re-wielding only ran between kills,
		// after looting, and the fast cowbell respawn left no time for it).
		if (ranged() && wornBow() != null && arrowsWorn() < 30 && wieldArrows())
		{
			return;
		}
		// Magic: autocast the best spell the level and the runes in the bag allow (a lower one when an element ran out).
		if (magic() && staffWorn() && setAutocast())
		{
			return;
		}

		if (alive)
		{
			attack(npc);
			return;
		}

		// Between kills. Not enough food for another kill (the most the last kills ate): loot, then leave before he is back
		// (live 12:2x: sovietlaxbro, 21 HP, ran out mid-kill and left on "no food and N HP"). Never before the trip's first
		// kill: a kill that needs more than a full bag would bounce between the bank and the pen.
		int need = foodForAKill();
		if (!questFight && tripKills > 0 && food < need)
		{
			if (bury() || loot())
			{
				return;
			}
			leave(food + " food left, a kill takes up to " + need);
			return;
		}
		if (ringPending && ring())
		{
			return;
		}
		if (questFight)
		{
			status("quest: Bull down, waiting");
			return;
		}
		if (bury() || loot() || wieldArrows() || rotateStyle() || wearMooleta())
		{
			return;
		}
		if (Rs2Inventory.isFull())
		{
			if (food > 0 && hp < maxHp)
			{
				status("eating to make room");
				eat();
				return;
			}
			leave("inventory full");
			return;
		}
		status("waiting for Brutus to respawn");
	}

	/** Food to start a kill with: the most one of the last 5 kills ate, at least the "Leave at food" setting. */
	private int foodForAKill()
	{
		int need = config.leaveFood();
		for (int n : foodPerKill)
		{
			need = Math.max(need, n);
		}
		return need;
	}

	private void leave(String why)
	{
		leaving = why;
		leaveSinceMs = System.currentTimeMillis();
		Microbot.log(tag() + "leaving the pen: " + why);
	}

	/** Out of the pen after a leave: clear it so the next trip starts clean. */
	private void leftPen()
	{
		leaving = null;
		trip++;
		BrutusEvents.clearNoAmmo();
		stocked = false;
		inPenCached = false;
		if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE))
		{
			Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
		}
	}

	private void quickEscape()
	{
		status("leaving: " + leaving);
		Rs2TileObjectModel gate = Act.obj(EXIT_GATES);
		if (gate == null)
		{
			// No gate in sight must still end in the one-minute stop, not a silent wait in the pen for ever.
			if (System.currentTimeMillis() - leaveSinceMs > 60_000)
			{
				stop("could not find the pen gate for a minute (" + leaving + ")");
			}
			return;
		}
		synchronized (INPUT)
		{
			if (!gate.click("Quick-escape") && !gate.click("Quick escape"))
			{
				gate.click();
			}
		}
		Global.sleepUntil(() -> !inPen(), 10_000);
		if (!inPen())
		{
			leftPen();
		}
		else if (System.currentTimeMillis() - leaveSinceMs > 60_000)
		{
			stop("could not leave the pen for a minute (" + leaving + ")");
		}
	}

	private void prayer(boolean alive)
	{
		boolean want = alive && config.protectMelee() && Rs2Player.getRealSkillLevel(Skill.PRAYER) >= 43
			&& Rs2Prayer.getPrayerPoints() > 0;
		if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE) != want)
		{
			synchronized (INPUT)
			{
				Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, want);
			}
		}
	}

	private void attack(NPC npc)
	{
		boolean onHim = Microbot.getClientThread().runOnClientThreadOptional(() ->
			Microbot.getClient().getLocalPlayer() != null && Microbot.getClient().getLocalPlayer().getInteracting() == npc)
			.orElse(false);
		long now = System.currentTimeMillis();
		if (onHim)
		{
			status("fighting Brutus");
			return;
		}
		// After a dodge we lost the target: attack at once. Otherwise a short human pause between clicks.
		if (now - lastAttackMs < 1_200)
		{
			return;
		}
		// Our target flickers off while he walks; still swinging or walking up to him after a recent click is still
		// fighting (live 09:57: a re-click every 1.2 s through whole fights). A dodge clears this: it is newer.
		BrutusEvents.Special s = BrutusEvents.latest;
		boolean dodgedSince = s != null && s.atMs > lastAttackMs;
		if (!dodgedSince && now - lastAttackMs < 4_000 && (Rs2Player.isAnimating() || Rs2Player.isMoving()))
		{
			status("fighting Brutus");
			return;
		}
		status("attacking Brutus");
		synchronized (INPUT)
		{
			Rs2Npc.interact(new net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel(npc), "Attack");
		}
		lastAttackMs = now;
	}

	private boolean eat()
	{
		long now = System.currentTimeMillis();
		if (now - lastEatMs < 1_800)
		{
			return false;
		}
		String food = config.food();
		int before = food();
		boolean ok;
		synchronized (INPUT)
		{
			ok = Rs2Inventory.interact(food, "Eat");
			if (!ok)
			{
				// Any food in the bag (cake slices, other fish picked up).
				List<Rs2ItemModel> any = Rs2Inventory.getInventoryFood();
				ok = !any.isEmpty() && Rs2Inventory.interact(any.get(0), "Eat");
			}
		}
		if (ok)
		{
			lastEatMs = now;
			status("eating");
			// Any food: waiting on the chosen one alone sat out the full 1.2 s after every fallback bite.
			Global.sleepUntil(() -> food() < before, 1_200);
		}
		return ok;
	}

	private int food()
	{
		int n = Rs2Inventory.getInventoryFood().size();
		return n > 0 ? n : Rs2Inventory.count(config.food());
	}

	/**
	 * Rings the cowbell for the fast respawn. Only once his body is gone: the kill is counted when the death animation
	 * starts, and a ring during it is ignored by the game (KSP notes), which is why ringing never worked (the ring went
	 * out the next loop, mid-death, and was not retried). Waits up to {@link #RING_WAIT_MS} for the body to despawn;
	 * returns false while waiting so bones and loot carry on. The action comes from the amulet's own menu
	 * (Rs2Inventory.interact "clicks" a missing action as nothing and still returns true, see teleport()).
	 */
	private boolean ring()
	{
		long sinceKill = System.currentTimeMillis() - ringKillMs;
		boolean body = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getRs2NpcCache().query()
			.where(n -> Act.in(n.getId(), BRUTUS_IDS)).nearest() != null).orElse(false);
		if (body && sinceKill < RING_WAIT_MS)
		{
			return false;
		}
		if (System.currentTimeMillis() - lastRingMs < 1_200)
		{
			return false;
		}
		boolean inBag = Rs2Inventory.hasItem(COWBELL) || Rs2Inventory.hasItem(COWBELL_EMPTY);
		if (!inBag && !Rs2Equipment.isWearing(COWBELL) && !Rs2Equipment.isWearing(COWBELL_EMPTY))
		{
			ringPending = false;
			return false;
		}
		Rs2ItemModel bell = inBag ? Rs2Inventory.get(Rs2Inventory.hasItem(COWBELL) ? COWBELL : COWBELL_EMPTY)
			: Rs2Equipment.get(EquipmentInventorySlot.AMULET);
		List<String> actions = new ArrayList<>();
		if (bell != null)
		{
			if (!inBag && bell.getEquipmentActions() != null)
			{
				actions.addAll(bell.getEquipmentActions());
			}
			else if (inBag && bell.getInventoryActions() != null)
			{
				actions.addAll(java.util.Arrays.asList(bell.getInventoryActions()));
			}
		}
		String action = "Ring";
		for (String a : actions)
		{
			if (a != null && a.toLowerCase(Locale.ROOT).contains("ring"))
			{
				action = a;
				break;
			}
		}
		int id = bell != null ? bell.getId() : COWBELL;
		status("ringing the cowbell for a fast respawn");
		long clickMs = System.currentTimeMillis();
		lastRingMs = clickMs;
		boolean ok;
		synchronized (INPUT)
		{
			ok = inBag ? Rs2Inventory.interact(id, action) : Rs2Equipment.interact(id, action);
		}
		if (ok)
		{
			ringPending = false;
			Global.sleep(600, 900);
			Microbot.log(tag() + "rang the cowbell (" + (inBag ? "bag" : "worn") + ", '" + action + "' of " + actions
				+ ", " + (sinceKill / 100) / 10.0 + " s after the kill); game said: " + BrutusEvents.messagesSince(clickMs));
			return true;
		}
		if (++ringTries >= 3)
		{
			ringPending = false;
			Microbot.log(tag() + "could not ring the cowbell ('" + action + "' of " + actions + "); skipping it this kill");
		}
		return false;
	}

	/** Longest wait for his body to despawn before ringing anyway. */
	private static final long RING_WAIT_MS = 6_000;
	private long ringKillMs;
	private long lastRingMs;
	private int ringTries;

	private boolean bury()
	{
		if (!config.buryBones())
		{
			return false;
		}
		Rs2ItemModel bones = Rs2Inventory.get(i -> i.getName() != null && i.getName().toLowerCase(Locale.ROOT).contains("bones"));
		if (bones == null)
		{
			return false;
		}
		status("burying " + bones.getName());
		Rs2Inventory.interact(bones, "Bury");
		Global.sleep(900, 1300);
		return true;
	}

	/**
	 * Nearest drop on the scene tiles around us (scene tiles work inside the instance). Bull bones on the ground are
	 * buried there (no bag space needed), so they follow "Bury bones" alone: with "Loot" off or a full bag only bones
	 * are taken care of (they were skipped with the rest before).
	 */
	private boolean loot()
	{
		boolean pickUp = config.loot() && !Rs2Inventory.isFull();
		if (!pickUp && !config.buryBones())
		{
			return false;
		}
		Set<String> skip = new HashSet<>();
		for (String s : config.skipLoot().split(","))
		{
			if (!s.trim().isEmpty())
			{
				skip.add(s.trim().toLowerCase(Locale.ROOT));
			}
		}
		boolean bones = config.buryBones();
		List<RS2Item> items = Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			List<RS2Item> found = new ArrayList<>();
			net.runelite.api.Player me = Microbot.getClient().getLocalPlayer();
			var wv = Microbot.getClient().getTopLevelWorldView();
			if (me == null || wv == null)
			{
				return found;
			}
			LocalPoint lp = me.getLocalLocation();
			net.runelite.api.Tile[][] tiles = wv.getScene().getTiles()[wv.getPlane()];
			for (int x = Math.max(0, lp.getSceneX() - 12); x <= Math.min(tiles.length - 1, lp.getSceneX() + 12); x++)
			{
				for (int y = Math.max(0, lp.getSceneY() - 12); y <= Math.min(tiles[x].length - 1, lp.getSceneY() + 12); y++)
				{
					net.runelite.api.Tile t = tiles[x][y];
					if (t == null || t.getGroundItems() == null)
					{
						continue;
					}
					for (net.runelite.api.TileItem ti : t.getGroundItems())
					{
						var def = Microbot.getClient().getItemDefinition(ti.getId());
						String name = def.getName() == null ? "" : def.getName().toLowerCase(Locale.ROOT);
						if (skip.contains(name) || (name.contains("bones") ? !bones : !pickUp))
						{
							continue;
						}
						found.add(new RS2Item(def, t, ti));
					}
				}
			}
			return found;
		}).orElse(new ArrayList<>());
		LocalPoint me = Rs2Player.getLocalLocation();
		if (items.isEmpty() || me == null)
		{
			return false;
		}
		RS2Item best = null;
		int bestDist = Integer.MAX_VALUE;
		for (RS2Item it : items)
		{
			String key = it.getTile().getLocalLocation().getSceneX() + "," + it.getTile().getLocalLocation().getSceneY()
				+ ":" + it.getTileItem().getId();
			if (lootMisses.contains(key))
			{
				continue;
			}
			int d = Math.max(Math.abs(it.getTile().getLocalLocation().getSceneX() - me.getSceneX()),
				Math.abs(it.getTile().getLocalLocation().getSceneY() - me.getSceneY()));
			if (d < bestDist)
			{
				bestDist = d;
				best = it;
			}
		}
		if (best == null)
		{
			return false;
		}
		String key = best.getTile().getLocalLocation().getSceneX() + "," + best.getTile().getLocalLocation().getSceneY()
			+ ":" + best.getTileItem().getId();
		lootTries = key.equals(lastLootKey) ? lootTries + 1 : 1;
		lastLootKey = key;
		if (lootTries > 3)
		{
			lootMisses.add(key);
			return false;
		}
		String name = best.getItem().getName() == null ? "" : best.getItem().getName();
		if (name.toLowerCase(Locale.ROOT).contains("bones"))
		{
			// Bull bones can't be picked up: on the ground they only have "Bury" (live 10:05, every kill).
			status("burying " + name + " on the ground");
			if (groundAction(best, "Bury"))
			{
				Global.sleep(1_200, 1_800);
			}
			return true;
		}
		int before = Rs2Inventory.count();
		int lootId = best.getTileItem().getId();
		int stackBefore = Rs2Inventory.itemQuantity(lootId);
		// getItemPrice reads the item definition: client thread only (live 12:12, 0.4.0: every loot threw).
		long unitPrice = Microbot.getClientThread().runOnClientThreadOptional(() ->
			Long.valueOf(Microbot.getItemManager().getItemPrice(lootId))).orElse(0L);
		long value = unitPrice * best.getTileItem().getQuantity();
		status("looting " + name);
		if (Rs2GroundItem.interact(best) && Global.sleepUntil(() -> Rs2Inventory.count() != before
			|| Rs2Inventory.itemQuantity(lootId) != stackBefore, 4_000))
		{
			lootPicked++;
			lootGp += Math.max(0, value);
		}
		return true;
	}

	/** Rs2GroundItem's own click with another action than "Take" (its private interact(RS2Item, String)). */
	private static boolean groundAction(RS2Item item, String action)
	{
		try
		{
			java.lang.reflect.Method m = Rs2GroundItem.class.getDeclaredMethod("interact", RS2Item.class, String.class);
			m.setAccessible(true);
			return Boolean.TRUE.equals(m.invoke(null, item, action));
		}
		catch (Exception e)
		{
			Microbot.log(tag() + "cannot " + action + " " + item.getItem().getName() + " on the ground: " + e);
			return false;
		}
	}

	/** Trains the lowest of Attack, Strength and Defence; true when it clicked. */
	private boolean rotateStyle()
	{
		if (magic())
		{
			return false; // the autocast spell sets the style
		}
		if (ranged())
		{
			// Rapid (bow style 2 of 4): the fastest Ranged xp and kills; the style rotation is melee only.
			if (wornBow() == null || Microbot.getVarbitPlayerValue(VarPlayer.ATTACK_STYLE) == 1)
			{
				return false;
			}
			status("switching attack style: Ranged rapid");
			Rs2Tab.switchToCombatOptionsTab();
			Global.sleep(300, 600);
			Rs2Combat.setAttackStyle(WidgetInfo.COMBAT_STYLE_TWO);
			Global.sleepUntil(() -> Microbot.getVarbitPlayerValue(VarPlayer.ATTACK_STYLE) == 1, 2_000);
			Rs2Tab.switchToInventoryTab();
			return true;
		}
		if (!config.rotateStyles())
		{
			return false;
		}
		Skill train = pickStyle();
		int index = train == Skill.STRENGTH ? 1 : train == Skill.ATTACK ? 0 : 3;
		WidgetInfo widget = index == 1 ? WidgetInfo.COMBAT_STYLE_TWO
			: index == 0 ? WidgetInfo.COMBAT_STYLE_ONE : WidgetInfo.COMBAT_STYLE_FOUR;
		if (Microbot.getVarbitPlayerValue(VarPlayer.ATTACK_STYLE) == index)
		{
			return false;
		}
		status("switching attack style: " + train.getName() + " to " + blocks.until());
		Rs2Tab.switchToCombatOptionsTab();
		Global.sleep(300, 600);
		Rs2Combat.setAttackStyle(widget);
		Global.sleepUntil(() -> Microbot.getVarbitPlayerValue(VarPlayer.ATTACK_STYLE) == index, 2_000);
		Rs2Tab.switchToInventoryTab();
		return true;
	}

	/**
	 * Wears the Mooleta from the bag: only when the shield slot already holds something (a one-handed weapon for sure) or
	 * no weapon is worn. An empty shield slot with a weapon may be a two-hander, which the shield would take off.
	 */
	private boolean wearMooleta()
	{
		if (!config.equipMooleta() || ranged() || !Rs2Inventory.hasItem(MOOLETA) || Rs2Equipment.isWearing(MOOLETA))
		{
			return false;
		}
		boolean shield = Rs2Equipment.get(EquipmentInventorySlot.SHIELD) != null;
		Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
		// User 2026-10-09: the Mooleta was never worn. The kiteshield is skipped while a Mooleta is owned, so the shield
		// slot stayed empty beside the scimitar; a weapon from our one-handed list is safe too.
		if (!shield && weapon != null && !oneHanded(weapon.getName()))
		{
			return false;
		}
		status("wearing the Mooleta");
		Rs2Inventory.interact(MOOLETA, "Wield");
		Global.sleepUntil(() -> Rs2Equipment.isWearing(MOOLETA), 2_000);
		return true;
	}

	// ------------------------------------------------------------------ quest Bull

	/** The Ides of Milk, stage 18: food first, then "Open" the pen and kill the Bull with the normal fight. */
	void questBull()
	{
		if (inPen() || boss() != null)
		{
			inPenCached = true;
			if (!dodging())
			{
				pen(true);
			}
			return;
		}
		inPenCached = false;
		if (food() < 4 && !stocked)
		{
			bank(false);
			return;
		}
		status("quest: open the bull pen");
		if (!Act.near(PEN, 6))
		{
			Act.walk(PEN, 3);
			return;
		}
		Rs2TileObjectModel gate = Act.obj(PEN_GATES);
		if (gate != null && (gate.click("Open") || gate.click("Release") || gate.click()))
		{
			Global.sleepUntil(() -> inPen() || Act.inDialogue(), 5_000);
		}
	}

	// ------------------------------------------------------------------ outside

	private boolean hasCowbell()
	{
		return Rs2Inventory.hasItem(COWBELL) || Rs2Inventory.hasItem(COWBELL_EMPTY) || Rs2Equipment.isWearing(COWBELL)
			|| Rs2Equipment.isWearing(COWBELL_EMPTY);
	}

	private static int cowbellCharges()
	{
		return Microbot.getVarbitValue(VarbitID.CHARGES_COWBELL_AMULET_QUANTITY);
	}

	/**
	 * The cowbell can teleport: the charged amulet (33104) is held or worn and its last teleport did not fail. The charge
	 * varbit reads 0 on a charged amulet (live 12:21, 0.5.1), so it only counts when it is above 0.
	 */
	private boolean cowbellCharged()
	{
		boolean charged = Rs2Inventory.hasItem(COWBELL) || Rs2Equipment.isWearing(COWBELL);
		return (charged && !bellFailed) || cowbellCharges() > 0;
	}

	private boolean needsBank()
	{
		// Spare gear even on a stocked trip: gear back from a grave came with "stocked" still set (live 12:35).
		if (Rs2Inventory.items().anyMatch(this::spareGear))
		{
			stocked = false;
			return true;
		}
		if (stocked)
		{
			return false;
		}
		// A Bone mace only hits rats: bank for another weapon (unless we already found none).
		boolean boneMace = Rs2Equipment.isWearing("Bone mace") && !cannotWield.contains("Bone mace") && !ranged() && !magic();
		if (ranged() && wornBow() != null && arrowsCarried() < Math.min(50, arrowsPerTrip()))
		{
			return true;
		}
		if (magic() && staffWorn() && (castsCarried() < Math.min(50, castsPerTrip()) || bestSpellMissing() != null))
		{
			return true;
		}
		int hp = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = Math.max(1, Rs2Player.getRealSkillLevel(Skill.HITPOINTS));
		return boneMace || hp * 10 < maxHp * 9 || food() < foodPerTrip() || Rs2Inventory.emptySlotCount() < 3
			|| (config.useCowbell() && !hasCowbell() && !cowbellMissing);
	}

	/**
	 * Gear carried but not worn (user 2026-10-03: "bank unused gear after upgrade"): the old piece after an upgrade, the
	 * kiteshield after the Mooleta went on, gear back from a grave. The cowbell and a Mooleta still to be worn stay.
	 */
	private boolean spareGear(Rs2ItemModel it)
	{
		if (it == null || it.getId() == COWBELL || it.getId() == COWBELL_EMPTY || it.isStackable())
		{
			return false;
		}
		if (it.getId() == MOOLETA && config.equipMooleta() && !Rs2Equipment.isWearing(MOOLETA))
		{
			return false;
		}
		String[] actions = it.getInventoryActions();
		if (actions == null)
		{
			return false;
		}
		for (String a : actions)
		{
			if ("Wear".equalsIgnoreCase(a) || "Wield".equalsIgnoreCase(a) || "Equip".equalsIgnoreCase(a))
			{
				return true;
			}
		}
		return false;
	}

	private void outside()
	{
		if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE))
		{
			Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
			return;
		}
		if (regear && wearFromBag())
		{
			return;
		}
		if (selling)
		{
			status("selling loot for GE money");
			if (seller.tick() == LootSeller.Result.DONE)
			{
				selling = false;
				geSkipCoins = -1; // new coins: plan the food buy again
				stocked = false;
				bankTried.clear();
			}
			return;
		}
		if (orders != null)
		{
			restock();
			return;
		}
		if (bury())
		{
			return;
		}
		if (needsBank())
		{
			bank(true);
			return;
		}
		if (cowbellMissing && config.useCowbell() && !hasCowbell() && gillieTries < 3)
		{
			getCowbellFromGillie();
			return;
		}
		if (config.altar() && Rs2Player.getRealSkillLevel(Skill.PRAYER) >= 43 && config.protectMelee()
			&& Rs2Prayer.getPrayerPoints() * 2 < Rs2Player.getRealSkillLevel(Skill.PRAYER) && !Act.near(PEN, 30))
		{
			status("praying at the Lumbridge church altar");
			if (Act.near(ALTAR, 6))
			{
				Rs2GameObject.interact(ALTAR_ID, "Pray-at");
				Global.sleepUntil(() -> Rs2Prayer.getPrayerPoints() >= Rs2Player.getRealSkillLevel(Skill.PRAYER), 5_000);
			}
			else
			{
				Act.walk(ALTAR, 3);
			}
			return;
		}
		if (chargeCowbell())
		{
			return;
		}
		if (!Act.near(PEN, 12))
		{
			travel();
			return;
		}
		release();
	}

	/** The cowbell teleport can be tried now. */
	private boolean cowbellReady()
	{
		return config.useCowbell() && cowbellCharged() && teleportUsable();
	}

	private void travel()
	{
		// The cowbell only from the Lumbridge bank or the GE (user 2026-10-19); from anywhere else, walk.
		if (cowbellReady() && (Act.near(LUMBRIDGE_BANK, 15) || Act.near(GE, 25)))
		{
			teleport();
			if (Act.near(PEN, 30))
			{
				return;
			}
		}
		status("walking to Brutus's pen");
		Act.walk(PEN, 3);
	}

	/**
	 * The cowbell teleport can be tried: on any world (user 2026-10-09; it used to be members worlds only, after it "did
	 * nothing" on free worlds, live 10-04 12:2x-12:45), with fewer than 3 failures this run. A teleport that does not
	 * move us counts as a failure and logs the amulet's options and the game's messages.
	 */
	private boolean teleportUsable()
	{
		if (teleportFails >= 3)
		{
			if (!teleportNote)
			{
				teleportNote = true;
				Microbot.log(tag() + "cowbell teleport failed " + teleportFails + " times: walking for the rest of the run");
			}
			return false;
		}
		return true;
	}

	/**
	 * One cowbell teleport. The action is taken from the amulet's own menu (Rs2Inventory.interact "clicks" a missing
	 * action as nothing and still returns true, so "clicked" proved nothing in 0.5.6). A destination menu is answered.
	 * On failure the amulet's options and the game's messages are logged.
	 */
	private void teleport()
	{
		boolean worn = !Rs2Inventory.hasItem(COWBELL);
		Rs2ItemModel bell = worn ? Rs2Equipment.get(EquipmentInventorySlot.AMULET) : Rs2Inventory.get(COWBELL);
		if (bell == null || bell.getId() != COWBELL)
		{
			return;
		}
		List<String> actions = new ArrayList<>();
		if (worn)
		{
			if (bell.getEquipmentActions() != null)
			{
				actions.addAll(bell.getEquipmentActions());
			}
		}
		else if (bell.getInventoryActions() != null)
		{
			actions.addAll(java.util.Arrays.asList(bell.getInventoryActions()));
		}
		String action = pickAction(actions, bell.getSubops());
		// User 2026-10-09: an open bank swallows the amulet click; close it first.
		if (Rs2Bank.isOpen())
		{
			Rs2Bank.closeBank();
			if (!Global.sleepUntil(() -> !Rs2Bank.isOpen(), 2_000))
			{
				return;
			}
			Global.sleep(600, 1000);
		}
		WorldPoint before = Act.me();
		long clickMs = System.currentTimeMillis();
		status("cowbell teleport to the cow field (" + action + ")");
		boolean ok = worn ? Rs2Equipment.interact(COWBELL, action) : Rs2Inventory.interact(COWBELL, action);
		boolean moved = ok && Global.sleepUntil(() ->
		{
			if (Rs2Dialogue.hasSelectAnOption())
			{
				Act.dialogue("Cow field", "Cow", "Brutus", "Teleport", "Yes");
			}
			WorldPoint now = Act.me();
			return now != null && before != null && now.distanceTo(before) > 20;
		}, 12_000);
		if (moved)
		{
			teleportFails = 0;
			Global.sleep(600, 1200);
			return;
		}
		teleportFails++;
		String said = BrutusEvents.messagesSince(clickMs);
		// User 2026-10-19: one miss used to switch the cowbell off until a charge registered, so only the first trip
		// teleported. Only the game saying it is out of charges does that now; a miss is tried again next trip.
		bellFailed = said != null && said.toLowerCase(Locale.ROOT).contains("charge");
		Microbot.log(tag() + "cowbell teleport did not move us (" + (worn ? "worn" : "bag") + ", action '" + action
			+ "' of " + actions + ", fail " + teleportFails + "); game said: " + said
			+ (bellFailed ? "; walking, charging it at the bank" : "; walking, trying it again next trip"));
	}

	/** The amulet's teleport action: one naming "Teleport" or the cow field, else "Rub", else "Teleport". */
	static String pickAction(List<String> actions, String[][] subops)
	{
		for (String key : new String[]{"teleport", "cow field", "rub"})
		{
			for (String a : actions)
			{
				if (a != null && a.toLowerCase(Locale.ROOT).contains(key))
				{
					return a;
				}
			}
			for (String[] row : subops == null ? new String[0][] : subops)
			{
				for (String a : row == null ? new String[0] : row)
				{
					if (a != null && a.toLowerCase(Locale.ROOT).contains(key))
					{
						return a; // Rs2Inventory finds sub-menu actions by name
					}
				}
			}
		}
		return "Teleport";
	}

	/** "Release" on the pen gate (the warning is answered with "don't ask again"). */
	private void release()
	{
		Rs2TileObjectModel gate = Act.obj(PEN_GATES);
		if (gate == null)
		{
			status("no pen gate near " + PEN + "; walking closer");
			Act.walk(PEN, 1);
			return;
		}
		status("releasing Brutus");
		if (gate.click("Release") || gate.click())
		{
			Global.sleepUntil(() -> inPen() || Act.inDialogue(), 6_000);
			if (Act.inDialogue())
			{
				answerDialogue();
				Global.sleepUntil(DaBrutusScript::inPen, 5_000);
			}
		}
	}

	/** Lumbridge castle bank: food, cowbell, air runes. {@code full}: the trip's full stock (else food only). */
	private void bank(boolean full)
	{
		if (!Rs2Bank.isOpen())
		{
			bankTried.clear();
			// From the GE the cowbell is the way back (user 2026-10-19): the cow field is a short walk from the castle.
			if (Act.near(GE, 25) && cowbellReady() && System.currentTimeMillis() - geTeleportMs > 60_000)
			{
				geTeleportMs = System.currentTimeMillis();
				teleport();
				return;
			}
			status("walking to the Lumbridge bank");
			// Walk, then open: Rs2Bank.walkToBankAndUseBank threw a NullPointerException in SlugBot's pathfinder on every
			// loop for a player (2026-10-19, "it doesn't walk to the bank").
			if (!Act.near(LUMBRIDGE_BANK, 6))
			{
				Act.walk(LUMBRIDGE_BANK, 4);
				return;
			}
			Rs2Bank.openBank();
			Global.sleepUntil(Rs2Bank::isOpen, 3_000);
			return;
		}
		status("banking");
		String food = bankFood();
		Set<Integer> keep = new HashSet<>();
		keep.add(COWBELL);
		keep.add(COWBELL_EMPTY);
		keep.add(AIR_RUNE);
		if (config.equipMooleta() && !ranged() && !Rs2Equipment.isWearing(MOOLETA))
		{
			keep.add(MOOLETA); // still to be worn; a spare one (one already worn) is banked
		}
		// Ranged: the arrows this trip uses stay in the bag (to be worn); other arrows are banked.
		int arrowId = ranged() ? arrowTarget() : -1;
		if (arrowId > 0)
		{
			keep.add(arrowId);
		}
		// Magic: the runes stay in the bag.
		if (magic())
		{
			for (int r : MagicGear.RUNES)
			{
				keep.add(r);
			}
		}
		// The quest's items must stay in the bag.
		keep.add(ItemID.COWQUEST_HUSBANDRY_BOOK);
		keep.add(ItemID.COWQUEST_MILK_SAMPLE_1);
		keep.add(ItemID.COWQUEST_MILK_SAMPLE_2);
		boolean other = false;
		for (Rs2ItemModel it : Rs2Inventory.items().collect(java.util.stream.Collectors.toList()))
		{
			if (it != null && !keep.contains(it.getId()) && !isFood(it.getName()))
			{
				other = true;
				break;
			}
		}
		if (other)
		{
			Rs2Bank.depositAll(i -> !keep.contains(i.getId()) && !isFood(i.getName()));
			Global.sleep(400, 800);
			return;
		}
		if (full && (magic() ? wieldStaff() : ranged() ? wieldBestBow() : wieldBestWeapon()))
		{
			return;
		}
		if (full && config.upgradeArmour() && (magic() ? wearArmourSet(MagicGear.SLOTS, MagicGear.ARMOUR)
			: ranged() ? wearRangedArmour() : wearArmour()))
		{
			return;
		}
		if (full && bankTried.add("geplan") && planGe(false))
		{
			return;
		}
		// User 2026-10-09: the coins did not cover the restock: sell Brutus's loot, then plan it again with the new gold.
		if (full && shortOfGold && bankTried.add("goldsale") && startLootSale("the coins don't cover the GE restock"))
		{
			return;
		}
		// Eat up at the bank first, then restock (user 2026-10-03): the trip starts at full HP with full food.
		int hp = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = Rs2Player.getRealSkillLevel(Skill.HITPOINTS);
		if (hp < maxHp - 4)
		{
			if (food() > 0)
			{
				status("eating at the bank (" + hp + "/" + maxHp + ")");
				if (!eat())
				{
					Global.sleep(300, 600);
				}
				return;
			}
			if (Rs2Bank.count(food, true) > 0 && bankTried.add("eat" + hp))
			{
				Rs2Bank.withdrawX(food, Math.min(Rs2Bank.count(food, true), Math.max(2, (maxHp - hp) / 6 + 1)), true);
				Global.sleepUntil(() -> food() > 0, 2_000);
				return;
			}
		}
		if (full && config.useCowbell() && !hasCowbell() && !cowbellMissing)
		{
			if (Rs2Bank.hasItem(COWBELL) || Rs2Bank.hasItem(COWBELL_EMPTY))
			{
				Rs2Bank.withdrawOne(Rs2Bank.hasItem(COWBELL) ? COWBELL : COWBELL_EMPTY);
				Global.sleepUntil(this::hasCowbell, 2_000);
				return;
			}
			if (!cowbellMissing)
			{
				Microbot.log(tag() + "no cowbell amulet in the bank: Gillie Groats gives a new one");
			}
			cowbellMissing = true;
		}
		int wantAir = full && config.useCowbell() && !cowbellCharged() ? config.airRunes() : 0;
		int air = Rs2Inventory.itemQuantity(AIR_RUNE);
		// Each top-up is tried once per bank visit: a withdraw that does not land (live 09:51, Edric at 48/50 air
		// runes) looped the bank for ever.
		if (wantAir > 0 && air < wantAir && Rs2Bank.count(AIR_RUNE) > 0 && bankTried.add("air"))
		{
			Rs2Bank.withdrawX(AIR_RUNE, Math.min(wantAir - air, Rs2Bank.count(AIR_RUNE)));
			Global.sleepUntil(() -> Rs2Inventory.itemQuantity(AIR_RUNE) != air, 2_000);
			return;
		}
		if (full && ranged() && stockArrows(arrowId))
		{
			return;
		}
		if (full && magic() && stockRunes())
		{
			return;
		}
		if (full && config.equipMooleta() && !ranged() && !Rs2Equipment.isWearing(MOOLETA) && !Rs2Inventory.hasItem(MOOLETA)
			&& Rs2Bank.hasItem(MOOLETA) && bankTried.add("mooleta"))
		{
			Rs2Bank.withdrawOne(MOOLETA);
			Global.sleep(400, 800);
			return;
		}
		int want = full ? foodPerTrip() : Math.max(6, foodPerTrip() / 2);
		int have = food();
		// The chosen food first (user 2026-10-03): the bank has none of it, so try the GE for it before other bank food.
		if (have < want && !food.equalsIgnoreCase(config.food()) && bankTried.add("gechosen") && planGe(true))
		{
			return;
		}
		// Three tries per bank visit (user 2026-10-19: one withdraw that did not land within 2 s stopped the script with
		// "could not take Trout from the bank").
		if (have < want && (bankTried.add("food1") || bankTried.add("food2") || bankTried.add("food3")))
		{
			int inBank = Rs2Bank.count(food, true);
			if (inBank <= 0 && bankTried.add("gefood") && planGe(true))
			{
				return;
			}
			if (inBank <= 0 && startLootSale("out of food and the coins buy none"))
			{
				return;
			}
			if (inBank <= 0)
			{
				if (have >= 4)
				{
					Microbot.log(tag() + "bank is out of " + food + "; going with " + have);
				}
				else
				{
					stop("no " + food + " in the bank (" + have + " carried)");
					return;
				}
			}
			else
			{
				if (Rs2Inventory.emptySlotCount() == 0)
				{
					Microbot.log(tag() + "bag full before taking " + food + ": " + Rs2Inventory.items()
						.map(Rs2ItemModel::getName).collect(java.util.stream.Collectors.toList()));
				}
				Rs2Bank.withdrawX(food, Math.min(want - have, inBank), true); // exact: "Trout" also matched Raw trout (live 10:05, Edric)
				Global.sleepUntil(() -> food() > have, 3_000);
				return;
			}
		}
		if (have < 4)
		{
			stop("could not take " + food + " from the bank (" + have + " carried)");
			return;
		}
		if (full && ranged() && wornBow() != null && arrowsCarried() == 0)
		{
			// Out of arrows, none in the bank, and the GE plan bought none (user 2026-10-09: stop with a proper message).
			RangedGear.Piece arrow = RangedGear.ARROWS[RangedGear.bestArrowTier(wornBow(), Rs2Player.getRealSkillLevel(Skill.RANGED))];
			long coins = (long) Rs2Bank.count(COINS) + Rs2Inventory.itemQuantity(COINS);
			String why = !config.buyOnGe() ? "buying at the GE is turned off"
				: geGaveUp.contains(arrow.name) ? "the GE could not buy " + arrow.name + "s"
				: shortOfGold ? "not enough gold to buy " + arrow.name + "s (" + coins + " gp)"
				: "the GE plan bought none (" + coins + " gp; see the GE: lines above)";
			stop("out of arrows: none in the bank, and " + why);
			return;
		}
		if (full && magic() && staffWorn() && castsCarried() == 0)
		{
			// Out of runes, none in the bank, and the GE plan bought none.
			MagicGear.Cast spell = MagicGear.best(Rs2Player.getRealSkillLevel(Skill.MAGIC), config.useBlasts());
			String rune = MagicGear.runeName(spell.catalytic);
			long coins = (long) Rs2Bank.count(COINS) + Rs2Inventory.itemQuantity(COINS);
			String why = !config.buyOnGe() ? "buying at the GE is turned off"
				: geGaveUp.contains(rune) ? "the GE could not buy " + rune + "s"
				: shortOfGold ? "not enough gold to buy runes for " + spell.name + " (" + coins + " gp)"
				: "the GE plan bought none (" + coins + " gp; see the GE: lines above)";
			stop("out of runes: none in the bank, and " + why);
			return;
		}
		if (full && magic() && staffWorn() && bestSpellMissing() != null)
		{
			// Neither the bank nor the GE plan gave runes for the best spell: fight with a fallback, don't loop back here.
			runeBuyFailedMs = System.currentTimeMillis();
			Microbot.log(tag() + "no runes for " + bestSpellMissing().name + " after banking: using a lower spell for 10 min");
		}
		Rs2Bank.closeBank();
		Global.sleepUntil(() -> !Rs2Bank.isOpen(), 2_000);
		bankTried.clear();
		stocked = true;
		tripKills = 0;
		status("stocked: " + food() + " food");
	}

	/**
	 * Out of food and the coins buy none (user 2026-10-04): sell his drops on the GE, then the bank step plans the food
	 * buy again. At most once per 10 min, and only with loot in the bank. Bank open.
	 */
	private boolean startLootSale(String why)
	{
		if (!config.sellLootWhenOutOfFood() || selling || System.currentTimeMillis() - lastSaleMs < 10 * 60_000L)
		{
			return false;
		}
		if (LootSeller.sellable().isEmpty())
		{
			Microbot.log(tag() + why + ", and no loot in the bank to sell");
			lastSaleMs = System.currentTimeMillis();
			return false;
		}
		Microbot.log(tag() + why + ": selling loot on the GE");
		lastSaleMs = System.currentTimeMillis();
		seller.reset();
		selling = true;
		return true;
	}

	/** Cooked foods, best first, used when the chosen food runs out (user 2026-10-03: "use any available food"). */
	static final String[] FOODS = {"Shark", "Monkfish", "Lobster", "Swordfish", "Cooked karambwan", "Bass", "Tuna",
		"Salmon", "Trout", "Pike", "Cod", "Herring", "Mackerel", "Cake", "Meat pie", "Bread", "Sardine", "Shrimps",
		"Anchovies", "Cooked meat", "Cooked chicken", "Plain pizza", "Meat pizza", "Anchovy pizza", "Pineapple pizza",
		"Chocolate cake", "Potato with cheese", "Tuna potato", "Stew", "Jug of wine", "Apple pie", "Redberry pie",
		"Rainbow fish", "Cooked rabbit", "Ugthanki kebab"};

	/** The food to take now: the chosen one while the bank has it, else the best other food the bank holds. */
	private String bankFood()
	{
		String chosen = config.food();
		if (Rs2Bank.count(chosen, true) > 0)
		{
			return chosen;
		}
		for (String f : FOODS)
		{
			if (Rs2Bank.count(f, true) > 0)
			{
				if (bankTried.add("foodswitch" + f))
				{
					Microbot.log(tag() + "no " + chosen + " in the bank; taking " + f);
				}
				return f;
			}
		}
		return chosen;
	}

	private boolean isFood(String name)
	{
		if (name == null)
		{
			return false;
		}
		if (name.equalsIgnoreCase(config.food()))
		{
			return true;
		}
		for (String f : FOODS)
		{
			if (f.equalsIgnoreCase(name))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Melee weapons best first, with the Attack level each needs (Da-Scurrius's MeleeGear list, one-handed so the Mooleta
	 * fits). No Bone mace: it only works against rats (user 2026-10-03), so it is never wielded here.
	 */
	static final String[][] WEAPONS = {
		{"Abyssal tentacle", "75"}, {"Abyssal whip", "70"}, {"Zombie axe", "65"}, {"Sarachnis cudgel", "65"},
		{"Dragon scimitar", "60"}, {"Arkan blade", "60"}, {"Dragon longsword", "60"}, {"Dragon sword", "60"},
		{"Dragon mace", "60"}, {"Granite hammer", "50"}, {"Rune scimitar", "40"}, {"Rune longsword", "40"},
		{"Rune sword", "40"}, {"Rune mace", "40"}, {"Adamant scimitar", "30"}, {"Mithril scimitar", "20"},
		{"Black scimitar", "10"}, {"Steel scimitar", "5"}, {"Iron scimitar", "1"}, {"Bronze scimitar", "1"}};
	/** A weapon of {@link #WEAPONS} (all one-handed) or the Bone mace: a shield fits beside it. */
	static boolean oneHanded(String name)
	{
		if (name == null)
		{
			return false;
		}
		if (name.equalsIgnoreCase("Bone mace"))
		{
			return true;
		}
		for (String[] w : WEAPONS)
		{
			if (w[0].equalsIgnoreCase(name))
			{
				return true;
			}
		}
		return false;
	}

	/** Weapons that failed to wield (a members item on a free world, a quest lock). */
	private final Set<String> cannotWield = new HashSet<>();

	/**
	 * Bank open: wields the best weapon the account owns and can use, when it is not the one worn (a Bone mace is
	 * always swapped). True when it clicked.
	 */
	private boolean wieldBestWeapon()
	{
		int attack = Rs2Player.getRealSkillLevel(Skill.ATTACK);
		Rs2ItemModel worn = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
		String wornName = worn == null || worn.getName() == null ? "" : worn.getName();
		for (String[] w : WEAPONS)
		{
			String name = w[0];
			if (Integer.parseInt(w[1]) > attack || cannotWield.contains(name))
			{
				continue;
			}
			if (wornName.equalsIgnoreCase(name))
			{
				return false; // already wearing the best one we own
			}
			boolean held = Rs2Inventory.hasItem(name);
			if (!held && !Rs2Bank.hasItem(name, true))
			{
				continue;
			}
			status("wielding " + name + (wornName.isEmpty() ? "" : " instead of " + wornName));
			if (held)
			{
				Rs2Inventory.interact(name, "Wield");
			}
			else
			{
				Rs2Bank.withdrawAndEquip(name);
			}
			if (!Global.sleepUntil(() -> Rs2Equipment.isWearing(name), 3_000))
			{
				cannotWield.add(name);
				Microbot.log(tag() + "could not wield " + name + "; trying the next weapon");
			}
			return true;
		}
		if (wornName.equalsIgnoreCase("Bone mace") && cannotWield.add("Bone mace"))
		{
			Microbot.log(tag() + "only a Bone mace (rats only) and no other weapon owned: keeping it");
		}
		return false;
	}

	/** Every air rune in the bag onto the cowbell at once (user 2026-10-03): use the runes on it, type the whole stack. */
	private boolean chargeCowbell()
	{
		if (!config.useCowbell() || !hasCowbell() || !Rs2Inventory.hasItem(AIR_RUNE) || cowbellCharged()
			|| !bankTried.add("charge" + Rs2Inventory.itemQuantity(AIR_RUNE)))
		{
			return false;
		}
		if (Rs2Equipment.isWearing(COWBELL) || Rs2Equipment.isWearing(COWBELL_EMPTY))
		{
			Rs2Equipment.unEquip(EquipmentInventorySlot.AMULET);
			Global.sleep(600, 900);
			return true;
		}
		int cowbell = Rs2Inventory.hasItem(COWBELL) ? COWBELL : COWBELL_EMPTY;
		int runes = Rs2Inventory.itemQuantity(AIR_RUNE);
		int before = cowbellCharges();
		int amount = Math.min(runes, 1000 - before);
		status("charging the cowbell with " + amount + " air runes");
		Rs2Inventory.combine(AIR_RUNE, cowbell);
		if (Global.sleepUntil(DaBrutusScript::chargePrompt, 3_000))
		{
			Global.sleep(400, 900);
			Rs2Keyboard.typeString(Integer.toString(amount));
			Global.sleep(200, 500);
			Rs2Keyboard.enter();
		}
		else
		{
			Microbot.log(tag() + "cowbell: no charge prompt after using the runes (chat title '" + chatText(WidgetInfo.CHATBOX_TITLE)
				+ "', input '" + chatText(WidgetInfo.CHATBOX_FULL_INPUT) + "', dialogue " + Act.inDialogue() + ")");
		}
		if (Global.sleepUntil(() -> cowbellCharges() != before || Rs2Inventory.itemQuantity(AIR_RUNE) != runes, 3_000))
		{
			bellFailed = false;
		}
		return true;
	}

	private static String chatText(WidgetInfo info)
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Widget w = Microbot.getClient().getWidget(info);
			return w == null || w.isHidden() || w.getText() == null ? "" : w.getText();
		}).orElse("");
	}

	/** The "How many charges do you want to apply?" amount box. Only then are digits typed (never into public chat). */
	private static boolean chargePrompt()
	{
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			for (WidgetInfo w : new WidgetInfo[]{WidgetInfo.CHATBOX_FULL_INPUT, WidgetInfo.CHATBOX_TITLE})
			{
				Widget widget = Microbot.getClient().getWidget(w);
				if (widget != null && !widget.isHidden() && widget.getText() != null
					&& widget.getText().toLowerCase(Locale.ROOT).contains("how many charges"))
				{
					return true;
				}
			}
			return false;
		}).orElse(false);
	}

	/** A lost cowbell amulet: Gillie Groats hands out another after the quest. */
	private void getCowbellFromGillie()
	{
		status("getting a cowbell amulet from Gillie Groats");
		if (Act.talk(IdesOfMilk.GILLIE, NpcID.GILLIE_THE_MILKMAID, "cowbell", "amulet"))
		{
			gillieTries++;
			long end = System.currentTimeMillis() + 20_000;
			while (System.currentTimeMillis() < end && Act.inDialogue() && isRunning())
			{
				Act.dialogue("cowbell", "amulet");
			}
			if (hasCowbell())
			{
				cowbellMissing = false;
			}
		}
	}

	// ------------------------------------------------------------------ paint numbers

	/** The skill of the current training block ({@link StyleBlocks}: 3-7 levels, 1-3 at 50+). */
	private Skill pickStyle()
	{
		return blocks.pick(Rs2Player.getRealSkillLevel(Skill.ATTACK), Rs2Player.getRealSkillLevel(Skill.STRENGTH),
			Rs2Player.getRealSkillLevel(Skill.DEFENCE));
	}

	/** The level the current training block ends at. */
	int trainingUntil()
	{
		return blocks.until();
	}

	/** The skill the style rotation trains now, or null when rotation is off. */
	Skill trainingSkill()
	{
		return config == null ? null : magic() ? Skill.MAGIC : ranged() ? Skill.RANGED : !config.rotateStyles() ? null : pickStyle();
	}

	/** The paint's training cell. */
	String trainingText()
	{
		if (config != null && magic())
		{
			MagicGear.Cast c = castable();
			return "Magic · " + (!staffWorn() ? "no staff" : c == null ? "no runes" : c.name) + " · " + castsCarried();
		}
		if (config != null && ranged())
		{
			RangedGear.Piece bow = wornBow();
			return "Ranged rapid · " + (bow == null ? "no bow" : bow.name.replace(" shortbow", " sb")) + " · " + arrowsCarried();
		}
		Skill s = trainingSkill();
		return s == null ? "off" : s.getName() + " to " + trainingUntil();
	}

	List<Long> killTimes()
	{
		return killTimes;
	}

	/** XP gained this session; the first call per skill (logged in) sets the baseline. Render thread. */
	int xpGained(Skill skill)
	{
		Integer start = startXp.get(skill);
		int now = Microbot.getClient().getSkillExperience(skill);
		if (start == null || start <= 0)
		{
			if (now > 0)
			{
				startXp.put(skill, now);
			}
			return 0;
		}
		return Math.max(0, now - start);
	}

	// ------------------------------------------------------------------ magic (combat mode MAGIC)

	private boolean magic()
	{
		return config != null && config.combatMode() == DaBrutusConfig.CombatMode.MAGIC;
	}

	static boolean staffWorn()
	{
		return Rs2Equipment.isWearing(MagicGear.STAFF_OF_AIR);
	}

	/** The best spell the Magic level and the runes in the bag allow, or null. */
	private MagicGear.Cast castable()
	{
		return MagicGear.castable(Rs2Player.getRealSkillLevel(Skill.MAGIC), config.useBlasts(), Rs2Inventory::itemQuantity);
	}

	/**
	 * The level's best spell when the bag holds no runes for it and a trip may fix that (GE buying on, and the last full
	 * bank trip did not end without them in the past 10 min); else null.
	 */
	private MagicGear.Cast bestSpellMissing()
	{
		MagicGear.Cast best = MagicGear.best(Rs2Player.getRealSkillLevel(Skill.MAGIC), config.useBlasts());
		if (best.casts(Rs2Inventory::itemQuantity) > 0)
		{
			return null;
		}
		boolean geCanBuy = config.buyOnGe() && System.currentTimeMillis() - runeBuyFailedMs > 10 * 60_000L
			&& !geGaveUp.contains(MagicGear.runeName(best.catalytic))
			&& (best.element <= 0 || !geGaveUp.contains(MagicGear.runeName(best.element)));
		return geCanBuy ? best : null;
	}

	/** Casts of the best castable spell the bag's runes allow. */
	private int castsCarried()
	{
		MagicGear.Cast c = castable();
		return c == null ? 0 : c.casts(Rs2Inventory::itemQuantity);
	}

	/** Sets the autocast spell to the best castable one when it is not already. True when it clicked. */
	private boolean setAutocast()
	{
		MagicGear.Cast c = castable();
		if (c == null || Rs2Magic.getCurrentAutoCastSpell() == c.spell)
		{
			return false;
		}
		status("autocasting " + c.name);
		synchronized (INPUT)
		{
			Rs2Combat.setAutoCastSpell(c.spell, false);
		}
		if (!Global.sleepUntil(() -> Rs2Magic.getCurrentAutoCastSpell() == c.spell, 3_000))
		{
			Microbot.log(tag() + "could not set autocast to " + c.name + " (now " + Rs2Magic.getCurrentAutoCastSpell() + ")");
		}
		Rs2Tab.switchToInventoryTab();
		return true;
	}

	/** Bank open: wields the Staff of air (from the bag or the bank). True when it clicked. */
	private boolean wieldStaff()
	{
		if (staffWorn() || cannotWield.contains("Staff of air"))
		{
			return false;
		}
		boolean held = Rs2Inventory.hasItem(MagicGear.STAFF_OF_AIR);
		if (!held && !Rs2Bank.hasItem(MagicGear.STAFF_OF_AIR))
		{
			return false; // the GE plan buys one
		}
		status("wielding Staff of air");
		if (held)
		{
			Rs2Inventory.interact(MagicGear.STAFF_OF_AIR, "Wield");
		}
		else
		{
			Rs2Bank.withdrawAndEquip(MagicGear.STAFF_OF_AIR);
		}
		if (!Global.sleepUntil(DaBrutusScript::staffWorn, 3_000))
		{
			cannotWield.add("Staff of air");
			Microbot.log(tag() + "could not wield the Staff of air");
		}
		return true;
	}

	/**
	 * Bank open: runes for "Casts per trip" of every spell of the trip's tier, known or not: earth, fire and water
	 * runes, and mind runes below Magic 17, chaos runes from 17, or death runes from 41 with "Use blast spells" on
	 * (user 2026-10-09). One withdraw per rune per visit. True when it clicked.
	 */
	private boolean stockRunes()
	{
		if (!staffWorn())
		{
			return false;
		}
		int level = Rs2Player.getRealSkillLevel(Skill.MAGIC);
		int catalytic = MagicGear.catalytic(level, config.useBlasts());
		int casts = castsPerTrip();
		java.util.Map<Integer, Integer> want = new java.util.LinkedHashMap<>();
		want.put(catalytic, casts);
		// Water, earth and fire runes all come along (user 2026-10-09), for the tier's spells the level does not know yet
		// too: switching to another spell (an element ran out, or a level-up unlocked one) needs no bank trip.
		for (MagicGear.Cast c : MagicGear.SPELLS)
		{
			if (c.catalytic == catalytic && c.element > 0)
			{
				want.merge(c.element, casts * c.elementPerCast, Math::max);
			}
		}
		for (java.util.Map.Entry<Integer, Integer> e : want.entrySet())
		{
			int id = e.getKey();
			int need = e.getValue() - Rs2Inventory.itemQuantity(id);
			int inBank = Rs2Bank.count(id);
			if (need > 0 && inBank > 0 && bankTried.add("runes" + id))
			{
				int n = Math.min(need, inBank);
				status("taking " + n + " " + MagicGear.runeName(id) + "s");
				Rs2Bank.withdrawX(id, n);
				Global.sleepUntil(() -> Rs2Inventory.itemQuantity(id) > 0, 2_000);
				return true;
			}
		}
		return false;
	}

	/**
	 * GE orders for magic mode: a Staff of air when none is owned, then runes for "Casts per GE buy" of the best spell for
	 * the level (its catalytic and element runes) when the bank and bag hold less than a trip's worth, while the budget
	 * lasts. Bank open.
	 */
	private long planMagic(List<GeBuyer.Order> list, StringBuilder text, long budget, int markup)
	{
		boolean ownStaff = staffWorn() || Rs2Inventory.hasItem(MagicGear.STAFF_OF_AIR) || Rs2Bank.hasItem(MagicGear.STAFF_OF_AIR);
		if (!ownStaff && !geGaveUp.contains("Staff of air") && !cannotWield.contains("Staff of air"))
		{
			int unit = GeBuyer.unitPrice(new GeBuyer.Order("Staff of air", MagicGear.STAFF_OF_AIR, 1, 1500));
			int offer = GeBuyer.offerPrice(unit, markup, 0);
			if (offer <= budget)
			{
				list.add(new GeBuyer.Order("Staff of air", MagicGear.STAFF_OF_AIR, 1, unit));
				budget -= offer;
				text.append(text.length() > 0 ? ", " : "").append("Staff of air");
			}
			else
			{
				noGold("GE: not buying a Staff of air: ~" + offer + " gp, " + budget + " gp left");
			}
		}
		MagicGear.Cast spell = MagicGear.best(Rs2Player.getRealSkillLevel(Skill.MAGIC), config.useBlasts());
		int casts = castsPerTrip();
		int id = spell.catalytic;
		String name = MagicGear.runeName(id);
		long have = Rs2Bank.count(id) + Rs2Inventory.itemQuantity(id);
		if (have < casts && !geGaveUp.contains(name))
		{
			int unit = GeBuyer.unitPrice(new GeBuyer.Order(name, id, 1,
				id == MagicGear.DEATH_RUNE ? 200 : id == MagicGear.CHAOS_RUNE ? 90 : 5));
			int offer = GeBuyer.offerPrice(unit, markup, 0);
			int qty = (int) Math.min(geBatch(Amounts.Kind.RUNES, config.castsToBuy(), 100), budget / Math.max(1, offer));
			if (qty >= 50)
			{
				list.add(new GeBuyer.Order(name, id, qty, unit));
				budget -= (long) offer * qty;
				text.append(text.length() > 0 ? ", " : "").append(qty).append(" x ").append(name);
			}
			else
			{
				noGold("GE: " + budget + " gp does not buy " + name + "s for " + spell.name + " (~" + offer + " gp each)");
			}
		}
		// Water, earth and fire runes are bought together (user 2026-10-09): on any restock, or when the best spell's
		// element is short of a trip, each is topped up to 3000.
		boolean elementShort = spell.element > 0
			&& Rs2Bank.count(spell.element) + Rs2Inventory.itemQuantity(spell.element) < (long) casts * spell.elementPerCast;
		boolean elementsDone = false;
		if (elementShort || !list.isEmpty())
		{
			budget = planElements(list, text, budget, markup);
			elementsDone = true;
		}
		if (config.upgradeArmour())
		{
			int before = list.size();
			budget = planArmourSet(MagicGear.SLOTS, MagicGear.ARMOUR, list, text, budget, markup);
			// An armour-only restock still goes to the GE: top the element runes up on the same trip.
			if (!elementsDone && list.size() > before)
			{
				budget = planElements(list, text, budget, markup);
			}
		}
		return budget;
	}

	/** GE orders for a batch (1500-6500 by the coins) of each of water, earth and fire runes held short of one. Bank open. */
	private long planElements(List<GeBuyer.Order> list, StringBuilder text, long budget, int markup)
	{
		for (int id : MagicGear.ELEMENTS)
		{
			String name = MagicGear.runeName(id);
			int want = MagicGear.elementBatch(Rs2Bank.count(id) + Rs2Inventory.itemQuantity(id), geBatch(Amounts.Kind.ELEMENTS,
				GeBuyer.byGold(planCoins, MagicGear.ELEMENTS_MIN, MagicGear.ELEMENTS_MAX), MagicGear.ELEMENTS_MIN / 2));
			if (want < 50 || geGaveUp.contains(name))
			{
				continue;
			}
			int unit = GeBuyer.unitPrice(new GeBuyer.Order(name, id, 1, 5));
			int offer = GeBuyer.offerPrice(unit, markup, 0);
			int qty = (int) Math.min(want, budget / Math.max(1, offer));
			if (qty >= 50)
			{
				list.add(new GeBuyer.Order(name, id, qty, unit));
				budget -= (long) offer * qty;
				text.append(text.length() > 0 ? ", " : "").append(qty).append(" x ").append(name);
			}
			else
			{
				noGold("GE: " + budget + " gp does not buy " + name + "s (~" + offer + " gp each)");
			}
		}
		return budget;
	}

	// ------------------------------------------------------------------ ranged (combat mode RANGED)

	private boolean ranged()
	{
		return config != null && config.combatMode() == DaBrutusConfig.CombatMode.RANGED;
	}

	/** The worn shortbow, or null. */
	static RangedGear.Piece wornBow()
	{
		Rs2ItemModel w = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
		return w == null ? null : RangedGear.bow(w.getName());
	}

	/** Arrows in the quiver (0 when none, or not arrows). */
	static int arrowsWorn()
	{
		Rs2ItemModel a = Rs2Equipment.get(EquipmentInventorySlot.AMMO);
		return a == null || !RangedGear.isArrow(a.getId()) ? 0 : a.getQuantity();
	}

	static int arrowsWornId()
	{
		Rs2ItemModel a = Rs2Equipment.get(EquipmentInventorySlot.AMMO);
		return a == null || !RangedGear.isArrow(a.getId()) ? -1 : a.getId();
	}

	/** Arrows the worn bow can fire: in the quiver plus in the bag. */
	private int arrowsCarried()
	{
		int max = RangedGear.bestArrowTier(wornBow(), Rs2Player.getRealSkillLevel(Skill.RANGED));
		int n = RangedGear.arrowTier(arrowsWornId()) <= max ? arrowsWorn() : 0;
		for (Rs2ItemModel i : Rs2Inventory.items().filter(i -> i != null && RangedGear.isArrow(i.getId()))
			.collect(java.util.stream.Collectors.toList()))
		{
			n += RangedGear.arrowTier(i.getId()) <= max ? i.getQuantity() : 0;
		}
		return n;
	}

	/**
	 * Arrows picked up in the pen (ours, or his iron arrow drops) go back in the quiver when they are the same kind, or
	 * when the quiver is empty and the bow fires them. True when it clicked.
	 */
	private boolean wieldArrows()
	{
		if (!ranged() || wornBow() == null)
		{
			return false;
		}
		int worn = arrowsWornId();
		int max = RangedGear.bestArrowTier(wornBow(), Rs2Player.getRealSkillLevel(Skill.RANGED));
		Rs2ItemModel bag = Rs2Inventory.get(i -> i != null && RangedGear.isArrow(i.getId())
			&& (worn < 0 ? RangedGear.arrowTier(i.getId()) <= max : i.getId() == worn));
		if (bag == null)
		{
			return false;
		}
		status("wielding " + bag.getQuantity() + " " + bag.getName());
		int id = bag.getId();
		synchronized (INPUT)
		{
			Rs2Inventory.interact(id, "Wield");
		}
		Global.sleepUntil(() -> !Rs2Inventory.hasItem(id), 1_500);
		return true;
	}

	/**
	 * Bank open: the arrows for this trip: the best kind the bow (worn, or the best one for the level) fires at this
	 * Ranged level that is worn, in the bag or in the bank. -1 when none.
	 */
	private int arrowTarget()
	{
		int r = Rs2Player.getRealSkillLevel(Skill.RANGED);
		RangedGear.Piece bow = wornBow() != null ? wornBow() : RangedGear.bestBow(r);
		for (int t = RangedGear.bestArrowTier(bow, r); t >= 0; t--)
		{
			int id = RangedGear.ARROWS[t].id;
			if (arrowsWornId() == id || Rs2Inventory.hasItem(id) || Rs2Bank.hasItem(id))
			{
				return id;
			}
		}
		return -1;
	}

	/** Bank open: wear the trip's arrows from the bag, else withdraw up to "Arrows per trip". True when it clicked. */
	private boolean stockArrows(int id)
	{
		if (id <= 0 || wornBow() == null)
		{
			return false;
		}
		String name = RangedGear.ARROWS[RangedGear.arrowTier(id)].name;
		if (Rs2Inventory.hasItem(id))
		{
			status("wielding " + name + "s");
			Rs2Inventory.interact(id, "Wield");
			Global.sleepUntil(() -> !Rs2Inventory.hasItem(id), 2_000);
			return true;
		}
		int worn = arrowsWornId() == id ? arrowsWorn() : 0;
		int want = arrowsPerTrip() - worn;
		int inBank = Rs2Bank.count(id);
		if (want > 0 && inBank > 0 && bankTried.add("arrows" + id))
		{
			status("taking " + Math.min(want, inBank) + " " + name + "s");
			Rs2Bank.withdrawX(id, Math.min(want, inBank));
			Global.sleepUntil(() -> Rs2Inventory.hasItem(id), 2_000);
			return true;
		}
		return false;
	}

	/** Bank open: wields the best shortbow owned that the Ranged level allows. True when it clicked. */
	private boolean wieldBestBow()
	{
		int r = Rs2Player.getRealSkillLevel(Skill.RANGED);
		RangedGear.Piece worn = wornBow();
		for (int i = 0; i < RangedGear.BOWS.length; i++)
		{
			RangedGear.Piece b = RangedGear.BOWS[i];
			// 0.6.0: an owned bow also waits for this account's upgrade timing
			if (RangedGear.lag.usedFrom(GearLag.Kind.BOW, i, b.ranged, i == RangedGear.BOWS.length - 1) > r
				|| cannotWield.contains(b.name))
			{
				continue;
			}
			if (worn != null && worn.name.equalsIgnoreCase(b.name))
			{
				return false; // already wearing the best one we own
			}
			boolean held = Rs2Inventory.hasItem(b.id);
			if (!held && !Rs2Bank.hasItem(b.id))
			{
				continue;
			}
			status("wielding " + b.name);
			if (held)
			{
				Rs2Inventory.interact(b.id, "Wield");
			}
			else
			{
				Rs2Bank.withdrawAndEquip(b.id);
			}
			if (!Global.sleepUntil(() -> Rs2Equipment.isWearing(b.id), 3_000))
			{
				cannotWield.add(b.name);
				Microbot.log(tag() + "could not wield " + b.name + "; trying the next bow");
			}
			return true;
		}
		return false;
	}

	private static boolean dragonSlayerDone()
	{
		return Rs2Player.getQuestState(net.runelite.api.Quest.DRAGON_SLAYER_I) == net.runelite.api.QuestState.FINISHED;
	}

	/** Bank open: wears one better ranged armour piece the account owns and can wear. True when it clicked. */
	private boolean wearRangedArmour()
	{
		return wearArmourSet(RangedGear.SLOTS, RangedGear.ARMOUR);
	}

	/**
	 * Bank open: wears one better piece of a gear set (slots, and each slot's pieces best first) the account owns and
	 * can wear. True when it clicked. Used for the ranged and the magic armour.
	 */
	private boolean wearArmourSet(EquipmentInventorySlot[] slots, RangedGear.Piece[][] armour)
	{
		int r = Rs2Player.getRealSkillLevel(Skill.RANGED);
		int d = Rs2Player.getRealSkillLevel(Skill.DEFENCE);
		boolean ds = dragonSlayerDone();
		for (int i = 0; i < slots.length; i++)
		{
			Rs2ItemModel worn = Rs2Equipment.get(slots[i]);
			if (worn != null && (worn.getId() == COWBELL || worn.getId() == COWBELL_EMPTY))
			{
				continue;
			}
			int wornRank = RangedGear.rank(armour[i], worn == null ? null : worn.getName());
			for (RangedGear.Piece p : armour[i])
			{
				if (!p.usable(r, d, ds) || cannotWear.contains(p.name))
				{
					continue;
				}
				if (RangedGear.rank(armour[i], p.name) <= wornRank)
				{
					break;
				}
				boolean held = Rs2Inventory.hasItem(p.id);
				if (!held && !Rs2Bank.hasItem(p.id))
				{
					continue;
				}
				status("wearing " + p.name);
				if (held)
				{
					Rs2Inventory.interact(p.id, "Wear");
				}
				else
				{
					Rs2Bank.withdrawAndEquip(p.id);
				}
				if (!Global.sleepUntil(() -> Rs2Equipment.isWearing(p.id), 3_000))
				{
					cannotWear.add(p.name);
					Microbot.log(tag() + "could not wear " + p.name + "; skipping it");
				}
				return true;
			}
		}
		return false;
	}

	/** GE orders for ranged mode: the best bow, its arrows, and ranged armour, while the budget lasts. Bank open. */
	private long planRanged(List<GeBuyer.Order> list, StringBuilder text, long budget, int markup)
	{
		int r = Rs2Player.getRealSkillLevel(Skill.RANGED);
		RangedGear.Piece best = RangedGear.bestBow(r);
		RangedGear.Piece worn = wornBow();
		boolean ownBest = (worn != null && worn.name.equalsIgnoreCase(best.name)) || Rs2Inventory.hasItem(best.id)
			|| Rs2Bank.hasItem(best.id);
		RangedGear.Piece bow = ownBest ? best : worn;
		if (!ownBest && !geGaveUp.contains(best.name) && !cannotWield.contains(best.name))
		{
			int unit = GeBuyer.unitPrice(new GeBuyer.Order(best.name, best.id, 1, 0));
			int offer = GeBuyer.offerPrice(unit, markup, 0);
			if (offer <= budget)
			{
				list.add(new GeBuyer.Order(best.name, best.id, 1, unit));
				budget -= offer;
				text.append(text.length() > 0 ? ", " : "").append(best.name);
				bow = best;
			}
			else
			{
				noGold("GE: not buying " + best.name + ": ~" + offer + " gp, " + budget + " gp left");
			}
		}
		if (bow == null)
		{
			bow = best;
		}
		// Arrows: the best kind the bow fires at this level (bronze..adamant: adamant is the top, so upgrading stops
		// there), bought when the bank, bag and quiver hold fewer than a trip's worth of THAT kind (user 2026-10-05:
		// upgrade the arrows). Lower arrows stay in the bank as a fallback; the bank step wears the best kind owned.
		int max = RangedGear.bestArrowTier(bow, r);
		RangedGear.Piece arrow = RangedGear.ARROWS[max];
		long have = Rs2Bank.count(arrow.id) + Rs2Inventory.itemQuantity(arrow.id) + (arrowsWornId() == arrow.id ? arrowsWorn() : 0);
		if (have < arrowsPerTrip() && !geGaveUp.contains(arrow.name))
		{
			int unit = GeBuyer.unitPrice(new GeBuyer.Order(arrow.name, arrow.id, 1, 5));
			int offer = GeBuyer.offerPrice(unit, markup, 0);
			int qty = (int) Math.min(geBatch(Amounts.Kind.ARROWS, GeBuyer.byGold(planCoins, ARROWS_MIN, ARROWS_MAX), ARROWS_MIN / 2), budget / Math.max(1, offer));
			if (qty >= 50)
			{
				list.add(new GeBuyer.Order(arrow.name, arrow.id, qty, unit));
				budget -= (long) offer * qty;
				text.append(text.length() > 0 ? ", " : "").append(qty).append(" x ").append(arrow.name);
			}
			else
			{
				noGold("GE: " + budget + " gp does not buy " + arrow.name + "s (~" + offer + " gp each)");
			}
		}
		if (!config.upgradeArmour())
		{
			return budget;
		}
		return planArmourSet(RangedGear.SLOTS, RangedGear.ARMOUR, list, text, budget, markup);
	}

	/** GE orders for one gear set: each slot's best wearable piece when no piece as good is worn or owned. Bank open. */
	private long planArmourSet(EquipmentInventorySlot[] slots, RangedGear.Piece[][] armour, List<GeBuyer.Order> list,
		StringBuilder text, long budget, int markup)
	{
		int r = Rs2Player.getRealSkillLevel(Skill.RANGED);
		int d = Rs2Player.getRealSkillLevel(Skill.DEFENCE);
		boolean ds = dragonSlayerDone();
		for (int i = 0; i < slots.length; i++)
		{
			Rs2ItemModel w = Rs2Equipment.get(slots[i]);
			if (w != null && (w.getId() == COWBELL || w.getId() == COWBELL_EMPTY))
			{
				continue;
			}
			int wornRank = RangedGear.rank(armour[i], w == null ? null : w.getName());
			RangedGear.Piece buy = null;
			for (RangedGear.Piece p : armour[i])
			{
				if (p.usable(r, d, ds) && !cannotWear.contains(p.name))
				{
					buy = p;
					break;
				}
			}
			if (buy == null || RangedGear.rank(armour[i], buy.name) <= wornRank || geGaveUp.contains(buy.name))
			{
				continue;
			}
			boolean owned = false;
			for (RangedGear.Piece p : armour[i])
			{
				owned |= RangedGear.rank(armour[i], p.name) >= RangedGear.rank(armour[i], buy.name)
					&& (Rs2Inventory.hasItem(p.id) || Rs2Bank.hasItem(p.id));
			}
			if (owned)
			{
				continue;
			}
			int unit = GeBuyer.unitPrice(new GeBuyer.Order(buy.name, buy.id, 1, 0));
			int offer = GeBuyer.offerPrice(unit, markup, 0);
			if (offer > budget)
			{
				noGold("GE: not buying " + buy.name + ": ~" + offer + " gp, " + budget + " gp left");
				continue;
			}
			list.add(new GeBuyer.Order(buy.name, buy.id, 1, unit));
			budget -= offer;
			text.append(text.length() > 0 ? ", " : "").append(buy.name);
		}
		return budget;
	}

	/** Melee scimitars the GE buys, best first, with the Attack level each needs (user 2026-10-09). */
	static final Object[][] SCIMITARS = {{"Rune scimitar", 1333, 40}, {"Adamant scimitar", 1331, 30},
		{"Mithril scimitar", 1329, 20}, {"Iron scimitar", 1323, 1}};

	/** Attack level a weapon in {@link #WEAPONS} needs; -1 when it is not on the list. */
	private static int weaponLevel(String name)
	{
		for (String[] w : WEAPONS)
		{
			if (w[0].equalsIgnoreCase(name))
			{
				return Integer.parseInt(w[1]);
			}
		}
		return -1;
	}

	/**
	 * Melee GE order: the best scimitar for the Attack level (Iron, Mithril 20, Adamant 30, Rune 40) when no weapon as good
	 * is worn or owned. wieldBestWeapon only used weapons already owned, so the scimitar never got upgraded. Bank open.
	 */
	private long planScimitar(List<GeBuyer.Order> list, StringBuilder text, long budget, int markup)
	{
		int attack = Rs2Player.getRealSkillLevel(Skill.ATTACK);
		Object[] target = null;
		for (Object[] sc : SCIMITARS)
		{
			if ((int) sc[2] <= attack && !cannotWield.contains((String) sc[0]))
			{
				target = sc;
				break;
			}
		}
		if (target == null)
		{
			return budget;
		}
		String name = (String) target[0];
		int id = (int) target[1];
		int need = (int) target[2];
		Rs2ItemModel worn = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
		if (worn != null && weaponLevel(worn.getName()) >= need)
		{
			return budget;
		}
		for (String[] w : WEAPONS)
		{
			int lvl = Integer.parseInt(w[1]);
			if (lvl >= need && lvl <= attack && !cannotWield.contains(w[0])
				&& (Rs2Inventory.hasItem(w[0]) || Rs2Bank.hasItem(w[0], true)))
			{
				return budget; // a weapon this good is owned: wieldBestWeapon wears it
			}
		}
		if (geGaveUp.contains(name))
		{
			return budget;
		}
		int unit = GeBuyer.unitPrice(new GeBuyer.Order(name, id, 1, 0));
		int offer = GeBuyer.offerPrice(unit, markup, 0);
		if (offer > budget)
		{
			noGold("GE: not buying " + name + ": ~" + offer + " gp, " + budget + " gp left");
			return budget;
		}
		list.add(new GeBuyer.Order(name, id, 1, unit));
		text.append(text.length() > 0 ? ", " : "").append(name);
		return budget - offer;
	}

	// ------------------------------------------------------------------ armour and the GE

	/** Bank open: wears one better armour piece the account owns (Iron..Rune by Defence). True when it clicked. */
	private boolean wearArmour()
	{
		int def = Rs2Player.getRealSkillLevel(Skill.DEFENCE);
		boolean ds = dragonSlayerDone();
		for (int i = 0; i < Armour.SLOTS.length; i++)
		{
			if (Armour.SLOTS[i] == EquipmentInventorySlot.SHIELD && mooletaKept())
			{
				continue;
			}
			Rs2ItemModel worn = Rs2Equipment.get(Armour.SLOTS[i]);
			// By piece, not tier (user 2026-10-09): a Rune platebody replaces a worn Rune chainbody once Dragon Slayer I
			// is done.
			int wornScore = Armour.score(worn == null ? null : worn.getName());
			for (Armour.Tier t : Armour.TIERS)
			{
				if (t.defence > def)
				{
					continue;
				}
				for (Armour.Piece p : t.slots[i])
				{
					if (cannotWear.contains(p.name) || !p.usable(ds) || Armour.score(p.name) <= wornScore)
					{
						continue;
					}
					boolean held = Rs2Inventory.hasItem(p.id);
					if (!held && !Rs2Bank.hasItem(p.id))
					{
						continue;
					}
					status("wearing " + p.name);
					if (held)
					{
						Rs2Inventory.interact(p.id, "Wear");
					}
					else
					{
						Rs2Bank.withdrawAndEquip(p.id);
					}
					if (!Global.sleepUntil(() -> Rs2Equipment.isWearing(p.id), 3_000))
					{
						cannotWear.add(p.name);
						Microbot.log(tag() + "could not wear " + p.name + "; skipping it");
					}
					return true;
				}
			}
		}
		return wearExtras();
	}

	/** Bank open: puts on an owned Amulet of strength, Leather gloves or Leather boots when that slot lacks one. */
	private boolean wearExtras()
	{
		for (int i = 0; i < Armour.EXTRA_SLOTS.length; i++)
		{
			Rs2ItemModel worn = Rs2Equipment.get(Armour.EXTRA_SLOTS[i]);
			if (worn != null && (Armour.extraWorn(i, worn.getName()) || worn.getId() == COWBELL))
			{
				continue;
			}
			for (Armour.Piece p : Armour.EXTRAS[i])
			{
				if (cannotWear.contains(p.name))
				{
					continue;
				}
				boolean held = Rs2Inventory.hasItem(p.id);
				if (!held && !Rs2Bank.hasItem(p.id))
				{
					continue;
				}
				status("wearing " + p.name);
				if (held)
				{
					Rs2Inventory.interact(p.id, "Wear");
				}
				else
				{
					Rs2Bank.withdrawAndEquip(p.id);
				}
				if (!Global.sleepUntil(() -> Rs2Equipment.isWearing(p.id), 3_000))
				{
					cannotWear.add(p.name);
					Microbot.log(tag() + "could not wear " + p.name + "; skipping it");
				}
				return true;
			}
		}
		return false;
	}

	/** The Mooleta keeps the shield slot: wanted and owned. */
	private boolean mooletaKept()
	{
		return config.equipMooleta() && !ranged() && (Rs2Equipment.isWearing(MOOLETA) || Rs2Inventory.hasItem(MOOLETA)
			|| (Rs2Bank.isOpen() && Rs2Bank.hasItem(MOOLETA)));
	}

	/**
	 * Bank open: the GE shopping list (user 2026-10-03), paid with the bank's coins: the Defence tier's armour for each
	 * slot not owned yet, and with {@code needFood} the chosen food (150-600 by the coins). Food goes
	 * first; each item only when the coins cover its first-offer price (user 2026-10-09: budgeting at the third offer's
	 * price left half the gold unspent and dropped the fire runes; a raised offer the coins miss fails like any). True
	 * when it took the coins and set the list (the next
	 * loops walk to the GE).
	 */
	private boolean planGe(boolean needFood)
	{
		if (!config.buyOnGe())
		{
			return false;
		}
		long coins = (long) Rs2Bank.count(COINS) + Rs2Inventory.itemQuantity(COINS);
		// Live 2026-10-05: the bank visit's gear-only plan found nothing with 23k gp and set the skip; the food plan right
		// after saw the same coins, returned at once and the script went selling loot "because the coins buy none".
		// Live 2026-10-09: a plan with nothing to buy set the skip at 271k gp; the chaos runes ran out later with the same
		// coins, every bank visit skipped the plan and the script stopped "not enough gold". Re-plan when the needs change.
		String needs = geNeeds();
		if (coins == geSkipCoins && needs.equals(geSkipNeeds) && (geSkipFood || !needFood))
		{
			return false;
		}
		shortOfGold = false;
		planCoins = coins;
		int markup = config.gePriceMarkup();
		long budget = coins;
		List<GeBuyer.Order> list = new ArrayList<>();
		StringBuilder text = new StringBuilder();
		if (needFood)
		{
			String name = config.food();
			int id = foodId(name);
			if (id <= 0)
			{
				Microbot.log(tag() + "GE: no item id for " + name + "; pick a common fish in the settings");
			}
			else if (!geGaveUp.contains(name))
			{
				int unit = GeBuyer.unitPrice(new GeBuyer.Order(name, id, 1, 0));
				int offer = GeBuyer.offerPrice(unit, markup, 0);
				int qty = (int) Math.min(geBatch(Amounts.Kind.FOOD, GeBuyer.byGold(planCoins, FOOD_MIN, FOOD_MAX), FOOD_MIN / 2), budget / Math.max(1, offer));
				if (qty >= 5)
				{
					list.add(new GeBuyer.Order(name, id, qty, unit));
					budget -= (long) offer * qty;
					text.append(qty).append(" x ").append(name);
				}
				else
				{
					noGold("GE: " + coins + " gp does not buy food (" + name + " ~" + offer + " gp)");
					// No food left in the bank either: buy whatever food the coins cover instead of stopping the script.
					if (Rs2Bank.count(bankFood(), true) <= 0)
					{
						GeBuyer.Order alt = cheaperFood(name, budget, markup);
						if (alt != null)
						{
							list.add(alt);
							budget -= (long) GeBuyer.offerPrice(alt.fallbackPrice, markup, 0) * alt.qty;
							text.append(alt.qty).append(" x ").append(alt.name).append(" (can't afford ").append(name).append(')');
						}
					}
				}
			}
		}
		// Air runes for the cowbell when the bank and bag are short of one charge-up (user 2026-10-03): ten charge-ups.
		int air = Rs2Bank.count(AIR_RUNE) + Rs2Inventory.itemQuantity(AIR_RUNE);
		if (config.useCowbell() && config.airRunes() > 0 && air < config.airRunes() && !geGaveUp.contains("Air rune"))
		{
			int unit = GeBuyer.unitPrice(new GeBuyer.Order("Air rune", AIR_RUNE, 1, 5));
			int offer = GeBuyer.offerPrice(unit, markup, 0);
			int qty = (int) Math.min(config.airRunes() * 10L, budget / Math.max(1, offer));
			if (qty >= config.airRunes())
			{
				list.add(new GeBuyer.Order("Air rune", AIR_RUNE, qty, unit));
				budget -= (long) offer * qty;
				text.append(text.length() > 0 ? ", " : "").append(qty).append(" x Air rune");
			}
		}
		if (magic())
		{
			budget = planMagic(list, text, budget, markup);
		}
		else if (ranged())
		{
			budget = planRanged(list, text, budget, markup);
		}
		else if (config.upgradeArmour())
		{
			budget = planScimitar(list, text, budget, markup);
			Armour.Tier target = Armour.target(Rs2Player.getRealSkillLevel(Skill.DEFENCE));
			boolean ds = dragonSlayerDone();
			for (int i = 0; i < Armour.SLOTS.length; i++)
			{
				if (Armour.SLOTS[i] == EquipmentInventorySlot.SHIELD && mooletaKept())
				{
					continue;
				}
				Rs2ItemModel worn = Rs2Equipment.get(Armour.SLOTS[i]);
				// The tier's best piece the quest state allows and the GE/wearing did not rule out (Rune platebody with
				// Dragon Slayer I done, else the chainbody), when it beats what is worn and is not owned.
				Armour.Piece buy = null;
				for (Armour.Piece p : target.slots[i])
				{
					if (p.usable(ds) && !geGaveUp.contains(p.name) && !cannotWear.contains(p.name))
					{
						buy = p;
						break;
					}
				}
				if (buy == null || Armour.score(worn == null ? null : worn.getName()) >= Armour.score(buy.name)
					|| Rs2Inventory.hasItem(buy.id) || Rs2Bank.hasItem(buy.id))
				{
					continue;
				}
				int unit = GeBuyer.unitPrice(new GeBuyer.Order(buy.name, buy.id, 1, 0));
				int offer = GeBuyer.offerPrice(unit, markup, 0);
				if (offer > budget)
				{
					noGold("GE: not buying " + buy.name + ": ~" + offer + " gp, " + budget + " gp left");
					continue;
				}
				list.add(new GeBuyer.Order(buy.name, buy.id, 1, unit));
				budget -= offer;
				text.append(text.length() > 0 ? ", " : "").append(buy.name);
			}
			// Amulet of strength, Leather gloves, Leather boots (no Defence requirement).
			for (int i = 0; i < Armour.EXTRA_SLOTS.length; i++)
			{
				Rs2ItemModel worn = Rs2Equipment.get(Armour.EXTRA_SLOTS[i]);
				if (worn != null && (Armour.extraWorn(i, worn.getName()) || worn.getId() == COWBELL))
				{
					continue;
				}
				boolean owned = false;
				for (Armour.Piece p : Armour.EXTRAS[i])
				{
					owned |= !cannotWear.contains(p.name) && (Rs2Inventory.hasItem(p.id) || Rs2Bank.hasItem(p.id));
				}
				Armour.Piece buy = Armour.EXTRAS[i][0];
				if (owned || geGaveUp.contains(buy.name) || cannotWear.contains(buy.name))
				{
					continue;
				}
				int unit = GeBuyer.unitPrice(new GeBuyer.Order(buy.name, buy.id, 1, 0));
				int offer = GeBuyer.offerPrice(unit, markup, 0);
				if (offer > budget)
				{
					noGold("GE: not buying " + buy.name + ": ~" + offer + " gp, " + budget + " gp left");
					continue;
				}
				list.add(new GeBuyer.Order(buy.name, buy.id, 1, unit));
				budget -= offer;
				text.append(text.length() > 0 ? ", " : "").append(buy.name);
			}
		}
		if (list.isEmpty())
		{
			geSkipCoins = coins;
			geSkipNeeds = needs;
			geSkipFood = needFood;
			return false;
		}
		if (Rs2Bank.count(COINS) > 0)
		{
			Rs2Bank.withdrawAll(COINS);
			Global.sleepUntil(() -> Rs2Bank.count(COINS) == 0, 2_000);
		}
		Microbot.log(tag() + "GE: buying " + text + " with " + coins + " gp");
		buyer.reset();
		orders = list;
		stocked = false;
		return true;
	}

	/**
	 * What the GE plan depends on besides the coins, per combat mode (user 2026-10-09: a planning step for melee, ranged
	 * and magic): the levels that pick weapon, armour, bow, arrows and spells, and which supplies are short of a trip
	 * (bank and bag). When it differs from the last empty plan's, the plan runs again. Bank open.
	 */
	private String geNeeds()
	{
		StringBuilder k = new StringBuilder(config.combatMode().name());
		int air = Rs2Bank.count(AIR_RUNE) + Rs2Inventory.itemQuantity(AIR_RUNE);
		k.append(" air:").append(config.useCowbell() && air < config.airRunes());
		k.append(" def:").append(Rs2Player.getRealSkillLevel(Skill.DEFENCE));
		if (magic())
		{
			int level = Rs2Player.getRealSkillLevel(Skill.MAGIC);
			MagicGear.Cast spell = MagicGear.best(level, config.useBlasts());
			int casts = castsPerTrip();
			k.append(" mag:").append(level).append(" staff:").append(staffWorn() || Rs2Inventory.hasItem(MagicGear.STAFF_OF_AIR)
				|| Rs2Bank.hasItem(MagicGear.STAFF_OF_AIR));
			k.append(" cat:").append(Rs2Bank.count(spell.catalytic) + Rs2Inventory.itemQuantity(spell.catalytic) < casts);
			for (int id : MagicGear.ELEMENTS)
			{
				k.append(' ').append(id).append(':').append(Rs2Bank.count(id) + Rs2Inventory.itemQuantity(id) < casts * 5);
			}
		}
		else if (ranged())
		{
			int r = Rs2Player.getRealSkillLevel(Skill.RANGED);
			RangedGear.Piece bow = wornBow();
			k.append(" rng:").append(r).append(" bow:").append(bow == null ? "none" : bow.name);
			if (bow != null)
			{
				RangedGear.Piece arrow = RangedGear.ARROWS[RangedGear.bestArrowTier(bow, r)];
				long have = Rs2Bank.count(arrow.id) + Rs2Inventory.itemQuantity(arrow.id)
					+ (arrowsWornId() == arrow.id ? arrowsWorn() : 0);
				k.append(" arrows:").append(have < arrowsPerTrip());
			}
		}
		else
		{
			k.append(" att:").append(Rs2Player.getRealSkillLevel(Skill.ATTACK)).append(" ds:").append(dragonSlayerDone());
		}
		return k.toString();
	}

	/** A GE plan item the coins don't cover: logged, and the bank step sells loot for it (user 2026-10-09). */
	private void noGold(String message)
	{
		shortOfGold = true;
		Microbot.log(tag() + message);
	}

	/** One step of the GE shopping; when done, back to the bank (which wears the armour and takes the food). */
	private void restock()
	{
		status("GE restock");
		if (buyer.buyAll(orders, config.gePriceMarkup()) != GeBuyer.Result.DONE)
		{
			return;
		}
		for (GeBuyer.Order o : orders)
		{
			if (o.failed())
			{
				geGaveUp.add(o.name());
				Microbot.log(tag() + "GE: could not buy " + o.name());
			}
			else
			{
				Microbot.log(tag() + "GE: bought " + o.qty() + " x " + o.name());
			}
		}
		orders = null;
		geSkipCoins = -1;
		stocked = false;
	}

	/**
	 * The best other food {@code budget} buys: the first of {@link #FOODS} (best first) the coins buy a full trip of, else
	 * the one they buy the most of (at least 5). Null when none.
	 */
	private GeBuyer.Order cheaperFood(String chosen, long budget, int markup)
	{
		GeBuyer.Order most = null;
		for (String f : FOODS)
		{
			int id = foodId(f);
			if (id <= 0 || f.equalsIgnoreCase(chosen) || geGaveUp.contains(f))
			{
				continue;
			}
			int unit = GeBuyer.unitPrice(new GeBuyer.Order(f, id, 1, 0));
			if (unit <= 0)
			{
				continue;
			}
			int offer = GeBuyer.offerPrice(unit, markup, 0);
			int qty = (int) Math.min(geBatch(Amounts.Kind.FOOD, GeBuyer.byGold(planCoins, FOOD_MIN, FOOD_MAX), FOOD_MIN / 2), budget / Math.max(1, offer));
			if (qty >= foodPerTrip())
			{
				return new GeBuyer.Order(f, id, qty, unit);
			}
			if (qty >= 5 && (most == null || qty > most.qty))
			{
				most = new GeBuyer.Order(f, id, qty, unit);
			}
		}
		return most;
	}

	/** GE ids of the foods the script can buy; -1 when unknown. */
	static int foodId(String name)
	{
		switch (name.toLowerCase(Locale.ROOT))
		{
			case "shrimps":
				return ItemID.SHRIMP;
			case "anchovies":
				return ItemID.ANCHOVIES;
			case "sardine":
				return ItemID.SARDINE;
			case "herring":
				return ItemID.HERRING;
			case "mackerel":
				return ItemID.MACKEREL;
			case "cod":
				return ItemID.COD;
			case "trout":
				return ItemID.TROUT;
			case "pike":
				return ItemID.PIKE;
			case "salmon":
				return ItemID.SALMON;
			case "tuna":
				return ItemID.TUNA;
			case "lobster":
				return ItemID.LOBSTER;
			case "bass":
				return ItemID.BASS;
			case "swordfish":
				return ItemID.SWORDFISH;
			case "monkfish":
				return ItemID.MONKFISH;
			case "shark":
				return ItemID.SHARK;
			case "cake":
				return ItemID.CAKE;
			case "bread":
				return ItemID.BREAD;
			case "meat pie":
				return ItemID.MEAT_PIE;
			case "plain pizza":
				return ItemID.PLAIN_PIZZA;
			case "meat pizza":
				return ItemID.MEAT_PIZZA;
			case "cooked karambwan":
				return ItemID.TBWT_COOKED_KARAMBWAN;
			default:
				return -1;
		}
	}

	int foodHeld()
	{
		return config == null ? 0 : food();
	}

	/** For the paint: the charge count when the varbit has one, -1 charged (count unknown), 0 empty. */
	int charges()
	{
		int n = cowbellCharges();
		return n > 0 ? n : cowbellCharged() ? -1 : 0;
	}
}
