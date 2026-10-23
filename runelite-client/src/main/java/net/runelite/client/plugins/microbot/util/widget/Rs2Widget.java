package net.runelite.client.plugins.microbot.util.widget;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;

/** All widget traversal and target extraction take place on the client thread. */
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

	public static boolean clickWidget(Widget widget)
	{
		int[] target = MobileActions.read(() -> widget == null ? null : new int[]{widget.getId(), widget.getIndex()}, null);
		return target != null && MobileActions.widget(target[0], target[1], "");
	}

	public static boolean clickWidget(int id) { return MobileActions.widget(id, -1, ""); }
	public static boolean clickWidget(int group, int child) { return clickWidget((group << 16) | child); }
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

}
