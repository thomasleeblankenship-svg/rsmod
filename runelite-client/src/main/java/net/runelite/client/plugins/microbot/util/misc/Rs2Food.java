package net.runelite.client.plugins.microbot.util.misc;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.runelite.api.gameval.ItemID;

/**
 * Common cooked foods: item id, hitpoints healed and display name. Microbot scripts use it as a
 * config choice ({@code default Rs2Food food()}) and read {@link #getId()} / {@link #getName()}.
 * Written for RuneMobile; heal values are the fixed amounts (variable foods use their usual heal).
 */
public enum Rs2Food
{
	SHRIMPS(ItemID.SHRIMP, 3, "Shrimps"),
	ANCHOVIES(ItemID.ANCHOVIES, 1, "Anchovies"),
	SARDINE(ItemID.SARDINE, 4, "Sardine"),
	HERRING(ItemID.HERRING, 5, "Herring"),
	MACKEREL(ItemID.MACKEREL, 6, "Mackerel"),
	TROUT(ItemID.TROUT, 7, "Trout"),
	COD(ItemID.COD, 7, "Cod"),
	PIKE(ItemID.PIKE, 8, "Pike"),
	SALMON(ItemID.SALMON, 9, "Salmon"),
	TUNA(ItemID.TUNA, 10, "Tuna"),
	LOBSTER(ItemID.LOBSTER, 12, "Lobster"),
	BASS(ItemID.BASS, 13, "Bass"),
	SWORDFISH(ItemID.SWORDFISH, 14, "Swordfish"),
	MONKFISH(ItemID.MONKFISH, 16, "Monkfish"),
	KARAMBWAN(ItemID.TBWT_COOKED_KARAMBWAN, 18, "Cooked karambwan"),
	SHARK(ItemID.SHARK, 20, "Shark"),
	SEA_TURTLE(ItemID.SEATURTLE, 21, "Sea turtle"),
	MANTA_RAY(ItemID.MANTARAY, 22, "Manta ray"),
	DARK_CRAB(ItemID.DARK_CRAB, 22, "Dark crab"),
	ANGLERFISH(ItemID.ANGLERFISH, 22, "Anglerfish"),
	POTATO_WITH_CHEESE(ItemID.POTATO_CHEESE, 16, "Potato with cheese"),
	TUNA_POTATO(ItemID.POTATO_TUNA_SWEETCORN, 22, "Tuna potato"),
	CURRY(ItemID.CURRY, 19, "Curry"),
	JUG_OF_WINE(ItemID.JUG_WINE, 11, "Jug of wine"),
	CAKE(ItemID.CAKE, 4, "Cake"),
	BREAD(ItemID.BREAD, 5, "Bread"),
	COOKED_MEAT(ItemID.COOKED_MEAT, 3, "Cooked meat"),
	COOKED_CHICKEN(ItemID.COOKED_CHICKEN, 3, "Cooked chicken");

	private final int id;
	private final int heal;
	private final String name;

	Rs2Food(int id, int heal, String name)
	{
		this.id = id;
		this.heal = heal;
		this.name = name;
	}

	public int getId()
	{
		return id;
	}

	/** Hitpoints restored by one bite. */
	public int getHeal()
	{
		return heal;
	}

	public String getName()
	{
		return name;
	}

	/** The name, so config dropdowns read naturally. */
	@Override
	public String toString()
	{
		return name;
	}

	/** Every listed food's item id. */
	public static Set<Integer> getIds()
	{
		return Arrays.stream(values()).map(Rs2Food::getId).collect(Collectors.toSet());
	}
}
