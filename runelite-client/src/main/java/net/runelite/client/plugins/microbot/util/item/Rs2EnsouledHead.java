/*
 * Adapted from chsami/Microbot util/item/Rs2EnsouledHead.java at
 * e01e0d77d79b354472a5737b3aaab2449eb034fc. BSD 2-Clause; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.item;

import java.util.Arrays;
import java.util.Objects;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.headless.ScriptInputSession;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.util.Text;

/** Upstream ensouled-head catalogue and single-cast helper; no scheduler or repeated automation. */
@Getter
@RequiredArgsConstructor
public enum Rs2EnsouledHead
{
	// Level 16 - Basic Reanimation
	GOBLIN("Ensouled goblin head", 13448, 16, Rs2Spells.BASIC_REANIMATION),
	MONKEY("Ensouled monkey head", 13451, 16, Rs2Spells.BASIC_REANIMATION),
	IMP("Ensouled imp head", 13454, 16, Rs2Spells.BASIC_REANIMATION),
	MINOTAUR("Ensouled minotaur head", 13457, 16, Rs2Spells.BASIC_REANIMATION),
	SCORPION("Ensouled scorpion head", 13460, 16, Rs2Spells.BASIC_REANIMATION),
	BEAR("Ensouled bear head", 13463, 16, Rs2Spells.BASIC_REANIMATION),
	UNICORN("Ensouled unicorn head", 13466, 16, Rs2Spells.BASIC_REANIMATION),

	// Level 41 - Adept Reanimation
	DOG("Ensouled dog head", 13469, 41, Rs2Spells.ADEPT_REANIMATION),
	CHAOS_DRUID("Ensouled chaos druid head", 13472, 41, Rs2Spells.ADEPT_REANIMATION),
	GIANT("Ensouled giant head", 13475, 41, Rs2Spells.ADEPT_REANIMATION),
	OGRE("Ensouled ogre head", 13478, 41, Rs2Spells.ADEPT_REANIMATION),
	ELF("Ensouled elf head", 13481, 41, Rs2Spells.ADEPT_REANIMATION),
	TROLL("Ensouled troll head", 13484, 41, Rs2Spells.ADEPT_REANIMATION),
	HORROR("Ensouled horror head", 13487, 41, Rs2Spells.ADEPT_REANIMATION),

	// Level 72 - Expert Reanimation
	KALPHITE("Ensouled kalphite head", 13490, 72, Rs2Spells.EXPERT_REANIMATION),
	DAGANNOTH("Ensouled dagannoth head", 13493, 72, Rs2Spells.EXPERT_REANIMATION),
	BLOODVELD("Ensouled bloodveld head", 13496, 72, Rs2Spells.EXPERT_REANIMATION),
	TZHAAR("Ensouled tzhaar head", 13499, 72, Rs2Spells.EXPERT_REANIMATION),
	DEMON("Ensouled demon head", 13502, 72, Rs2Spells.EXPERT_REANIMATION),
	HELLHOUND("Ensouled hellhound head", 26997, 72, Rs2Spells.EXPERT_REANIMATION),

	// Level 90 - Master Reanimation
	AVIANSIE("Ensouled aviansie head", 13505, 90, Rs2Spells.MASTER_REANIMATION),
	ABYSSAL("Ensouled abyssal head", 13508, 90, Rs2Spells.MASTER_REANIMATION),
	DRAGON("Ensouled dragon head", 13511, 90, Rs2Spells.MASTER_REANIMATION),
	ALL_IN_BANK("All", 1337, 0, null);

	private final String name;
	private final int itemId;
	private final int magicLevelRequirement;
	private final Rs2Spells magicSpell;

	public boolean hasRequirements()
	{
		return magicSpell != null && MobileActions.read(() -> Rs2Magic.canCast(magicSpell), false);
	}

	public Rs2ItemModel getInventoryHead()
	{
		// ALL_IN_BANK is a configuration sentinel, never a real reanimation target.
		return magicSpell == null ? null : Rs2Inventory.get(itemId);
	}

	/**
	 * Selects this head's spell, waits for inventory, and dispatches one spell-on-item.
	 * True means dispatched, not that the game spawned a reanimated NPC. Off-client-thread only.
	 */
	public boolean reanimate()
	{
		Client client = Microbot.getClient();
		ScriptInputSession owner = MobileActions.currentPermission();
		long epoch = ScriptInputSession.inputEpoch();
		if (magicSpell == null || client == null || client.isClientThread() || !owns(owner, epoch)) return false;
		Rs2ItemModel head = getInventoryHead();
		if (head == null || !Rs2Magic.cast(magicSpell) || !owns(owner, epoch)) return false;
		// The inventory may hide the spellbook immediately after selection, so do not
		// rediscover the spell through visible spellbook children here.
		int spellWidget = MobileActions.read(() ->
		{
			Widget selected = Microbot.getClient().getSelectedWidget();
			String expected = magicSpell.getMagicAction().getName();
			return Microbot.getClient().isWidgetSelected() && selected != null
					&& (spellNameMatches(selected.getName(), expected) || spellNameMatches(selected.getText(), expected))
					? selected.getId() : -1;
		}, -1);
		if (spellWidget < 0 || !Global.sleepUntil(() -> !owns(owner, epoch)
				|| Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY, 5_000) || !owns(owner, epoch)) return false;
		// Mobile's spell-on-item opcode is the Use path (as in Rs2Magic.castOnItem).
		// Re-check selected spell and owning input epoch in the same client-thread action.
		return Global.sleepUntil(() -> !owns(owner, epoch) || MobileActions.read(() ->
		{
			Widget selected = Microbot.getClient().getSelectedWidget();
			return owns(owner, epoch) && Microbot.getClient().isWidgetSelected()
					&& selected != null && selected.getId() == spellWidget
					&& Rs2Inventory.interact(head, "Use");
		}, false), 2_000) && owns(owner, epoch);
	}

	private static boolean spellNameMatches(String actual, String expected)
	{
		return actual != null && Text.removeTags(actual).trim().equalsIgnoreCase(expected);
	}

	private static boolean owns(ScriptInputSession owner, long epoch)
	{
		return owner != null && MobileActions.currentPermission() == owner
				&& owner.allows(epoch) && MobileActions.active();
	}

	public static Rs2EnsouledHead getReanimatableHead()
	{
		return Rs2Inventory.items().map(Rs2EnsouledHead::forItem).filter(Objects::nonNull)
				.filter(Rs2EnsouledHead::hasRequirements).findFirst().orElse(null);
	}

	public static Rs2EnsouledHead forItem(Rs2ItemModel item)
	{
		if (item == null) return null;
		return Arrays.stream(values()).filter(head -> head.magicSpell != null && head.itemId == item.getId())
				.findFirst().orElse(null);
	}

	public static boolean isNpcReanimated(Rs2NpcModel npc)
	{
		if (npc == null) return false;
		String name = npc.getName(); // Legacy model marshals this read onto the client thread.
		return name != null && name.contains("Reanimated");
	}
}
