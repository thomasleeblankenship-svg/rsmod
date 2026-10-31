package net.runelite.client.plugins.microbot.dabrutus;

import net.runelite.api.Experience;
import net.runelite.api.Skill;

/** What the player chose in the setup window (0.6.0). Saved per account by {@link PersonaStore}. */
final class Setup
{
	enum WhenReached
	{
		LOG_OUT("Log out"), KEEP_GOING("Keep farming loot"), STOP("Stop, stay logged in");

		final String label;

		WhenReached(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum Lamp
	{
		PERSONA(null, "Persona pick"), ATTACK(Skill.ATTACK, "Attack"), STRENGTH(Skill.STRENGTH, "Strength"),
		DEFENCE(Skill.DEFENCE, "Defence"), HITPOINTS(Skill.HITPOINTS, "Hitpoints"), RANGED(Skill.RANGED, "Ranged"),
		MAGIC(Skill.MAGIC, "Magic"), PRAYER(Skill.PRAYER, "Prayer");

		final Skill skill;
		final String label;

		Lamp(Skill skill, String label)
		{
			this.skill = skill;
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	static final int DEFAULT_TARGET = 40;
	static final int DEFAULT_STRENGTH = 55;

	int attack = DEFAULT_TARGET;
	int strength = DEFAULT_TARGET;
	int defence = DEFAULT_TARGET;
	int ranged = DEFAULT_TARGET;
	int magic = DEFAULT_TARGET;
	WhenReached whenReached = WhenReached.LOG_OUT;
	Lamp lamp = Lamp.PERSONA;
	/** Persona strength, 0-100: how far the persona may bend the choices left on "Persona pick". */
	int personaStrength = DEFAULT_STRENGTH;

	/** Mobile: the choices from the "Goal and persona" settings instead of the desktop window. */
	static Setup fromConfig(DaBrutusConfig config)
	{
		Setup s = new Setup();
		s.attack = config.goalAttack();
		s.strength = config.goalStrength();
		s.defence = config.goalDefence();
		s.ranged = config.goalRanged();
		s.magic = config.goalMagic();
		s.whenReached = WhenReached.valueOf(config.goalReached().name());
		s.lamp = Lamp.valueOf(config.goalLamp().name());
		s.personaStrength = config.personaStrength();
		return s;
	}

	Setup copy()
	{
		Setup s = new Setup();
		s.attack = attack;
		s.strength = strength;
		s.defence = defence;
		s.ranged = ranged;
		s.magic = magic;
		s.whenReached = whenReached;
		s.lamp = lamp;
		s.personaStrength = personaStrength;
		return s;
	}

	/** The goal for the mode being trained: all three melee targets, or the Ranged / Magic one. */
	boolean reached(DaBrutusConfig.CombatMode mode, int att, int str, int def, int rng, int mag)
	{
		switch (mode)
		{
			case RANGED:
				return rng >= ranged;
			case MAGIC:
				return mag >= magic;
			default:
				return att >= attack && str >= strength && def >= defence;
		}
	}

	/** Melee targets for {@link StyleBlocks}, in Attack, Strength, Defence order. */
	int[] meleeTargets()
	{
		return new int[]{attack, strength, defence};
	}

	/**
	 * Combat level once the goal is reached. Hitpoints is estimated: it gets a third of the combat xp trained on the way
	 * (4 xp per damage to the skill, 1.33 to Hitpoints). Levels already above a target stay where they are.
	 */
	static int combatAtGoal(DaBrutusConfig.CombatMode mode, Setup s, int att, int str, int def, int hp, int rng, int mag,
		int prayer)
	{
		int a = att, st = str, d = def, r = rng, m = mag;
		long gained;
		if (mode == DaBrutusConfig.CombatMode.RANGED)
		{
			r = Math.max(rng, s.ranged);
			gained = xpBetween(rng, r);
		}
		else if (mode == DaBrutusConfig.CombatMode.MAGIC)
		{
			m = Math.max(mag, s.magic);
			gained = xpBetween(mag, m);
		}
		else
		{
			a = Math.max(att, s.attack);
			st = Math.max(str, s.strength);
			d = Math.max(def, s.defence);
			gained = xpBetween(att, a) + xpBetween(str, st) + xpBetween(def, d);
		}
		int hpLevel = Math.min(Experience.MAX_REAL_LEVEL, Experience.getLevelForXp((int) Math.min(Experience.MAX_SKILL_XP,
			Experience.getXpForLevel(Math.max(10, hp)) + gained / 3)));
		return Experience.getCombatLevel(a, st, d, Math.max(hp, hpLevel), m, r, prayer);
	}

	private static long xpBetween(int from, int to)
	{
		return to <= from ? 0 : Experience.getXpForLevel(to) - Experience.getXpForLevel(Math.max(1, from));
	}
}
