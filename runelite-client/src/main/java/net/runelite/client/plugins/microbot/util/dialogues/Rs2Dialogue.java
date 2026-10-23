package net.runelite.client.plugins.microbot.util.dialogues;

import java.awt.event.KeyEvent;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/** Continue-only dialogue support used by the Forestry forester conversation. */
public final class Rs2Dialogue
{
	private Rs2Dialogue() { }
	public static boolean isInDialogue()
	{
		if (!MobileActions.active()) { return false; }
		return Rs2Widget.findWidget("click here to continue", null, false) != null
			|| MobileActions.read(() ->
			{
				net.runelite.api.widgets.Widget widget = Microbot.getClient().getWidget(WidgetInfo.DIALOG_NPC_TEXT);
				return widget != null && !widget.isHidden();
			}, false);
	}
	public static boolean clickContinue()
	{
		if (!isInDialogue()) { return false; }
		boolean accepted = MobileActions.key(KeyEvent.VK_SPACE);
		// Do not spin a background loop while the engine applies the continue event.
		Rs2Random.waitEx(150, 20);
		return accepted;
	}
}
