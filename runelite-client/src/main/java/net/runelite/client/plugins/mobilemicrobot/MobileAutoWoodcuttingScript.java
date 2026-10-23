package net.runelite.client.plugins.mobilemicrobot;

import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.microbot.MicrobotScriptAdapter;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingConfig;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingScript;
import net.runelite.client.ui.overlay.OverlayManager;

/** Owns the uploaded script, Forestry events and its input permission as one lifecycle. */
public final class MobileAutoWoodcuttingScript implements MobileScript
{
	private final AutoWoodcuttingConfig config;
	private final OverlayManager overlays;
	private AutoWoodcuttingPlugin host;
	private MicrobotScriptAdapter<AutoWoodcuttingScript> adapter;
	private ScriptInputSession permission;

	MobileAutoWoodcuttingScript(AutoWoodcuttingConfig config, OverlayManager overlays)
	{
		this.config = config;
		this.overlays = overlays;
	}

	@Override
	public void onStart(MobileScriptContext context)
	{
		permission = context.inputPermission();
		MobileActions.bind(permission);
		try
		{
			host = new AutoWoodcuttingPlugin(config, overlays);
			adapter = new MicrobotScriptAdapter<>("Auto Woodcutting", () -> host.autoWoodcuttingScript,
				script ->
				{
					host.startMobile();
					return script.run(config);
				});
			adapter.onStart(context);
		}
		catch (RuntimeException error)
		{
			onStop(context);
			throw error;
		}
	}

	@Override
	public void onGameTick(MobileScriptContext context)
	{
		adapter.onGameTick(context);
	}

	@Override
	public void onStop(MobileScriptContext context)
	{
		MobileActions.unbind(permission);
		try
		{
			if (adapter != null)
			{
				adapter.onStop(context);
			}
		}
		finally
		{
			try
			{
				if (host != null)
				{
					host.stopMobile();
				}
			}
			finally
			{
				host = null;
				adapter = null;
				permission = null;
			}
		}
	}

	@Override
	public boolean finished()
	{
		return adapter != null && adapter.getScript() != null && !adapter.getScript().isRunning();
	}

	@Override
	public String status()
	{
		return adapter == null ? "Auto Woodcutting: stopped" : adapter.status()
			+ "; " + net.runelite.client.plugins.microbot.Microbot.status;
	}
}
