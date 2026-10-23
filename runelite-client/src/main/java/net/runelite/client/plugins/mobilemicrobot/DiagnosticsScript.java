package net.runelite.client.plugins.mobilemicrobot;

/** Read-only script proving access to live RuneLite state from the mobile JVM. */
public final class DiagnosticsScript implements MobileScript
{
	private int ticks;
	private String status = "Ready";

	@Override
	public void onGameTick(MobileScriptContext context)
	{
		ticks++;
		if (context.client().getLocalPlayer() != null)
		{
			status = "Tick " + ticks + "; player=" + context.client().getLocalPlayer().getName()
				+ "; inventoryFull=" + context.inventoryFull();
		}
		else
		{
			status = "Tick " + ticks + "; no player";
		}
	}

	@Override
	public String status()
	{
		return status;
	}
}
