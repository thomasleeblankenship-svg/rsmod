package net.runelite.client.plugins.microbot.util.walker;

import java.util.Locale;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;

/**
 * As upstream's {@code Rs2WalkerDoors.handleStrongholdOfSecurityAnswer}: the Stronghold of Security's
 * doors (Gate of War, Rickety door, Oozing barrier, Portal of Death) ask an account-security question
 * the first time they are opened. After the walker opens one, this answers the question from
 * {@link StrongholdAnswer}. Doors that ask nothing just let the player through.
 */
public final class StrongholdDoors
{
	/** As upstream: the Stronghold of Security's map regions. */
	static final Set<Integer> REGIONS = Set.of(7505, 7504, 7760, 7503, 7759, 7758, 7757, 8013, 7756, 8012, 8017, 8530, 9297);
	private static final int QUESTION_WAIT_MILLIS = 5_000;
	private static final int MAX_ATTEMPTS = 5;

	private StrongholdDoors()
	{
	}

	/** Whether {@code point} is inside the Stronghold of Security. */
	public static boolean isInStronghold(WorldPoint point)
	{
		return point != null && REGIONS.contains(point.getRegionID());
	}

	/** Whether {@code name} names a Stronghold door that is opened with "Open" (any case). */
	public static boolean isStrongholdDoor(String name)
	{
		if (name == null)
		{
			return false;
		}
		String lower = name.toLowerCase(Locale.ENGLISH);
		return lower.equals("gate of war") || lower.equals("rickety door") || lower.equals("oozing barrier")
			|| lower.equals("portal of death");
	}

	/** Whether the dialogue text is one of the doors' introductions, shown before the question. */
	static boolean isIntroduction(String text)
	{
		String lower = text == null ? "" : text.toLowerCase(Locale.ENGLISH);
		return lower.contains("two-factor authentication options") || lower.contains("hopefully you will learn<br>much from us.");
	}

	/**
	 * As upstream: after a door was opened, waits up to 5 s for its question and answers it. True when
	 * no question came (the door let the player through) or it was answered; false when it asked
	 * something {@link StrongholdAnswer} does not know.
	 */
	public static boolean answerIfAsked()
	{
		if (!Global.sleepUntil(Rs2Dialogue::isInDialogue, QUESTION_WAIT_MILLIS))
		{
			return true;
		}
		if (isIntroduction(Rs2Dialogue.getDialogueText()))
		{
			Rs2Dialogue.sleepUntilHasContinue();
			Global.sleepUntil(() -> !Rs2Dialogue.hasContinue() || text().contains("to pass you must answer me"),
				Rs2Dialogue::clickContinue, 5_000, 700);
			if (!Rs2Dialogue.isInDialogue())
			{
				return true;
			}
		}
		String answer = null;
		// As upstream: more dialogue can come before the question, so look a few times.
		for (int attempt = 0; answer == null && attempt < MAX_ATTEMPTS; attempt++)
		{
			String question = Rs2Dialogue.getDialogueText();
			answer = question == null ? null : StrongholdAnswer.findAnswer(question);
			if (answer == null)
			{
				Rs2Dialogue.clickContinue();
				Global.sleep(800);
			}
		}
		if (answer == null)
		{
			return false;
		}
		Rs2Dialogue.clickContinue();
		Rs2Dialogue.sleepUntilSelectAnOption();
		if (!Rs2Dialogue.clickOption(answer))
		{
			return false;
		}
		Rs2Dialogue.sleepUntilHasContinue();
		Global.sleepUntil(() -> !Rs2Dialogue.hasContinue(), Rs2Dialogue::clickContinue, 5_000, 700);
		return true;
	}

	private static String text()
	{
		String text = Rs2Dialogue.getDialogueText();
		return text == null ? "" : text.toLowerCase(Locale.ENGLISH);
	}
}
