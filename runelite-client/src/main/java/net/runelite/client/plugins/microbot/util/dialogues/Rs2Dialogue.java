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

	// ---- Dialogue reads requested for the Hub plugins (Microbot API names, written for mobile) ----

	/** Text components of the NPC, player, message-box and item dialogues. */
	private static final int[] DIALOGUE_TEXT = {
		net.runelite.api.widgets.ComponentID.DIALOG_NPC_TEXT,
		net.runelite.api.widgets.ComponentID.DIALOG_PLAYER_TEXT,
		net.runelite.api.gameval.InterfaceID.Messagebox.TEXT,
		net.runelite.api.widgets.ComponentID.DIALOG_SPRITE_TEXT,
		net.runelite.api.widgets.ComponentID.DIALOG_DOUBLE_SPRITE_TEXT,
	};

	/** As upstream: picks the shown option containing {@code text} (ignoring case). */
	public static boolean clickOption(String text)
	{
		return clickOption(text, false);
	}

	/**
	 * As upstream: picks the shown option equal to ({@code exact}) or containing {@code text},
	 * ignoring case and colour tags, by pressing its number key. {@code true} means the key was sent.
	 */
	public static boolean clickOption(String text, boolean exact)
	{
		return text != null && clickOption(exact, text);
	}

	public static boolean clickOption(String... texts)
	{
		return clickOption(false, texts);
	}

	/** As upstream: the first shown option matching any of {@code texts}. */
	public static boolean clickOption(boolean exact, String... texts)
	{
		int number = MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget menu = Microbot.getClient().getWidget(net.runelite.api.widgets.ComponentID.DIALOG_OPTION_OPTIONS);
			net.runelite.api.widgets.Widget[] rows = menu == null || menu.isHidden() ? null : menu.getDynamicChildren();
			if (rows == null)
			{
				return -1;
			}
			// Row 0 is the "Select an option" title; options are numbered from 1 in screen order.
			int option = 0;
			for (int i = 1; i < rows.length; i++)
			{
				if (rows[i] == null || rows[i].isHidden() || rows[i].getText() == null || rows[i].getText().isBlank())
				{
					continue;
				}
				option++;
				for (String text : texts)
				{
					if (text != null && textMatches(rows[i].getText(), text, exact))
					{
						return option;
					}
				}
			}
			return -1;
		}, -1);
		return number >= 1 && number <= 9 && MobileActions.key(KeyEvent.VK_0 + number);
	}

	/** Whether a visible "Click here to continue" prompt is showing. */
	public static boolean hasContinue()
	{
		return MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget prompt = Rs2Widget.findWidget("click here to continue", null, false);
			return prompt != null && !prompt.isHidden();
		}, false);
	}

	/** Whether the "Select an option" menu is showing. */
	public static boolean hasSelectAnOption()
	{
		return Rs2Widget.isWidgetVisible(net.runelite.api.widgets.ComponentID.DIALOG_OPTION_OPTIONS);
	}

	/**
	 * As upstream: the title row of the "Select an option" menu without colour tags, or
	 * {@code null} when no option menu is showing.
	 */
	public static String getQuestion()
	{
		if (!hasSelectAnOption())
		{
			return null;
		}
		String text = MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget menu = Microbot.getClient().getWidget(net.runelite.api.widgets.ComponentID.DIALOG_OPTION_OPTIONS);
			net.runelite.api.widgets.Widget[] rows = menu == null ? null : menu.getDynamicChildren();
			return rows == null || rows.length == 0 || rows[0] == null ? null : rows[0].getText();
		}, null);
		return text == null ? null : net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper.stripColTags(text);
	}

	/** Whether the option menu's question contains {@code text}, ignoring case. */
	public static boolean hasQuestion(String text)
	{
		return hasQuestion(text, false);
	}

	/** Whether the option menu's question equals ({@code exact}) or contains {@code text}, ignoring case. */
	public static boolean hasQuestion(String text, boolean exact)
	{
		String question = getQuestion();
		if (question == null || text == null)
		{
			return false;
		}
		return exact ? question.equalsIgnoreCase(text) : question.toLowerCase().contains(text.toLowerCase());
	}

	/** Whether the open dialogue's text contains {@code text}, ignoring case and colour tags. */
	public static boolean hasDialogueText(String text)
	{
		return hasDialogueText(text, false);
	}

	/** Whether the open dialogue's text equals ({@code exact}) or contains {@code text}, ignoring case. */
	public static boolean hasDialogueText(String text, boolean exact)
	{
		if (text == null)
		{
			return false;
		}
		return MobileActions.read(() ->
		{
			for (int id : DIALOGUE_TEXT)
			{
				net.runelite.api.widgets.Widget widget = Microbot.getClient().getWidget(id);
				if (widget != null && !widget.isHidden() && textMatches(widget.getText(), text, exact))
				{
					return true;
				}
			}
			return false;
		}, false);
	}

	/** Whether one of the shown options contains {@code text}, ignoring case. */
	public static boolean hasDialogueOption(String text)
	{
		return hasDialogueOption(text, false);
	}

	/** Whether one of the shown options equals ({@code exact}) or contains {@code text}, ignoring case. */
	public static boolean hasDialogueOption(String text, boolean exact)
	{
		if (text == null)
		{
			return false;
		}
		return MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget menu = Microbot.getClient().getWidget(net.runelite.api.widgets.ComponentID.DIALOG_OPTION_OPTIONS);
			net.runelite.api.widgets.Widget[] rows = menu == null || menu.isHidden() ? null : menu.getDynamicChildren();
			if (rows == null)
			{
				return false;
			}
			// Row 0 is the "Select an option" title, not an option.
			for (int i = 1; i < rows.length; i++)
			{
				if (rows[i] != null && !rows[i].isHidden() && textMatches(rows[i].getText(), text, exact))
				{
					return true;
				}
			}
			return false;
		}, false);
	}

	private static boolean textMatches(String shown, String text, boolean exact)
	{
		if (shown == null)
		{
			return false;
		}
		// Dialogue text carries colour tags and <br> line breaks; compare the words the player reads.
		String clean = shown.replace("<br>", " ").replaceAll("<[^>]*>", "").trim().toLowerCase(java.util.Locale.ROOT);
		String wanted = text.trim().toLowerCase(java.util.Locale.ROOT);
		return exact ? clean.equals(wanted) : clean.contains(wanted);
	}
}
