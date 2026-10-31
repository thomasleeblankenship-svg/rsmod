package net.runelite.client.plugins.microbot.dabrutus;

import java.security.SecureRandom;
import java.time.LocalDate;
import net.runelite.client.config.ConfigManager;

/**
 * Saves the persona's salt and birth day and the last setup in the account's RuneLite profile ({@code rsprofile}
 * config), so each account keeps its own persona and two accounts on one PC get different ones. The player name is not
 * used: the seed comes from the client's account hash and a random salt.
 */
final class PersonaStore
{
	private static final String SALT = "personaSalt";
	private static final String BORN = "personaBorn";
	private static final String REROLLED = "personaRerolled";
	private static final String PREFIX = "setup.";

	private final ConfigManager config;

	PersonaStore(ConfigManager config)
	{
		this.config = config;
	}

	static long today()
	{
		return LocalDate.now().toEpochDay();
	}

	/** False until the client knows which account is logged in. */
	boolean ready()
	{
		return config.getRSProfileKey() != null;
	}

	/** This account's persona today; made (and saved) on the first call. */
	Persona persona(long accountHash)
	{
		Long salt = getLong(SALT);
		Long born = getLong(BORN);
		if (salt == null || born == null)
		{
			salt = new SecureRandom().nextLong();
			born = today();
			set(SALT, salt);
			set(BORN, born);
		}
		return Persona.of(Persona.seed(accountHash, salt), (int) (today() - born));
	}

	/** Epoch day of the last re-roll, or -1. */
	long lastReroll()
	{
		Long v = getLong(REROLLED);
		return v == null ? -1 : v;
	}

	/** A new persona when the 7 days are up, else null. */
	Persona reroll(long accountHash)
	{
		if (!Persona.canReroll(lastReroll(), today()))
		{
			return null;
		}
		long salt = new SecureRandom().nextLong();
		set(SALT, salt);
		set(BORN, today());
		set(REROLLED, today());
		return Persona.of(Persona.seed(accountHash, salt), 0);
	}

	/** The last setup for this account, or the defaults. */
	Setup loadSetup()
	{
		Setup s = new Setup();
		s.attack = getInt("attack", s.attack, 1, 99);
		s.strength = getInt("strength", s.strength, 1, 99);
		s.defence = getInt("defence", s.defence, 1, 99);
		s.ranged = getInt("ranged", s.ranged, 1, 99);
		s.magic = getInt("magic", s.magic, 1, 99);
		s.personaStrength = getInt("personaStrength", s.personaStrength, 0, 100);
		s.whenReached = getEnum("whenReached", Setup.WhenReached.class, s.whenReached);
		s.lamp = getEnum("lamp", Setup.Lamp.class, s.lamp);
		return s;
	}

	/** Whether this account has a saved setup ("Use last settings" is offered only then). */
	boolean hasSetup()
	{
		return config.getRSProfileConfiguration(DaBrutusConfig.GROUP, PREFIX + "attack") != null;
	}

	void saveSetup(Setup s)
	{
		set(PREFIX + "attack", s.attack);
		set(PREFIX + "strength", s.strength);
		set(PREFIX + "defence", s.defence);
		set(PREFIX + "ranged", s.ranged);
		set(PREFIX + "magic", s.magic);
		set(PREFIX + "personaStrength", s.personaStrength);
		set(PREFIX + "whenReached", s.whenReached.name());
		set(PREFIX + "lamp", s.lamp.name());
	}

	private void set(String key, Object value)
	{
		config.setRSProfileConfiguration(DaBrutusConfig.GROUP, key, String.valueOf(value));
	}

	private Long getLong(String key)
	{
		String v = config.getRSProfileConfiguration(DaBrutusConfig.GROUP, key);
		try
		{
			return v == null ? null : Long.parseLong(v.trim());
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private int getInt(String key, int def, int min, int max)
	{
		Long v = getLong(PREFIX + key);
		return v == null ? def : (int) Math.max(min, Math.min(max, v));
	}

	private <E extends Enum<E>> E getEnum(String key, Class<E> type, E def)
	{
		String v = config.getRSProfileConfiguration(DaBrutusConfig.GROUP, PREFIX + key);
		try
		{
			return v == null ? def : Enum.valueOf(type, v.trim());
		}
		catch (IllegalArgumentException e)
		{
			return def;
		}
	}
}
