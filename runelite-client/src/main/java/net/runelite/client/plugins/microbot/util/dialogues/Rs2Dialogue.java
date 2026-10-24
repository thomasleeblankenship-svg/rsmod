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
	/** Upstream's {@code getDialogueText} widgets (group, child), in its order. */
	private static final int[][] DIALOGUE_TEXT_WIDGETS = {{229, 1}, {229, 3}, {231, 6}};
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
		String text = optionTitleText();
		return text == null ? null : net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper.stripColTags(text);
	}

	/** The option menu's title row as the client holds it (tags included), or {@code null}. */
	private static String optionTitleText()
	{
		return MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget menu = Microbot.getClient().getWidget(net.runelite.api.widgets.ComponentID.DIALOG_OPTION_OPTIONS);
			net.runelite.api.widgets.Widget[] rows = menu == null ? null : menu.getDynamicChildren();
			return rows == null || rows.length == 0 || rows[0] == null ? null : rows[0].getText();
		}, null);
	}

	/**
	 * As upstream: whether the option menu's title equals ({@code exact}) or contains {@code text},
	 * ignoring case. Unlike {@link #getQuestion()} the title is compared with its tags, as upstream.
	 */
	public static boolean hasDialogueOptionTitle(String text, boolean exact)
	{
		if (text == null || !hasSelectAnOption())
		{
			return false;
		}
		String title = optionTitleText();
		if (title == null)
		{
			return false;
		}
		return exact ? title.equalsIgnoreCase(text) : title.toLowerCase().contains(text.toLowerCase());
	}

	public static boolean hasDialogueOptionTitle(String text)
	{
		return hasDialogueOptionTitle(text, false);
	}

	/** As upstream: waits (upstream's default wait) until an option containing {@code text} shows. */
	/** As upstream: waits (upstream's default wait) until a dialogue is showing. */
	public static boolean sleepUntilInDialogue()
	{
		return net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue(Rs2Dialogue::isInDialogue);
	}

	/** As upstream: waits until no dialogue is showing. */
	public static boolean sleepUntilNotInDialogue()
	{
		return net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue(() -> !isInDialogue());
	}

	public static boolean sleepUntilHasDialogueOption(String text)
	{
		return sleepUntilHasDialogueOption(text, false);
	}

	public static boolean sleepUntilHasDialogueOption(String text, boolean exact)
	{
		return net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue(() -> hasDialogueOption(text, exact));
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

	/** As upstream: the shown option containing {@code text}, ignoring case, or {@code null}. */
	public static net.runelite.api.widgets.Widget getDialogueOption(String text)
	{
		return getDialogueOption(text, false);
	}

	/**
	 * As upstream: the shown option equal to ({@code exact}) or containing {@code text}, ignoring
	 * case, while "Select an option" is up; {@code null} otherwise. A handle for null checks and
	 * {@code Rs2Widget.clickWidget}.
	 */
	public static net.runelite.api.widgets.Widget getDialogueOption(String text, boolean exact)
	{
		if (text == null || !hasSelectAnOption())
		{
			return null;
		}
		return MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget menu = Microbot.getClient().getWidget(net.runelite.api.widgets.ComponentID.DIALOG_OPTION_OPTIONS);
			net.runelite.api.widgets.Widget[] rows = menu == null || menu.isHidden() ? null : menu.getDynamicChildren();
			for (int i = 1; rows != null && i < rows.length; i++)
			{
				if (rows[i] != null && !rows[i].isHidden() && textMatches(rows[i].getText(), text, exact))
				{
					return rows[i];
				}
			}
			return null;
		}, null);
	}

	/**
	 * As upstream: the open dialogue's text without colour tags, from the NPC, player or sprite
	 * dialogue (229:1, 229:3, 231:6); empty when no dialogue or text is showing.
	 */
	public static String getDialogueText()
	{
		if (!isInDialogue())
		{
			return "";
		}
		return MobileActions.read(() ->
		{
			for (int[] id : DIALOGUE_TEXT_WIDGETS)
			{
				net.runelite.api.widgets.Widget widget = Microbot.getClient().getWidget(id[0], id[1]);
				String text = widget == null || widget.isHidden() ? null : widget.getText();
				if (text != null && !text.isEmpty())
				{
					return net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper.stripColTags(text);
				}
			}
			return "";
		}, "");
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

	/** As upstream: whether the "make X" combination interface (270:1) is showing. */
	public static boolean hasCombinationDialogue()
	{
		return Rs2Widget.isWidgetVisible(270, 1);
	}
}
