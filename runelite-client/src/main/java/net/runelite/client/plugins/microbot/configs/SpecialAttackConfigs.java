/*
 * Adapted from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/configs/SpecialAttackConfigs.java.
 * BSD 2-Clause License; see LICENSE. Mobile: waits for the weapon swap instead of a fixed sleep.
 */
package net.runelite.client.plugins.microbot.configs;

import java.util.List;
import java.util.Locale;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.misc.SpecialAttackWeaponEnum;

/**
 * As upstream: a script's special-attack settings ({@code Microbot.getSpecialAttackConfigs()}):
 * whether to use the special attack, with which weapon, and from how much energy. A script calls
 * {@link #useSpecWeapon()} in its fight loop; it swaps to the weapon and turns the special on.
 */
public class SpecialAttackConfigs
{
	/** The worn items to swap back to after the special attack, as the script recorded them. */
	@Setter
	private static List<Rs2ItemModel> currentEquipment = null;

	@Getter
	private boolean useSpecialAttack = false;

	@Getter
	private SpecialAttackWeaponEnum specialAttackWeapon = null;

	@Getter
	private int minimumSpecEnergy = -1;

	public SpecialAttackConfigs setSpecialAttack(boolean useSpecialAttack)
	{
		this.useSpecialAttack = useSpecialAttack;
		return this;
	}

	public SpecialAttackConfigs setSpecialAttackWeapon(SpecialAttackWeaponEnum specialAttackWeapon)
	{
		this.specialAttackWeapon = specialAttackWeapon;
		return this;
	}

	public SpecialAttackConfigs setMinimumSpecEnergy(int minimumSpecEnergy)
	{
		this.minimumSpecEnergy = minimumSpecEnergy;
		return this;
	}

	/** As upstream: back to "no special attack". */
	public void reset()
	{
		setCurrentEquipment(null);
		setMinimumSpecEnergy(-1);
		setSpecialAttackWeapon(null);
		setSpecialAttack(false);
	}

	/**
	 * As upstream: while logged in and fighting with the special attack enabled, uses the chosen
	 * weapon (or any listed one) carried in the inventory, else worn. True when the special was set.
	 */
	public boolean useSpecWeapon()
	{
		if (!Microbot.isLoggedIn() || !Rs2Combat.inCombat() || !useSpecialAttack)
		{
			return false;
		}
		if (Rs2Combat.getSpecEnergy() < minimumSpecEnergy && currentEquipment == null)
		{
			return false;
		}
		for (SpecialAttackWeaponEnum weapon : SpecialAttackWeaponEnum.values())
		{
			if (Rs2Inventory.hasItem(weapon.getName()) && chosen(weapon) && useSpecWeapon(weapon.getName(), weapon.getEnergyRequired(), weapon.is2H()))
			{
				return true;
			}
		}
		for (SpecialAttackWeaponEnum weapon : SpecialAttackWeaponEnum.values())
		{
			if (Rs2Equipment.isWearing(weapon.getName()) && chosen(weapon) && useSpecWeapon(weapon.getName(), weapon.getEnergyRequired(), weapon.is2H()))
			{
				return true;
			}
		}
		return false;
	}

	private boolean chosen(SpecialAttackWeaponEnum weapon)
	{
		return specialAttackWeapon == null
			|| specialAttackWeapon.getName().toLowerCase(Locale.ROOT).contains(weapon.getName());
	}

	/**
	 * As upstream: with enough energy, wields {@code name} and turns the special attack on; with too
	 * little, swaps back to the recorded weapon and shield. False when nothing was turned on.
	 */
	public boolean useSpecWeapon(String name, int specEnergy, boolean is2H)
	{
		if (name == null || name.isEmpty())
		{
			return false;
		}
		if (Rs2Equipment.isWearing(EquipmentInventorySlot.SHIELD) && is2H && Rs2Inventory.isFull())
		{
			return false;
		}
		if (currentEquipment == null)
		{
			currentEquipment = Rs2Equipment.items();
		}
		if (Rs2Combat.getSpecEnergy() < specEnergy)
		{
			Rs2ItemModel weapon = recorded(EquipmentInventorySlot.WEAPON);
			Rs2ItemModel worn = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
			if (weapon != null && (worn == null || weapon.getId() != worn.getId()))
			{
				Rs2ItemModel shield = recorded(EquipmentInventorySlot.SHIELD);
				if (shield != null)
				{
					Rs2Inventory.wear(shield.getName());
				}
				Rs2Inventory.wear(weapon.getName());
			}
			return false;
		}
		if (Rs2Inventory.wear(name))
		{
			Global.sleepUntil(() -> Rs2Equipment.isWearing(name), 1_800);
		}
		return Rs2Combat.setSpecState(true, specEnergy);
	}

	private static Rs2ItemModel recorded(EquipmentInventorySlot slot)
	{
		List<Rs2ItemModel> equipment = currentEquipment;
		return equipment == null ? null
			: equipment.stream().filter(item -> item.getSlot() == slot.getSlotIdx()).findFirst().orElse(null);
	}
}
