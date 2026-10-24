package net.runelite.client.plugins.microbot.kspsupport;

/**
 * Mobile stand-in for KSP's break-handler settings.
 * <p>
 * Kept so KSP plugins compile unchanged. KSP's randomized logout breaks (and the automatic
 * re-login after them) are scheduled-break anti-detection behaviour, which RuneMobile does not
 * port, so this declares only the section name and no settings: nothing appears in the panel.
 */
public interface KspBreakConfig
{
	String SECTION = "breakHandler";
}
