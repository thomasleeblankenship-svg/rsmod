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

	// ---- Actions (Claude 1): util-gap batch C ----

	/**
	 * As upstream: picks the option Quest Helper marks (its text starts with "[") by pressing that
	 * option's number. False when no option is marked or the press was not sent.
	 */
	public static boolean handleQuestOptionDialogueSelection()
	{
		if (!hasSelectAnOption())
		{
			return false;
		}
		int marked = MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget menu = Microbot.getClient().getWidget(net.runelite.api.widgets.ComponentID.DIALOG_OPTION_OPTIONS);
			net.runelite.api.widgets.Widget[] rows = menu == null || menu.isHidden() ? null : menu.getDynamicChildren();
			int number = 0;
			for (int i = 1; rows != null && i < rows.length; i++)
			{
				if (rows[i] == null || rows[i].isHidden() || rows[i].getText() == null || rows[i].getText().isBlank())
				{
					continue;
				}
				number++;
				if (rows[i].getText().startsWith("["))
				{
					return number;
				}
			}
			return 0;
		}, 0);
		return marked > 0 && keyPressForDialogueOption(marked);
	}

	// ---- Util action batch S (Claude 1): dialogue waits ----

	/** As upstream: waits (bounded) until a combination option named {@code text} shows, exactly or containing it. */
	public static boolean sleepUntilHasCombinationOption(String text, boolean exact)
	{
		return text != null && net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue(() -> combinationOptionTarget(text, exact) != null);
	}

	/** As upstream: {@link #sleepUntilHasCombinationOption(String, boolean)} by containment. */
	public static boolean sleepUntilHasCombinationOption(String text)
	{
		return sleepUntilHasCombinationOption(text, false);
	}

	/** As upstream: waits (bounded) until the dialogue text matches. */
	public static boolean sleepUntilHasDialogueText(String text, boolean exact)
	{
		return net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue(() -> hasDialogueText(text, exact));
	}

	/** As upstream: {@link #sleepUntilHasDialogueText(String, boolean)} by containment. */
	public static boolean sleepUntilHasDialogueText(String text)
	{
		return sleepUntilHasDialogueText(text, false);
	}

	/** As upstream: waits (bounded) until the dialogue question matches. */
	public static boolean sleepUntilHasQuestion(String text, boolean exact)
	{
		return net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue(() -> hasQuestion(text, exact));
	}

	/** As upstream: {@link #sleepUntilHasQuestion(String, boolean)} by containment. */
	public static boolean sleepUntilHasQuestion(String text)
	{
		return sleepUntilHasQuestion(text, false);
	}

	/** As upstream: a cutscene is playing (varbit 542). */
	public static boolean isInCutScene()
	{
		return Microbot.getVarbitValue(542) == 1;
	}

	/** As upstream: waits up to 5 s for a cutscene to start, then up to 5 s for it to end. */
	public static void waitForCutScene()
	{
		waitForCutScene(100, 5000);
	}

	/** As upstream: polls every {@code time} ms, up to {@code timeout} ms, for a cutscene to start and then end. */
	public static void waitForCutScene(int time, int timeout)
	{
		if (net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue(Rs2Dialogue::isInCutScene, time, timeout))
		{
			net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue(() -> !isInCutScene(), time, timeout);
		}
	}

	/** As upstream: answers "Yes" to a "Would you like to start ... quest?" question. */
	public static boolean acceptQuestStartDialogue()
	{
		String question = getQuestion();
		if (question == null)
		{
			return false;
		}
		String q = question.toLowerCase(java.util.Locale.ROOT).trim();
		return q.startsWith("would you like to start") && q.contains("quest") && q.endsWith("?") && clickOption("Yes", false);
	}

	/** As upstream: presses the number key of the shown option at {@code index} (from 1). */
	public static boolean keyPressForDialogueOption(int index)
	{
		if (index < 1 || index > 9 || !hasSelectAnOption())
		{
			return false;
		}
		int shown = MobileActions.read(() ->
		{
			net.runelite.api.widgets.Widget menu = Microbot.getClient().getWidget(net.runelite.api.widgets.ComponentID.DIALOG_OPTION_OPTIONS);
			net.runelite.api.widgets.Widget[] rows = menu == null || menu.isHidden() ? null : menu.getDynamicChildren();
			int count = 0;
			for (int i = 1; rows != null && i < rows.length; i++)
			{
				if (rows[i] != null && !rows[i].isHidden() && rows[i].getText() != null && !rows[i].getText().isBlank())
				{
					count++;
				}
			}
			return count;
		}, 0);
		return index <= shown && MobileActions.key(KeyEvent.VK_0 + index);
	}

	/** As upstream: the option containing {@code text}, by its number key. */
	public static boolean keyPressForDialogueOption(String text)
	{
		return keyPressForDialogueOption(text, false);
	}

	/** As upstream: the option equal to ({@code exact}) or containing {@code text}, by its number key. */
	public static boolean keyPressForDialogueOption(String text, boolean exact)
	{
		return hasSelectAnOption() && clickOption(text, exact);
	}

	/** As upstream: the "make X" entry whose name contains {@code text}. */
	public static boolean clickCombinationOption(String text)
	{
		return clickCombinationOption(text, false);
	}

	/**
	 * As upstream: taps the "make X" entry (270:14) whose name, without colour tags, equals
	 * ({@code exact}) or contains {@code text}, ignoring case; one script action.
	 */
	/** The {@code {widgetId, index}} of the shown combination option named {@code text}, or null. */
	private static int[] combinationOptionTarget(String text, boolean exact)
	{
		return MobileActions.read(() ->
		{
			if (!Rs2Widget.isWidgetVisible(270, 13))
			{
				return null;
			}
			net.runelite.api.widgets.Widget list = Microbot.getClient().getWidget(270, 14);
			net.runelite.api.widgets.Widget[] children = list == null ? null : list.getStaticChildren();
			for (int i = 0; children != null && i < children.length; i++)
			{
				net.runelite.api.widgets.Widget child = children[i];
				if (child == null || child.getActions() == null || child.getActions().length == 0 || child.getName() == null)
				{
					continue;
				}
				String name = child.getName().replaceAll("<[^>]*>", "").trim();
				if (exact ? name.equalsIgnoreCase(text) : name.toLowerCase(java.util.Locale.ROOT).contains(text.toLowerCase(java.util.Locale.ROOT)))
				{
					return new int[]{child.getId(), child.getIndex()};
				}
			}
			return null;
		}, null);
	}

	public static boolean clickCombinationOption(String text, boolean exact)
	{
		if (text == null || !hasCombinationDialogue())
		{
			return false;
		}
		int[] target = combinationOptionTarget(text, exact);
		return target != null && MobileActions.widget(target[0], target[1], "");
	}

	/** As upstream: waits (up to 5 s) until the "Select an option" menu is showing. */
	public static boolean sleepUntilSelectAnOption()
	{
		return net.runelite.client.plugins.microbot.util.Global.sleepUntil(Rs2Dialogue::hasSelectAnOption, 5_000);
	}

	/** As upstream: waits (up to 5 s) until a "Click here to continue" prompt is showing. */
	public static boolean sleepUntilHasContinue()
	{
		return net.runelite.client.plugins.microbot.util.Global.sleepUntil(Rs2Dialogue::hasContinue, 5_000);
	}

	/** As upstream: waits (up to 5 s) until the "make X" interface is showing. */
	public static boolean sleepUntilHasCombinationDialogue()
	{
		return net.runelite.client.plugins.microbot.util.Global.sleepUntil(Rs2Dialogue::hasCombinationDialogue, 5_000);
	}
}
