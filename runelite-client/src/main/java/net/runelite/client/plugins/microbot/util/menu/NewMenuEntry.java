package net.runelite.client.plugins.microbot.util.menu;

import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.runelite.api.Actor;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.widgets.Widget;

/**
 * A detached menu entry that a script builds to describe one click (option, target, opcode,
 * params, and the actor, object or widget it refers to). Data only: it is not inserted into the
 * client's menu, and invoking it is {@code Microbot.doInvoke}'s job. Ported from upstream
 * Microbot 2.6.38 with the same constructors, fluent setters, equality and
 * {@link #findIdentifier} helpers; lint-only edits (explicit imports, {@link Objects} in
 * {@code equals}/{@code hashCode}, no JetBrains annotations).
 */
public class NewMenuEntry implements MenuEntry
{
	private String option;
	private String target;
	private int identifier;
	private MenuAction type;
	private int param0;
	private int param1;
	private boolean forceLeftClick;
	private int itemId;
	private Actor actor;
	private TileObject gameObject;
	private Widget widget;
	private int worldViewId = WorldView.TOPLEVEL;

	private NewMenuEntry(int param0, int param1, MenuAction type, int identifier)
	{
		this.param0 = param0;
		this.param1 = param1;
		this.type = type;
		this.identifier = identifier;
	}

	private NewMenuEntry(int param0, int param1, int opcode, int identifier)
	{
		this(param0, param1, MenuAction.of(opcode), identifier);
	}

	/** An entry whose option is {@code target} and whose target is empty (upstream's widget form). */
	public NewMenuEntry(int param0, int param1, int opcode, int identifier, int itemId, String target)
	{
		this(param0, param1, opcode, identifier);
		this.option = target;
		this.target = "";
		this.itemId = itemId;
	}

	/** As {@link #NewMenuEntry(int, int, int, int, int, String)}, in the given world view. */
	public NewMenuEntry(int param0, int param1, int opcode, int identifier, int itemId, String target, int worldViewId)
	{
		this(param0, param1, opcode, identifier, itemId, target);
		this.worldViewId = worldViewId;
	}

	/** An entry on an actor (NPC or player). */
	public NewMenuEntry(int param0, int param1, int opcode, int identifier, int itemId, String target, Actor actor, String option)
	{
		this(param0, param1, opcode, identifier);
		this.option = option;
		this.target = target;
		this.itemId = itemId;
		this.actor = actor;
	}

	/** An entry on an actor with option "Use". */
	public NewMenuEntry(int param0, int param1, int opcode, int identifier, int itemId, String target, Actor actor)
	{
		this(param0, param1, opcode, identifier, itemId, target, actor, "Use");
	}

	/** An entry on a tile object. */
	public NewMenuEntry(int param0, int param1, int opcode, int identifier, int itemId, String option, String target, TileObject gameObject)
	{
		this(param0, param1, opcode, identifier);
		this.option = option;
		this.target = target;
		this.itemId = itemId;
		this.gameObject = gameObject;
	}

	/** An entry on a tile object in the given world view. */
	public NewMenuEntry(int param0, int param1, int opcode, int identifier, int itemId, String option, String target, TileObject gameObject, int worldViewId)
	{
		this(param0, param1, opcode, identifier, itemId, option, target, gameObject);
		this.worldViewId = worldViewId;
	}

	/** An entry with an explicit action type and left-click flag. */
	public NewMenuEntry(String option, String target, int identifier, MenuAction type, int param0, int param1, boolean forceLeftClick)
	{
		this(param0, param1, type, identifier);
		this.option = option;
		this.target = target;
		this.forceLeftClick = forceLeftClick;
	}

	/** An entry with an explicit option and target. */
	public NewMenuEntry(String option, int param0, int param1, int opcode, int identifier, int itemId, String target)
	{
		this(param0, param1, opcode, identifier);
		this.option = option;
		this.target = target;
		this.itemId = itemId;
	}

	/** An empty entry to fill in with the fluent setters. */
	public NewMenuEntry()
	{
		this.option = "";
		this.target = "";
		this.itemId = -1;
	}

	public NewMenuEntry option(String option)
	{
		this.option = option;
		return this;
	}

	public NewMenuEntry target(String target)
	{
		this.target = target;
		return this;
	}

	public NewMenuEntry identifier(int identifier)
	{
		this.identifier = identifier;
		return this;
	}

	public NewMenuEntry type(MenuAction type)
	{
		this.type = type;
		return this;
	}

	public NewMenuEntry opcode(int opcode)
	{
		this.type = MenuAction.of(opcode);
		return this;
	}

	public NewMenuEntry param0(int param0)
	{
		this.param0 = param0;
		return this;
	}

	public NewMenuEntry param1(int param1)
	{
		this.param1 = param1;
		return this;
	}

	public NewMenuEntry forceLeftClick(boolean forceLeftClick)
	{
		this.forceLeftClick = forceLeftClick;
		return this;
	}

	public NewMenuEntry actor(Actor actor)
	{
		this.actor = actor;
		return this;
	}

	public NewMenuEntry gameObject(TileObject gameObject)
	{
		this.gameObject = gameObject;
		return this;
	}

	public NewMenuEntry widget(Widget widget)
	{
		this.widget = widget;
		return this;
	}

	public NewMenuEntry worldViewId(int worldViewId)
	{
		this.worldViewId = worldViewId;
		return this;
	}

	public NewMenuEntry itemId(int itemId)
	{
		this.itemId = itemId;
		return this;
	}

	@Override
	public String getOption()
	{
		return option;
	}

	@Override
	public MenuEntry setOption(String option)
	{
		this.option = option;
		return this;
	}

	@Override
	public String getTarget()
	{
		return target;
	}

	@Override
	public MenuEntry setTarget(String target)
	{
		this.target = target;
		return this;
	}

	@Override
	public int getIdentifier()
	{
		return identifier;
	}

	@Override
	public MenuEntry setIdentifier(int identifier)
	{
		this.identifier = identifier;
		return this;
	}

	@Override
	public MenuAction getType()
	{
		return type;
	}

	@Override
	public MenuEntry setType(MenuAction type)
	{
		this.type = type;
		return this;
	}

	@Override
	public int getParam0()
	{
		return param0;
	}

	@Override
	public MenuEntry setParam0(int param0)
	{
		this.param0 = param0;
		return this;
	}

	@Override
	public int getParam1()
	{
		return param1;
	}

	@Override
	public MenuEntry setParam1(int param1)
	{
		this.param1 = param1;
		return this;
	}

	@Override
	public boolean isForceLeftClick()
	{
		return forceLeftClick;
	}

	@Override
	public MenuEntry setForceLeftClick(boolean forceLeftClick)
	{
		this.forceLeftClick = forceLeftClick;
		return this;
	}

	@Override
	public int getWorldViewId()
	{
		return worldViewId;
	}

	@Override
	public NewMenuEntry setWorldViewId(int worldViewId)
	{
		this.worldViewId = worldViewId;
		return this;
	}

	@Override
	public boolean isDeprioritized()
	{
		return false;
	}

	@Override
	public MenuEntry setDeprioritized(boolean deprioritized)
	{
		return this;
	}

	/** Ignored, as upstream: a detached entry has no client-side click callback. */
	@Override
	public MenuEntry onClick(Consumer<MenuEntry> callback)
	{
		return this;
	}

	@Override
	@Nullable
	public Consumer<MenuEntry> onClick()
	{
		return null;
	}

	/** This entry itself, as upstream (a detached entry has no parent menu). */
	public MenuEntry getParent()
	{
		return this;
	}

	@Override
	public boolean isItemOp()
	{
		return false;
	}

	@Override
	public int getItemOp()
	{
		return 0;
	}

	@Override
	public int getItemId()
	{
		return itemId;
	}

	@Override
	public MenuEntry setItemId(int itemId)
	{
		this.itemId = itemId;
		return this;
	}

	public MenuEntry setWidget(Widget widget)
	{
		this.widget = widget;
		return this;
	}

	@Override
	@Nullable
	public Widget getWidget()
	{
		return widget;
	}

	@Override
	@Nullable
	public NPC getNpc()
	{
		return actor instanceof NPC ? (NPC) actor : null;
	}

	@Override
	@Nullable
	public Player getPlayer()
	{
		return actor instanceof Player ? (Player) actor : null;
	}

	@Override
	@Nullable
	public Actor getActor()
	{
		return actor;
	}

	/** The tile object this entry targets, if any. */
	@Nullable
	public TileObject getGameObject()
	{
		return gameObject;
	}

	@Override
	@Nullable
	public Menu getSubMenu()
	{
		return null;
	}

	/** Returns {@code null}, as upstream: a detached entry cannot own a sub-menu. */
	@Override
	@Nullable
	public Menu createSubMenu()
	{
		return null;
	}

	@Override
	public void deleteSubMenu()
	{
		// A detached entry never has a sub-menu.
	}

	/** Equal when option, target, identifier, type, both params and the left-click flag match. */
	@Override
	public boolean equals(Object o)
	{
		if (o == this)
		{
			return true;
		}
		if (!(o instanceof NewMenuEntry))
		{
			return false;
		}
		NewMenuEntry other = (NewMenuEntry) o;
		return other.canEqual(this)
			&& identifier == other.identifier
			&& param0 == other.param0
			&& param1 == other.param1
			&& forceLeftClick == other.forceLeftClick
			&& Objects.equals(option, other.option)
			&& Objects.equals(target, other.target)
			&& Objects.equals(type, other.type);
	}

	/** Whether {@code other} may equal this entry (Lombok's subclass-safe equality hook, kept from upstream). */
	protected boolean canEqual(Object other)
	{
		return other instanceof NewMenuEntry;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(identifier, param0, param1, forceLeftClick, option, target, type);
	}

	@Override
	public String toString()
	{
		return "NewMenuEntry(option=" + option + ", target=" + target + ", identifier=" + identifier + ", type=" + type
			+ ", param0=" + param0 + ", param1=" + param1 + ", forceLeftClick=" + forceLeftClick + ")";
	}

	/**
	 * The identifier of a widget menu option: {@code menuOption * 65536 + offset}
	 * (for example option 1 with offset 6 is 65542).
	 *
	 * @param menuOption the menu option number, starting at 1
	 * @param offset the offset to add (may be negative)
	 * @return the identifier
	 */
	public static int findIdentifier(int menuOption, int offset)
	{
		return menuOption * 65536 + offset;
	}

	/**
	 * The identifier of a widget menu option with the default offset of 6.
	 *
	 * @param menuOption the menu option number, starting at 1
	 * @return the identifier
	 */
	public static int findIdentifier(int menuOption)
	{
		return findIdentifier(menuOption, 6);
	}
}
