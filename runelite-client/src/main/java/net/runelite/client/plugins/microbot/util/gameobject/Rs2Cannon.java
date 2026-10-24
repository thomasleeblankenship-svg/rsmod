/*
 * Adapted from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/gameobject/Rs2Cannon.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.util.gameobject;

import net.runelite.api.ObjectID;
import net.runelite.api.TileObject;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

/**
 * The player's own dwarf multicannon: repair it when broken and refill it with cannonballs.
 * <p>
 * As upstream, only a cannon standing where the player set theirs up (the owned-cannon varp) is
 * touched. Mobile adaptation: a refill is one "Fire" (the server walks to the cannon) followed by
 * a bounded wait for the ammo count to rise; upstream's second click, fixed sleep and pausing of
 * every other script are not needed.
 */
public final class Rs2Cannon
{
	private static final int[] BROKEN_CANNONS = {ObjectID.BROKEN_MULTICANNON_14916, ObjectID.BROKEN_MULTICANNON_43028};
	private static final int[] CANNONS = {ObjectID.DWARF_MULTICANNON, ObjectID.DWARF_MULTICANNON_43027};

	private Rs2Cannon()
	{
	}

	/** As upstream: "Repair" on the player's broken cannon. False when there is none. */
	public static boolean repair()
	{
		TileObject broken = ownedCannon(BROKEN_CANNONS);
		if (broken == null)
		{
			return false;
		}
		Microbot.status = "Repairing Cannon";
		return Rs2GameObject.interact(broken, "Repair");
	}

	/** As upstream: refills when 10 to 15 or fewer cannonballs are left. */
	public static boolean refill()
	{
		return refill(Rs2Random.between(10, 15));
	}

	/**
	 * As upstream: when at least 15 cannonballs are carried and no more than
	 * {@code cannonRefillAmount} are left in the player's cannon, fires it to load more and waits
	 * (up to 10 s) for the count to rise.
	 */
	public static boolean refill(int cannonRefillAmount)
	{
		if (!Rs2Inventory.hasItemAmount("cannonball", 15, true))
		{
			return false;
		}
		int before = ammo();
		if (before > cannonRefillAmount)
		{
			return false;
		}
		TileObject cannon = ownedCannon(CANNONS);
		if (cannon == null)
		{
			return false;
		}
		Microbot.status = "Refilling Cannon";
		return Rs2GameObject.interact(cannon, "Fire") && Global.sleepUntil(() -> ammo() > before, 10_000);
	}

	private static int ammo()
	{
		return MobileActions.read(() -> Microbot.getClient().getVarpValue(VarPlayer.CANNON_AMMO), 0);
	}

	/** The first of these cannon objects standing on the player's own cannon spot, or null. */
	private static TileObject ownedCannon(int[] ids)
	{
		for (int id : ids)
		{
			TileObject cannon = Rs2GameObject.findObjectById(id);
			if (cannon != null && isOwnedCannon(cannon))
			{
				return cannon;
			}
		}
		return null;
	}

	/** As upstream: the owned-cannon varp holds the cannon's south-west tile. */
	private static boolean isOwnedCannon(TileObject cannon)
	{
		int ownedCoord = Microbot.getVarbitPlayerValue(VarPlayerID.OWNEDMCANNON);
		if (ownedCoord <= 0)
		{
			return false;
		}
		WorldPoint location = MobileActions.read(cannon::getWorldLocation, null);
		return location != null
			&& new WorldPoint(location.getX() - 1, location.getY() - 1, location.getPlane()).equals(WorldPoint.fromCoord(ownedCoord));
	}
}
