// Adapted from the leaguesfiremaking plugin (LogType).
package net.runelite.client.plugins.microbot.firemakingplus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Logs burnable for Firemaking, with level requirements and whether the log is members-only.
 */
@Getter
@RequiredArgsConstructor
public enum Logs {
    LOGS("Logs", ItemID.LOGS, 1, false),
    OAK("Oak logs", ItemID.OAK_LOGS, 15, false),
    WILLOW("Willow logs", ItemID.WILLOW_LOGS, 30, false),
    TEAK("Teak logs", ItemID.TEAK_LOGS, 35, true),
    MAPLE("Maple logs", ItemID.MAPLE_LOGS, 45, false),
    MAHOGANY("Mahogany logs", ItemID.MAHOGANY_LOGS, 50, true),
    YEW("Yew logs", ItemID.YEW_LOGS, 60, false),
    MAGIC("Magic logs", ItemID.MAGIC_LOGS, 75, true),
    REDWOOD("Redwood logs", ItemID.REDWOOD_LOGS, 90, true);

    private final String logName;
    private final int itemId;
    private final int levelRequired;
    // Mobile: members-only logs are skipped by progressive mode on a free world.
    private final boolean members;

    public boolean hasRequiredLevel() {
        return Rs2Player.getSkillRequirement(Skill.FIREMAKING, levelRequired);
    }

    /**
     * Highest-level log the player can currently burn (for progressive mode). Mobile: on a free world
     * members-only logs (teak, mahogany, magic, redwood) are skipped, so a free-to-play account is not
     * sent to the bank for logs it cannot have.
     */
    public static Logs getBestForLevel() {
        boolean membersWorld = Rs2Player.isInMemberWorld();
        Logs best = LOGS;
        for (Logs log : values()) {
            if (log.hasRequiredLevel() && (membersWorld || !log.members)) {
                best = log;
            }
        }
        return best;
    }

    /** {@link #getBestForLevel()} for a given Firemaking level and world type, without game reads. */
    static Logs bestFor(int level, boolean membersWorld) {
        Logs best = LOGS;
        for (Logs log : values()) {
            if (level >= log.levelRequired && (membersWorld || !log.members)) {
                best = log;
            }
        }
        return best;
    }

    @Override
    public String toString() {
        return logName;
    }
}
