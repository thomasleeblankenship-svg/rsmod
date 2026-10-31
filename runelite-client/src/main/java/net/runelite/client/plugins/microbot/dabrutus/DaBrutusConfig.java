package net.runelite.client.plugins.microbot.dabrutus;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(DaBrutusConfig.GROUP)
public interface DaBrutusConfig extends Config
{
	String GROUP = "dabrutus";

	@ConfigSection(name = "Quest", description = "The Ides of Milk (needed for Brutus)", position = 0)
	String questSection = "quest";
	@ConfigSection(name = "Supplies", description = "Food and the cowbell amulet", position = 1)
	String suppliesSection = "supplies";
	@ConfigSection(name = "Combat", description = "Prayer, styles and dodging", position = 2)
	String combatSection = "combat";
	@ConfigSection(name = "Loot", description = "Loot and bones", position = 3)
	String lootSection = "loot";
	@ConfigSection(name = "Gear and GE", description = "Armour upgrades and Grand Exchange buying", position = 4)
	String geSection = "ge";
	// Mobile: the desktop setup window's choices (Android has no Swing window).
	@ConfigSection(name = "Goal and persona", description = "What the desktop setup window asks: the goal, what to do "
		+ "when it is reached, the lamp skill and the persona strength", position = 5)
	String goalSection = "goalSection";

	// ------------------------------------------------------------------ quest

	@ConfigItem(keyName = "doQuest", name = "Do The Ides of Milk", position = 0, section = questSection,
		description = "When the quest is not done, do it first (Da-Quester's steps), then fight Brutus")
	default boolean doQuest()
	{
		return true;
	}

	// ------------------------------------------------------------------ supplies

	@ConfigItem(keyName = "food", name = "Food", position = 0, section = suppliesSection,
		description = "Food name as in the bank (wiki F2P setup: 16 trout)")
	default String food()
	{
		return "Trout";
	}

	@ConfigItem(keyName = "foodAmount", name = "Food per trip", position = 1, section = suppliesSection,
		description = "Food taken from the bank each trip")
	@Range(min = 1, max = 26)
	default int foodAmount()
	{
		return 16;
	}

	@ConfigItem(keyName = "eatAtPercent", name = "Eat at HP %", position = 2, section = suppliesSection,
		description = "Eat at or below this. Specials hit up to 19, so keep it above 19 HP")
	@Range(min = 20, max = 90)
	default int eatAtPercent()
	{
		return 55;
	}

	@ConfigItem(keyName = "leaveFood", name = "Leave at food", position = 3, section = suppliesSection,
		description = "Leave the pen to bank when this much food is left and HP is below the eat line")
	@Range(min = 0, max = 10)
	default int leaveFood()
	{
		return 0;
	}

	@ConfigItem(keyName = "useCowbell", name = "Cowbell teleport", position = 4, section = suppliesSection,
		description = "Teleport to the cow field with the charged cowbell amulet instead of walking")
	default boolean useCowbell()
	{
		return true;
	}

	@ConfigItem(keyName = "airRunes", name = "Air runes to charge", position = 5, section = suppliesSection,
		description = "Air runes added to the cowbell amulet when it has fewer than 5 charges (1 rune = 1 charge)")
	@Range(min = 0, max = 1000)
	default int airRunes()
	{
		return 50;
	}

	@ConfigItem(keyName = "ringCowbell", name = "Ring for fast respawn", position = 6, section = suppliesSection,
		description = "After a kill, ring the cowbell amulet: Brutus is back in 7.8 s instead of 21.6 s")
	default boolean ringCowbell()
	{
		return true;
	}

	// ------------------------------------------------------------------ combat

	@ConfigItem(keyName = "protectMelee", name = "Protect from Melee", position = 0, section = combatSection,
		description = "Pray melee while he is up (Prayer 43). Only blocks his max-3 basic hit; specials ignore prayer")
	default boolean protectMelee()
	{
		return true;
	}

	@ConfigItem(keyName = "altar", name = "Recharge at the church", position = 1, section = combatSection,
		description = "Pray at Lumbridge church altar after banking when Prayer is below half")
	default boolean altar()
	{
		return true;
	}

	@ConfigItem(keyName = "rotateStyles", name = "Rotate styles", position = 2, section = combatSection,
		description = "Train the lowest of Attack, Strength and Defence in blocks: 3-7 levels, 1-3 levels once it is 50+ "
			+ "(off: keep the current style)")
	default boolean rotateStyles()
	{
		return true;
	}

	@ConfigItem(keyName = "equipMooleta", name = "Wear Mooleta", position = 3, section = combatSection,
		description = "Wear a Mooleta (shield slot) once one is owned, when the weapon is one-handed")
	default boolean equipMooleta()
	{
		return true;
	}

	@ConfigItem(keyName = "slamWait", name = "Stay back for slams", position = 4, section = combatSection,
		description = "After a snort, stand two tiles off until the slams are over, then attack again (safest)")
	default boolean slamWait()
	{
		return true;
	}

	@ConfigItem(keyName = "combatMode", name = "Combat mode", position = 6, section = combatSection,
		description = "Melee: best melee weapon and armour, style rotation. Ranged: the best F2P shortbow, arrows and "
			+ "ranged armour (Coif, leather/studded/green d'hide), rapid style. Magic: Staff of air, autocasts the best "
			+ "strike/bolt spell for the level with earth/fire/water runes and mind (strikes) or chaos (bolts) runes; the blasts too with Use blast spells")
	default CombatMode combatMode()
	{
		return CombatMode.MELEE;
	}

	enum CombatMode
	{
		MELEE, RANGED, MAGIC
	}

	@ConfigItem(keyName = "arrowsPerTrip", name = "Arrows per trip", position = 7, section = combatSection,
		description = "Ranged: arrows worn when leaving the bank")
	@Range(min = 50, max = 5000)
	default int arrowsPerTrip()
	{
		return 300;
	}

	@ConfigItem(keyName = "useBlasts", name = "Use blast spells", position = 9, section = combatSection,
		description = "Magic: from Magic 41 cast Wind/Water/Earth/Fire Blast with death runes (bought at the GE). Off: the "
			+ "bolts with chaos runes only (cheaper)")
	default boolean useBlasts()
	{
		return false;
	}

	@ConfigItem(keyName = "castsPerTrip", name = "Casts per trip", position = 8, section = combatSection,
		description = "Magic: runes for this many casts are taken from the bank each trip")
	@Range(min = 50, max = 5000)
	default int castsPerTrip()
	{
		return 300;
	}

	@ConfigItem(keyName = "maxKills", name = "Stop after kills", position = 5, section = combatSection,
		description = "Stop after this many kills (0 = keep going)")
	@Range(min = 0, max = 10000)
	default int maxKills()
	{
		return 0;
	}

	// ------------------------------------------------------------------ loot

	@ConfigItem(keyName = "loot", name = "Loot", position = 0, section = lootSection,
		description = "Pick up his drops (uniques, steaks, clues, coins...)")
	default boolean loot()
	{
		return true;
	}

	@ConfigItem(keyName = "buryBones", name = "Bury bones", position = 1, section = lootSection,
		description = "Bury his Bull bones for Prayer xp (off: leave them)")
	default boolean buryBones()
	{
		return true;
	}

	@ConfigItem(keyName = "skipLoot", name = "Don't pick up", position = 2, section = lootSection,
		description = "Comma-separated item names left on the ground")
	default String skipLoot()
	{
		return "Logs";
	}

	// ------------------------------------------------------------------ gear and GE

	@ConfigItem(keyName = "upgradeArmour", name = "Upgrade armour", position = 0, section = geSection,
		description = "Wear Iron, then Mithril (Defence 20), Adamant (30) and Rune (40): full helm, platebody, platelegs, "
			+ "kiteshield (Rune: chainbody). The Mooleta keeps the shield slot when worn")
	default boolean upgradeArmour()
	{
		return true;
	}

	@ConfigItem(keyName = "buyOnGe", name = "Buy at the GE", position = 1, section = geSection,
		description = "Buy missing armour, and food when the bank runs out, at the Grand Exchange with the bank's coins")
	default boolean buyOnGe()
	{
		return true;
	}

	@ConfigItem(keyName = "sellLootWhenOutOfFood", name = "Sell loot when short of gold", position = 4, section = geSection,
		description = "When the coins don't cover a GE restock (food, runes, arrows, gear), sell Brutus's drops (t-bone steaks, "
			+ "runes, cowhides...) on the GE, then plan the restock again (at most once per 10 min; runes are kept in Magic mode)")
	default boolean sellLootWhenOutOfFood()
	{
		return true;
	}

	@ConfigItem(keyName = "castsToBuy", name = "Casts per GE buy", position = 6, section = geSection,
		description = "Magic: mind, chaos or death runes for this many casts one GE restock buys (water, earth and fire runes: 1500-6500 each by the coins)")
	@Range(min = 100, max = 20000)
	default int castsToBuy()
	{
		return 1000;
	}

	@ConfigItem(keyName = "gePriceMarkup", name = "GE markup %", position = 3, section = geSection,
		description = "Offer this much above the guide price so buys fill quickly")
	@Range(min = 0, max = 300)
	default int gePriceMarkup()
	{
		return 30;
	}

	// ------------------------------------------------------------------ goal and persona (mobile)

	@ConfigItem(keyName = "goalAttack", name = "Attack goal", position = 0, section = goalSection,
		description = "Melee: train Attack up to this level")
	@Range(min = 1, max = 99)
	default int goalAttack()
	{
		return 40;
	}

	@ConfigItem(keyName = "goalStrength", name = "Strength goal", position = 1, section = goalSection,
		description = "Melee: train Strength up to this level")
	@Range(min = 1, max = 99)
	default int goalStrength()
	{
		return 40;
	}

	@ConfigItem(keyName = "goalDefence", name = "Defence goal", position = 2, section = goalSection,
		description = "Melee: train Defence up to this level")
	@Range(min = 1, max = 99)
	default int goalDefence()
	{
		return 40;
	}

	@ConfigItem(keyName = "goalRanged", name = "Ranged goal", position = 3, section = goalSection,
		description = "Ranged mode: train Ranged up to this level")
	@Range(min = 1, max = 99)
	default int goalRanged()
	{
		return 40;
	}

	@ConfigItem(keyName = "goalMagic", name = "Magic goal", position = 4, section = goalSection,
		description = "Magic mode: train Magic up to this level")
	@Range(min = 1, max = 99)
	default int goalMagic()
	{
		return 40;
	}

	@ConfigItem(keyName = "goalReached", name = "When the goal is reached", position = 5, section = goalSection,
		description = "Log out, keep farming loot, or stop and stay logged in")
	default GoalReached goalReached()
	{
		return GoalReached.LOG_OUT;
	}

	enum GoalReached
	{
		LOG_OUT, KEEP_GOING, STOP
	}

	@ConfigItem(keyName = "goalLamp", name = "Lamp skill", position = 6, section = goalSection,
		description = "The Ides of Milk's 1,000 xp lamp: a skill, or the persona's pick")
	default LampSkill goalLamp()
	{
		return LampSkill.PERSONA;
	}

	enum LampSkill
	{
		PERSONA, ATTACK, STRENGTH, DEFENCE, HITPOINTS, RANGED, MAGIC, PRAYER
	}

	@ConfigItem(keyName = "personaStrength", name = "Persona strength %", position = 7, section = goalSection,
		description = "How far the account's persona bends style order, amounts and upgrade timing (0 = the 0.5.26 rules)")
	@Range(min = 0, max = 100)
	default int personaStrength()
	{
		return 55;
	}
}
