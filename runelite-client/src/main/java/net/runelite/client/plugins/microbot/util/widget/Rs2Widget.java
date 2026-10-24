package net.runelite.client.plugins.microbot.util.widget;

import java.awt.event.KeyEvent;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;

/**
 * All widget traversal and target extraction take place on the client thread.
 * <p>
 * The read-only methods after {@link #getWidget(int)} are ported from upstream
 * chsami/Microbot e01e0d77 (BSD 2-Clause) with the same names and results. Each one reads the
 * client through {@link MobileActions#read}, so it is safe from a script thread and returns its
 * fallback when the client thread cannot be reached. A returned {@link Widget} is a live handle:
 * dereference it on the client thread, or use the read methods here.
 */
public final class Rs2Widget
{
	private Rs2Widget() { }

	/** A live handle for null checks/guarded dispatch only; dereference on the client thread. */
	public static Widget findWidget(String text, List<Widget> children, boolean exact)
	{
		return MobileActions.read(() -> findOnClientThread(text, children, exact), null);
	}

	private static Widget findOnClientThread(String text, List<Widget> children, boolean exact)
	{
		if (text == null || text.isEmpty()) { return null; }
		Widget[] roots = children == null ? Microbot.getClient().getWidgetRoots() : children.toArray(new Widget[0]);
		if (roots == null) { return null; }
		Queue<Widget> queue = new ArrayDeque<>();
		Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		add(queue, roots);
		while (!queue.isEmpty() && visited.size() < 20000)
		{
			Widget widget = queue.remove();
			if (!visited.add(widget) || widget.isHidden()) { continue; }
			if (matches(widget.getText(), text, exact) || matches(widget.getName(), text, exact)) { return widget; }
			if (widget.getActions() != null)
			{
				for (String action : widget.getActions())
				{
					if (matches(action, text, exact)) { return widget; }
				}
			}
			add(queue, widget.getChildren());
			add(queue, widget.getDynamicChildren());
			add(queue, widget.getStaticChildren());
			add(queue, widget.getNestedChildren());
		}
		return null;
	}

	private static void add(Queue<Widget> queue, Widget[] children)
	{
		if (children == null) { return; }
		Arrays.stream(children).filter(java.util.Objects::nonNull).forEach(queue::add);
	}

	static boolean matches(String value, String requested, boolean exact)
	{
		if (value == null || requested == null) { return false; }
		String clean = value.replaceAll("<[^>]*>", "").toLowerCase(Locale.ROOT);
		String query = requested.toLowerCase(Locale.ROOT);
		return exact ? clean.equals(query) : clean.contains(query);
	}

	public static Widget findWidget(String text) { return findWidget(text, null, false); }
	public static Widget findWidget(String text, boolean exact) { return findWidget(text, null, exact); }
	public static boolean clickWidget(String text)
	{
		// Copy only immutable identity into the caller, and re-resolve its live state at dispatch.
		int[] target = MobileActions.read(() ->
		{
			Widget widget = findOnClientThread(text, null, false);
			return widget == null ? null : new int[]{widget.getId(), widget.getIndex()};
		}, null);
		return target != null && MobileActions.widget(target[0], target[1], "");
	}

	/** As upstream: the first visible widget whose text matches {@code text} exactly (ignoring case) or contains it. */
	public static boolean clickWidget(String text, boolean exact)
	{
		int[] target = MobileActions.read(() ->
		{
			Widget widget = findOnClientThread(text, null, exact);
			return widget == null ? null : new int[]{widget.getId(), widget.getIndex()};
		}, null);
		return target != null && MobileActions.widget(target[0], target[1], "");
	}

	public static boolean clickWidget(Widget widget)
	{
		int[] target = MobileActions.read(() -> widget == null ? null : new int[]{widget.getId(), widget.getIndex()}, null);
		return target != null && MobileActions.widget(target[0], target[1], "");
	}

	public static boolean clickWidget(int id) { return MobileActions.widget(id, -1, ""); }
	public static boolean clickWidget(int group, int child) { return clickWidget((group << 16) | child); }
	/** As upstream: clicks child {@code childId} of the widget {@code id} (its first option, else a tap on it). */
	public static boolean clickChildWidget(int id, int childId) { return MobileActions.widget(id, childId, ""); }
	public static boolean isWidgetVisible(int group, int child)
	{
		return MobileActions.read(() ->
		{
			Widget widget = Microbot.getClient().getWidget(group, child);
			return widget != null && !widget.isHidden();
		}, false);
	}
	public static boolean isProductionWidgetOpen() { return isWidgetVisible(InterfaceID.SKILLMULTI, 0); }

	/** Selects only an option in the production interface, never an unrelated global text match. */
	public static boolean handleProcessingInterface(String outputName)
	{
		return clickProductionOption(outputName, false);
	}

	public static boolean enableQuantityOption(String quantity)
	{
		return clickProductionOption(quantity, true);
	}

	private static boolean clickProductionOption(String text, boolean exact)
	{
		int[] target = MobileActions.read(() ->
		{
			Widget root = Microbot.getClient().getWidget(InterfaceID.SKILLMULTI, 0);
			if (root == null || root.isHidden()) { return null; }
			Widget option = findOnClientThread(text, Collections.singletonList(root), exact);
			if (option == null) { return null; }
			Widget targetWidget = option;
			for (Widget ancestor = option; ancestor != null && ancestor != root; ancestor = ancestor.getParent())
			{
				if ((ancestor.getId() >>> 16) != InterfaceID.SKILLMULTI) { break; }
				if (ancestor.getActions() != null && Arrays.stream(ancestor.getActions())
					.anyMatch(action -> action != null && !action.isEmpty()))
				{
					targetWidget = ancestor;
					break;
				}
			}
			return new int[]{targetWidget.getId(), targetWidget.getIndex()};
		}, null);
		return target != null && MobileActions.widget(target[0], target[1], "");
	}

	// ---- Read-only API ported from upstream ----

	public static Widget getWidget(int id)
	{
		return MobileActions.read(() -> Microbot.getClient().getWidget(id), null);
	}

	public static Widget getWidget(int id, int child)
	{
		return MobileActions.read(() -> Microbot.getClient().getWidget(id, child), null);
	}

	public static boolean isWidgetVisible(int id)
	{
		return MobileActions.read(() ->
		{
			Widget widget = Microbot.getClient().getWidget(id);
			return widget != null && !widget.isHidden();
		}, false);
	}

	/** True when the widget is missing or hidden; {@code false} when the client thread is unreachable, as upstream. */
	public static boolean isHidden(int parentId, int childId)
	{
		return MobileActions.read(() ->
		{
			Widget widget = Microbot.getClient().getWidget(parentId, childId);
			return widget == null || widget.isHidden();
		}, false);
	}

	/** Same contract as {@link #isHidden(int, int)}, for a packed component id. */
	public static boolean isHidden(int id)
	{
		return MobileActions.read(() ->
		{
			Widget widget = Microbot.getClient().getWidget(id);
			return widget == null || widget.isHidden();
		}, false);
	}

	public static int getChildWidgetSpriteID(int id, int childId)
	{
		return MobileActions.read(() ->
		{
			Widget widget = Microbot.getClient().getWidget(id, childId);
			return widget == null ? -1 : widget.getSpriteId();
		}, -1);
	}

	/** The widget's raw text, or {@code ""} when it is missing. */
	public static String getChildWidgetText(int id, int childId)
	{
		return MobileActions.read(() ->
		{
			Widget widget = Microbot.getClient().getWidget(id, childId);
			return widget == null || widget.getText() == null ? "" : widget.getText();
		}, "");
	}

	public static Widget findWidget(String text, List<Widget> children)
	{
		return findWidget(text, children, false);
	}

	/** Whether a visible descendant of the component (or the component) matches {@code text}. */
	public static boolean hasWidgetText(String text, int componentId, boolean exact)
	{
		return MobileActions.read(() -> visibleMatch(Microbot.getClient().getWidget(componentId), text, exact), false);
	}

	public static boolean hasWidgetText(String text, int widgetId, int childId, boolean exact)
	{
		return MobileActions.read(() -> visibleMatch(Microbot.getClient().getWidget(widgetId, childId), text, exact), false);
	}

	private static boolean visibleMatch(Widget root, String text, boolean exact)
	{
		if (root == null) { return false; }
		Widget found = searchChildren(text, root, exact);
		return found != null && !found.isHidden();
	}

	public static boolean hasWidget(String text)
	{
		return findWidget(text, null, false) != null;
	}

	/** Exact, case-independent text, name or action match anywhere in the visible tree. */
	public static boolean hasVisibleWidgetText(String text)
	{
		return MobileActions.read(() ->
		{
			Widget widget = findOnClientThread(text, null, true);
			return widget != null && !widget.isHidden();
		}, false);
	}

	/**
	 * Depth-first search of {@code child} and its visible descendants for a text, name or action
	 * match. {@code child} itself is checked even when hidden, as upstream.
	 */
	public static Widget searchChildren(String text, Widget child, boolean exact)
	{
		return MobileActions.read(() -> searchOnClientThread(child,
			widget -> textMatches(widget, text, exact)), null);
	}

	/** Searches the visible tree, or only under {@code children}, for a widget showing {@code spriteId}. */
	public static Widget findWidget(int spriteId, List<Widget> children)
	{
		return MobileActions.read(() ->
		{
			java.util.function.Predicate<Widget> sprite = widget -> widget.getSpriteId() == spriteId;
			if (children != null)
			{
				for (Widget child : children)
				{
					Widget found = searchOnClientThread(child, sprite);
					if (found != null) { return found; }
				}
				return null;
			}
			Widget[] roots = Microbot.getClient().getWidgetRoots();
			if (roots == null) { return null; }
			for (Widget root : roots)
			{
				if (root == null || root.isHidden()) { continue; }
				Widget found = searchOnClientThread(root, sprite);
				if (found != null) { return found; }
			}
			return null;
		}, null);
	}

	/** Same search as {@link #searchChildren(String, Widget, boolean)}, by sprite id. */
	public static Widget searchChildren(int spriteId, Widget child)
	{
		return MobileActions.read(() -> searchOnClientThread(child, widget -> widget.getSpriteId() == spriteId), null);
	}

	private static Widget searchOnClientThread(Widget child, java.util.function.Predicate<Widget> match)
	{
		if (child == null) { return null; }
		if (match.test(child)) { return child; }
		Widget[][] groups = {child.getChildren(), child.getNestedChildren(), child.getDynamicChildren(), child.getStaticChildren()};
		for (Widget[] group : groups)
		{
			if (group == null) { continue; }
			for (Widget nested : group)
			{
				if (nested != null && !nested.isHidden())
				{
					Widget found = searchOnClientThread(nested, match);
					if (found != null) { return found; }
				}
			}
		}
		return null;
	}

	private static boolean textMatches(Widget widget, String text, boolean exact)
	{
		if (matches(widget.getText(), text, exact) || matches(widget.getName(), text, exact)) { return true; }
		if (widget.getActions() != null)
		{
			for (String action : widget.getActions())
			{
				if (matches(action, text, exact)) { return true; }
			}
		}
		return false;
	}

	public static boolean isGoldCraftingWidgetOpen() { return isWidgetVisible(446, 0); }
	public static boolean isSilverCraftingWidgetOpen() { return isWidgetVisible(6, 0); }
	public static boolean isSmithingWidgetOpen() { return isWidgetVisible(InterfaceID.SMITHING, 0); }
	public static boolean isDepositBoxWidgetOpen() { return isWidgetVisible(192, 0); }
	public static boolean isWildernessInterfaceOpen() { return isWidgetVisible(475, 11); }

	/**
	 * Maps each dynamic child index of the widget to the key its label shows ("1".."7", "Space"), as
	 * {@link KeyEvent} codes. Empty when the widget or its children are missing.
	 */
	public static Map<Integer, Integer> getWidgetsKeyMap(int widgetGroupId, int widgetSubGroupId)
	{
		return MobileActions.read(() ->
		{
			Map<Integer, Integer> keyMap = new HashMap<>();
			Widget widget = Microbot.getClient().getWidget(widgetGroupId, widgetSubGroupId);
			Widget[] dynamicChildren = widget == null ? null : widget.getDynamicChildren();
			if (dynamicChildren == null) { return keyMap; }
			for (int i = 0; i < dynamicChildren.length; i++)
			{
				Widget child = dynamicChildren[i];
				Integer key = child == null ? null : keyCode(stripTags(child.getText()));
				if (key != null) { keyMap.put(i, key); }
			}
			return keyMap;
		}, new HashMap<>());
	}

	private static Integer keyCode(String label)
	{
		if ("Space".equals(label)) { return KeyEvent.VK_SPACE; }
		if (label.length() == 1 && label.charAt(0) >= '1' && label.charAt(0) <= '7')
		{
			return KeyEvent.VK_1 + (label.charAt(0) - '1');
		}
		return null;
	}

	private static String stripTags(String value)
	{
		return value == null ? "" : value.replaceAll("<[^>]*>", "");
	}

	/**
	 * Among the component's dynamic children: the first exact (case-independent) text match, else the
	 * first containing match, else the one sharing the most words with {@code targetText}.
	 */
	public static Widget findBestMatchingWidget(int widgetId, String targetText)
	{
		return MobileActions.read(() ->
		{
			Widget parent = Microbot.getClient().getWidget(widgetId);
			Widget[] dynamicChildren = parent == null ? null : parent.getDynamicChildren();
			if (dynamicChildren == null || targetText == null) { return null; }
			List<Widget> children = Arrays.stream(dynamicChildren).filter(java.util.Objects::nonNull).collect(Collectors.toList());
			String target = targetText.toLowerCase(Locale.ROOT);
			for (Widget widget : children)
			{
				if (widget.getText() != null && widget.getText().toLowerCase(Locale.ROOT).equals(target)) { return widget; }
			}
			for (Widget widget : children)
			{
				if (widget.getText() != null && widget.getText().toLowerCase(Locale.ROOT).contains(target)) { return widget; }
			}
			String[] targetWords = target.split("\\s+");
			Widget best = null;
			int bestScore = 0;
			for (Widget widget : children)
			{
				if (widget.getText() == null) { continue; }
				int score = countMatchingWords(targetWords, widget.getText().toLowerCase(Locale.ROOT).split("\\s+"));
				if (score > bestScore)
				{
					best = widget;
					bestScore = score;
				}
			}
			return best;
		}, null);
	}

	private static int countMatchingWords(String[] sourceWords, String[] targetWords)
	{
		return (int) Arrays.stream(sourceWords)
			.filter(source -> Arrays.stream(targetWords).anyMatch(target -> target.contains(source) || source.contains(target)))
			.count();
	}

	/** Share of words (by the longer text) that appear in the other text, from 0 to 1. Pure. */
	public static double calculateTextSimilarity(String source, String target)
	{
		if (source == null || target == null) { return 0.0; }
		String[] sourceWords = source.toLowerCase(Locale.ROOT).split("\\s+");
		String[] targetWords = target.toLowerCase(Locale.ROOT).split("\\s+");
		int totalWords = Math.max(sourceWords.length, targetWords.length);
		return totalWords > 0 ? (double) countMatchingWords(sourceWords, targetWords) / totalWords : 0.0;
	}

	/** Widgets whose text is at least {@code threshold} similar to {@code targetText}, most similar first. */
	public static List<Widget> findSimilarWidgets(List<Widget> widgets, String targetText, double threshold)
	{
		return MobileActions.read(() -> widgets.stream()
			.filter(widget -> widget.getText() != null)
			.map(widget -> new AbstractMap.SimpleEntry<>(widget, calculateTextSimilarity(widget.getText(), targetText)))
			.filter(entry -> entry.getValue() >= threshold)
			.sorted(Map.Entry.<Widget, Double>comparingByValue().reversed())
			.map(Map.Entry::getKey)
			.collect(Collectors.toList()), Collections.emptyList());
	}

	public static boolean sleepUntilHasWidgetText(String text, int widgetId, int childId, boolean exact, int sleep)
	{
		return Global.sleepUntilTrue(() -> hasWidgetText(text, widgetId, childId, exact), 300, sleep);
	}

	public static boolean sleepUntilHasNotWidgetText(String text, int widgetId, int childId, boolean exact, int sleep)
	{
		return Global.sleepUntilTrue(() -> !hasWidgetText(text, widgetId, childId, exact), 300, sleep);
	}

	public static boolean sleepUntilHasWidget(String text)
	{
		return Global.sleepUntil(() -> findWidget(text, null, false) != null);
	}

	public static boolean waitForWidget(String text, int timeout)
	{
		return Global.sleepUntil(() -> hasVisibleWidgetText(text), timeout);
	}
}
