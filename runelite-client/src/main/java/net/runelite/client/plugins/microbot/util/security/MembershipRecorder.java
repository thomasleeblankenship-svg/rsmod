package net.runelite.client.plugins.microbot.util.security;

import java.util.concurrent.TimeUnit;
import net.runelite.client.config.ConfigProfile;

/**
 * The membership reads of upstream's {@code util/security/MembershipRecorder} (chsami/Microbot 2.6.47,
 * BSD 2-Clause): how many membership days a profile has left, and whether it is a member, from what was
 * saved at its last login. Mobile: the in-game recorder half is not here; {@link LoginManager#refreshMembership}
 * saves the same profile fields at each login.
 */
public final class MembershipRecorder
{
	private static final long DAY_MILLIS = TimeUnit.DAYS.toMillis(1);

	private MembershipRecorder()
	{
	}

	/** As upstream: days left from the saved credit, {@code -1} when nothing was saved yet. */
	public static long remainingDays(ConfigProfile profile, long now)
	{
		if (profile == null || profile.getMemberExpireDaysTimeStemp() == 0)
		{
			return -1;
		}
		long daysPassed = (now - profile.getMemberExpireDaysTimeStemp()) / DAY_MILLIS;
		return Math.max(0, profile.getMemberExpireDays() - daysPassed);
	}

	/** As upstream: a member while saved days remain; otherwise the saved member flag. */
	public static boolean isMember(ConfigProfile profile, long now)
	{
		if (profile == null)
		{
			return false;
		}
		long days = remainingDays(profile, now);
		if (days > 0)
		{
			return true;
		}
		if (days == 0 && profile.getMemberExpireDays() > 0)
		{
			return false; // the saved days have run out since the last login
		}
		return profile.isMember();
	}

	public static String describe(ConfigProfile profile, long now)
	{
		long days = remainingDays(profile, now);
		if (days < 0)
		{
			return "Unknown until login";
		}
		if (days > 0)
		{
			return "Members · " + days + (days == 1 ? " day left" : " days left");
		}
		return isMember(profile, now) ? "Members" : "Free-to-play";
	}
}
