package net.runelite.client.plugins.microbot.util.combat;

import net.runelite.api.VarPlayer;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/** Special-attack toggle with energy guard and state confirmation. */
public final class Rs2Combat
{
	private Rs2Combat() { }
	/**
	 * As upstream: clicks the combat-tab attack style button {@code style} (for example
	 * {@code WidgetInfo.COMBAT_STYLE_ONE}). False when the button is not loaded or the click
	 * was not dispatched.
	 */
	@SuppressWarnings("deprecation")
	public static boolean setAttackStyle(net.runelite.api.widgets.WidgetInfo style)
	{
		return style != null && Rs2Widget.getWidget(style.getId()) != null && Rs2Widget.clickWidget(style.getId());
	}

	public static boolean getSpecState()
	{
		return MobileActions.read(() -> Microbot.getClient().getVarpValue(VarPlayer.SPECIAL_ATTACK_ENABLED) == 1, false);
	}
	public static boolean setSpecState(boolean enabled, int requiredEnergy)
	{
		if (getSpecState() == enabled) { return true; }
		int energy = MobileActions.read(() -> Microbot.getClient().getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT), 0);
		if (enabled && energy < Math.max(0, requiredEnergy)) { return false; }
		if (!MobileActions.widget(10485795, -1, "")) { return false; }
		return Rs2Player.waitFor(() -> getSpecState() == enabled, 1800);
	}
	public static boolean setSpecState(boolean enabled) { return setSpecState(enabled, 0); }

	/**
	 * Whether the local player is fighting: interacting with an actor that has a combat level, and
	 * either animating or still interacting. False when logged out or without a player.
	 */
	/** Upstream's weapon range table, by item id. */
	private static final java.util.Map<Integer, net.runelite.client.plugins.microbot.util.combat.weapons.Weapon> WEAPONS_MAP =
		net.runelite.client.plugins.microbot.util.combat.weapons.WeaponsGenerator.generate();

	/** As upstream: the attack range of the equipped weapon in its current style (1 for melee or unknown). */
	public static int getAttackRange()
	{
		return getAttackRange(false, false);
	}

	/**
	 * As upstream: the equipped weapon's range for its current attack style; a manual-castable staff
	 * counts only while auto-casting unless {@code includeManualCast}; melee is 1 unless
	 * {@code includeSpecialAttack} (then its special attack range).
	 */
	public static int getAttackRange(boolean includeManualCast, boolean includeSpecialAttack)
	{
		net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel weapon =
			net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.get(net.runelite.api.EquipmentInventorySlot.WEAPON);
		net.runelite.client.plugins.microbot.util.combat.weapons.Weapon data = weapon == null ? null : WEAPONS_MAP.get(weapon.getId());
		if (data == null)
		{
			return 1;
		}
		if (data instanceof net.runelite.client.plugins.microbot.util.combat.weapons.Melee)
		{
			return includeSpecialAttack ? ((net.runelite.client.plugins.microbot.util.combat.weapons.Melee) data).getSpecialAttackRange() : 1;
		}
		String style = getWeaponAttackStyle();
		int range = data instanceof net.runelite.client.plugins.microbot.util.combat.weapons.ManualCastable
			? ((net.runelite.client.plugins.microbot.util.combat.weapons.ManualCastable) data).getRange(style, includeManualCast)
			: data.getRange(style);
		return Math.max(range, 1);
	}

	/**
	 * As upstream: the current attack style's name ("Accurate", "Longrange", "Casting", ...) from the
	 * weapon category's style structs; "" when it cannot be read. Client thread.
	 */
	public static String getWeaponAttackStyle()
	{
		return MobileActions.read(() ->
		{
			net.runelite.api.Client client = Microbot.getClient();
			int style = client.getVarpValue(net.runelite.api.gameval.VarPlayerID.COM_MODE);
			int category = client.getVarbitValue(net.runelite.api.gameval.VarbitID.COMBAT_WEAPON_CATEGORY);
			net.runelite.api.EnumComposition styles = client.getEnum(net.runelite.api.EnumID.WEAPON_STYLES);
			int[] structs = styles == null ? null : client.getEnum(styles.getIntValue(category)).getIntVals();
			if (structs == null || style < 0 || style >= structs.length)
			{
				return "";
			}
			String name = client.getStructComposition(structs[style]).getStringValue(net.runelite.api.ParamID.ATTACK_STYLE_NAME);
			return name == null ? "" : name;
		}, "");
	}

	public static boolean inCombat()
	{
		return MobileActions.read(() ->
		{
			net.runelite.api.Player player = Microbot.getClient().getLocalPlayer();
			net.runelite.api.Actor target = player == null ? null : player.getInteracting();
			return target != null && target.getCombatLevel() >= 1
				&& (player.getAnimation() != -1 || player.isInteracting());
		}, false);
	}

	// ---- Actions (Claude 1) ----

	/** As upstream: true once auto retaliate matches {@code enable}; opens the combat tab to reach the button. */
	public static boolean setAutoRetaliate(boolean enable)
	{
		int expected = enable ? 0 : 1;
		if (Microbot.getVarbitPlayerValue(net.runelite.api.gameval.VarPlayerID.OPTION_NODEF) == expected) { return true; }
		return net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.switchTo(net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.COMBAT)
			&& net.runelite.client.plugins.microbot.util.Global.sleepUntil(() ->
				MobileActions.widget(net.runelite.api.gameval.InterfaceID.CombatInterface.RETALIATE, -1, ""), 2_000)
			&& net.runelite.client.plugins.microbot.util.Global.sleepUntil(() ->
				Microbot.getVarbitPlayerValue(net.runelite.api.gameval.VarPlayerID.OPTION_NODEF) == expected, 2_000);
	}

	/** The autocast spell list's container (upstream reads 201:1). */
	private static final int AUTOCAST_GROUP = 201;
	private static final int AUTOCAST_CHILD = 1;

	/**
	 * As upstream: sets {@code combatSpell} as the autocast spell (defensive when
	 * {@code useDefensiveCast}). It opens the combat tab, presses the spell box, then picks the
	 * spell by its sprite in the list. True once the autocast varbits show the spell and mode;
	 * false when it cannot be cast or the list does not offer it. Each step is one script action.
	 */
	public static boolean setAutoCastSpell(net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells combatSpell, boolean useDefensiveCast)
	{
		if (combatSpell == null || !net.runelite.client.plugins.microbot.util.magic.Rs2Magic.canCast(combatSpell.getMagicAction()))
		{
			return false;
		}
		if (autoCastIs(combatSpell, useDefensiveCast))
		{
			return true;
		}
		int box = useDefensiveCast ? net.runelite.api.gameval.InterfaceID.CombatInterface.AUTOCAST_DEFENSIVE
			: net.runelite.api.gameval.InterfaceID.CombatInterface.AUTOCAST_NORMAL;
		if (!net.runelite.client.plugins.microbot.util.tabs.Rs2Tab.switchTo(net.runelite.client.plugins.microbot.globval.enums.InterfaceTab.COMBAT)
			|| !net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> MobileActions.widget(box, -1, ""), 2_000)
			|| !net.runelite.client.plugins.microbot.util.Global.sleepUntil(() ->
				Rs2Widget.isWidgetVisible(AUTOCAST_GROUP, AUTOCAST_CHILD), 3_000))
		{
			return false;
		}
		net.runelite.api.widgets.Widget options = Rs2Widget.getWidget(AUTOCAST_GROUP, AUTOCAST_CHILD);
		net.runelite.api.widgets.Widget spell = options == null ? null
			: Rs2Widget.findWidget(combatSpell.getMagicAction().getSprite(), java.util.List.of(options));
		if (spell == null)
		{
			return false;
		}
		int id = MobileActions.read(spell::getId, -1);
		int index = MobileActions.read(spell::getIndex, -1);
		return net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> MobileActions.widget(id, index, ""), 2_000)
			&& net.runelite.client.plugins.microbot.util.Global.sleepUntil(() -> autoCastIs(combatSpell, useDefensiveCast), 3_000);
	}

	private static boolean autoCastIs(net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells spell, boolean defensive)
	{
		return net.runelite.client.plugins.microbot.util.magic.Rs2Magic.getCurrentAutoCastSpell() == spell
			&& Microbot.getVarbitValue(net.runelite.api.Varbits.DEFENSIVE_CASTING_MODE) == (defensive ? 1 : 0);
	}

	/** Upstream spelling. */
	public static boolean enableAutoRetialiate()
	{
		return setAutoRetaliate(true);
	}

	/** As upstream: special attack energy, 0-1000 (100% = 1000). */
	public static int getSpecEnergy()
	{
		return Microbot.getVarbitPlayerValue(net.runelite.api.gameval.VarPlayerID.SA_ENERGY);
	}
}
