package net.runelite.client.plugins.microbot.kspmule;

/**
 * Mobile stand-in for KSP's local-mule settings.
 * <p>
 * Kept so KSP plugins compile unchanged. KSP's mule service hands gathered items to a separate
 * collector account; RuneMobile does not port item transfers between accounts, so this declares
 * only the section name and no settings.
 */
public interface KspMuleConfig
{
	String SECTION = "kspLocalMule";
}
