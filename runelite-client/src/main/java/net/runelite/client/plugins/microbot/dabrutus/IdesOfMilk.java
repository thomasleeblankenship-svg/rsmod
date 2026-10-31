package net.runelite.client.plugins.microbot.dabrutus;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID1;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * The Ides of Milk, ported from Da-Quester's {@code quests.TheIdesOfMilk} (itself from Quest Helper): stage =
 * varbit {@code COWQUEST} (20106). Wiki quick guide order: Cassius ("Yes") -> Gillie ("Can you share what makes your cows so
 * productive?") -> Seth -> Seth's shelves (The groats principles) -> Cassius -> drink sample 1 -> Cassius -> Duke Horacio
 * (castle, 1st floor) -> Gillie -> drink sample 2 -> Gillie -> the Bull (Brutus, the pen gate north-east of Gillie) ->
 * Gillie -> Cassius. Reward: cowbell amulet and a 1,000 xp combat lamp from Gillie.
 *
 * <p>No requirements; 15 combat recommended. The Bull fight is Brutus himself (specials up to 19, ignore prayer), so
 * it uses the script's fight with dodging, and food is fetched first.
 */
final class IdesOfMilk
{
	static final WorldPoint CASSIUS = new WorldPoint(3171, 3277, 0);
	static final WorldPoint SETH = new WorldPoint(3228, 3291, 0);
	static final WorldPoint SETH_SHELF = new WorldPoint(3227, 3287, 0);
	static final WorldPoint GILLIE = new WorldPoint(3253, 3270, 0);
	static final WorldPoint DUKE = new WorldPoint(3210, 3220, 1);
	static final WorldPoint CASTLE_STAIRS = new WorldPoint(3205, 3208, 0);
	static final WorldPoint BULL_PEN = new WorldPoint(3262, 3294, 0);
	/** Lumbridge castle, first floor (where the Duke is). */
	private static final int[] CASTLE_1 = {3203, 3206, 3218, 3231};

	private final DaBrutusScript script;
	private int lastStage = -1;
	private long stageSinceMs;

	IdesOfMilk(DaBrutusScript script)
	{
		this.script = script;
	}

	static boolean done()
	{
		// Stage 22 is the end (live 09:49: all three accounts read 22 while the quest state still said not finished).
		return stage() >= 22 || Rs2Player.getQuestState(Quest.THE_IDES_OF_MILK) == QuestState.FINISHED;
	}

	static int stage()
	{
		return Microbot.getVarbitValue(VarbitID.COWQUEST);
	}

	/** Quest-wide dialogue options. */
	static final String[] OPTIONS = {"Yes.", "Can you share what makes your cows so productive?"};

	/** One quest action. */
	void step()
	{
		if (Act.inDialogue())
		{
			Act.dialogue(OPTIONS);
			return;
		}
		int stage = stage();
		if (stage != lastStage)
		{
			lastStage = stage;
			stageSinceMs = System.currentTimeMillis();
			Microbot.log(DaBrutusScript.tag() + "The Ides of Milk: stage " + stage);
		}
		switch (stage)
		{
			case 0:
			case 1:
			case 2:
			case 8:
			case 21:
				script.status("quest: talk to Cassius");
				Act.talk(CASSIUS, NpcID.COWBOSS_FARMER, "Yes.");
				return;
			case 3:
				if (Microbot.getVarbitValue(VarbitID.COWQUEST_GILLIE_INFORMATION) == 1)
				{
					script.status("quest: talk to Seth Groats");
					Act.talk(SETH, NpcID.FAVOUR_SETH_GROATS);
				}
				else
				{
					script.status("quest: talk to Gillie Groats");
					Act.talk(GILLIE, NpcID.GILLIE_THE_MILKMAID, "Can you share what makes your cows so productive?");
				}
				return;
			case 4:
			case 5:
				if (Rs2Player.getWorldLocation() != null && (stage == 5
					|| net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.hasItem(ItemID.COWQUEST_HUSBANDRY_BOOK)))
				{
					script.status("quest: bring Cassius the book");
					Act.talk(CASSIUS, NpcID.COWBOSS_FARMER);
				}
				else
				{
					script.status("quest: search Seth's shelves for the book");
					Act.objAction(SETH_SHELF, "Search", ObjectID1.COWQUEST_SETH_SHELF);
				}
				return;
			case 6:
				script.status("quest: drink the first milk sample");
				if (!Act.item(ItemID.COWQUEST_MILK_SAMPLE_1, "Drink"))
				{
					Act.talk(CASSIUS, NpcID.COWBOSS_FARMER);
				}
				return;
			case 10:
				if (inCastleFirstFloor())
				{
					script.status("quest: talk to Duke Horacio");
					Act.talk(DUKE, NpcID.DUKE_OF_LUMBRIDGE);
				}
				else
				{
					script.status("quest: going up to Duke Horacio");
					Act.walk(DUKE, 3);
				}
				return;
			case 12:
			case 16:
			case 20:
				script.status("quest: talk to Gillie Groats");
				Act.talk(GILLIE, NpcID.GILLIE_THE_MILKMAID);
				return;
			case 14:
				script.status("quest: drink the second milk sample");
				if (!Act.item(ItemID.COWQUEST_MILK_SAMPLE_2, "Drink"))
				{
					Act.talk(GILLIE, NpcID.GILLIE_THE_MILKMAID);
				}
				return;
			case 18:
				script.questBull();
				return;
			default:
				// In-between stages (cutscenes, a line still to be read): wait; after a minute try the next NPC.
				script.status("quest: stage " + stage + ", waiting");
				if (System.currentTimeMillis() - stageSinceMs > 60_000)
				{
					stageSinceMs = System.currentTimeMillis();
					Act.talk(stage < 10 ? CASSIUS : GILLIE, stage < 10 ? NpcID.COWBOSS_FARMER : NpcID.GILLIE_THE_MILKMAID);
				}
		}
	}

	private static boolean inCastleFirstFloor()
	{
		WorldPoint p = Rs2Player.getWorldLocation();
		return p != null && p.getPlane() == 1 && p.getX() >= CASTLE_1[0] && p.getY() >= CASTLE_1[1] && p.getX() <= CASTLE_1[2]
			&& p.getY() <= CASTLE_1[3];
	}
}
