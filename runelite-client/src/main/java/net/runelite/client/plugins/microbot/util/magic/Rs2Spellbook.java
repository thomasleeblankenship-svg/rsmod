/*
 * Copied from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/magic/Rs2Spellbook.java.
 * BSD 2-Clause License; see LICENSE. Mobile adaptation: switchTo and switchViaNpc wait
 * on the observed spellbook; the unfinished magic-cape and POH switches are not included.
 */
package net.runelite.client.plugins.microbot.util.magic;



import lombok.Getter;
import net.runelite.api.GameObject;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

// TODO: Uncomment when implementing actual spellbook switching functionality
// // 
/**
 * Enum representing the different spellbooks in RuneScape with switching mechanics.
 * The integer values correspond to the varbit {@link net.runelite.api.gameval.VarbitID#SPELLBOOK}.
 * <p>
 * Enhanced with spellbook switching information including locations, methods,
 * object IDs, NPC IDs, and dialogue requirements.
 */
@Getter
public enum Rs2Spellbook {
    /**
     * Standard/Modern Spellbook - Default spellbook available to all players
     * Can be accessed from any other spellbook altar or via Magic cape
     */
    MODERN(0, 
           null, // No specific location - can be accessed from any altar                      
           null,
           null,
           "Standard spellbook - accessible from any other spellbook altar"),
    
    /**
     * Ancient Magicks Spellbook - Unlocked after Desert Treasure I quest
     * Located in Ancient Pyramid (Level 4 altar room)
     */
    ANCIENT(1,
            new WorldPoint(3233, 9315, 0), // Ancient Pyramid Level 4 altar room
            ObjectID.DT_ZAROS_ALTAR, // Ancient Pyramid altar object ID (ObjectID.ALTAR_6552)            
            Quest.DESERT_TREASURE_I,
            "Ancient Pyramid altar - pray to switch between Ancient Magicks and Standard spellbook"),
    
    /**
     * Lunar Spellbook - Unlocked after Lunar Diplomacy quest
     * Located at Astral Altar on Lunar Isle
     */
    LUNAR(2,
          new WorldPoint(2158, 3866, 0), // Astral Altar on Lunar Isle (south-east)
          ObjectID.ASTRAL_ALTAR, // Astral Altar object ID          
          Quest.LUNAR_DIPLOMACY,
          "Astral Altar on Lunar Isle - pray to switch to Lunar spellbook or back to Standard"),
    
    /**
     * Arceuus Spellbook - No quest requirement, accessible to all players
     * Located at Dark Altar north of Arceuus - requires speaking to Tyss NPC
     */
    ARCEUUS(3,
            new WorldPoint(1712, 3883, 0), // Dark Altar location north of Arceuus
            NpcID.ARCEUUS_DARKGUARDIAN, // Tyss NPC ID
            null,
            "Dark Altar north of Arceuus - speak to Tyss to access Arceuus spellbook");

    private final int value;
    private final WorldPoint switchLocation;
    private final Integer gameID; // Object ID for altars, null if not applicable, for aceueus spellbook this is the NPC ID        
    private final Quest requiredQuest;
    private final String description;

    /**
     * Constructor for Rs2Spellbook enum
     */
    Rs2Spellbook(int value, WorldPoint switchLocation, Integer gameID, Quest requiredQuest, String description) {
        this.value = value;
        this.switchLocation = switchLocation;
        this.gameID = gameID;        
        this.requiredQuest = requiredQuest;
        this.description = description;
    }


    /**
     * Get the spellbook from its value
     * 
     * @param value The numeric value of the spellbook
     * @return The corresponding Rs2Spellbook enum
     */
    public static Rs2Spellbook fromValue(int value) {
        for (Rs2Spellbook spellbook : Rs2Spellbook.values()) {
            if (spellbook.getValue() == value) {
                return spellbook;
            }
        }
        return MODERN;  // Default to modern spellbook
    }
    
    /**
     * Get the current active spellbook
     * 
     * @return The currently active Rs2Spellbook
     */
    public static Rs2Spellbook getCurrentSpellbook() {
        return fromValue(Microbot.getVarbitValue(VarbitID.SPELLBOOK));
    }
    
    /**
     * Check if the Lunar spellbook is unlocked (requires Lunar Diplomacy quest completion)
     * Note: His Faithful Servants quest was merged into Lunar Diplomacy
     * 
     * @return true if Lunar spellbook is available
     */
    public static boolean isLunarSpellBookUnlocked() {
        return Rs2Player.getQuestState(Quest.LUNAR_DIPLOMACY) == QuestState.FINISHED;               
    }
    
    /**
     * Check if the Ancient Magicks spellbook is unlocked (requires Desert Treasure I quest completion)
     * 
     * @return true if Ancient Magicks spellbook is available
     */
    public static boolean isAncientSpellBookUnlocked() {
        return Rs2Player.getQuestState(Quest.DESERT_TREASURE_I) == QuestState.FINISHED;                
    }
    public static boolean isArceuusSpellBookFullyUnlocked() { //some Arceuus spells require quests
        // Currently, no quests are required for Arceuus spellbook, but this can be expanded in the future
        return Rs2Player.getQuestState(Quest.A_KINGDOM_DIVIDED) == QuestState.FINISHED;                
    }
    
    /**
     * Check if the Arceuus spellbook is unlocked
     * Note: Arceuus spellbook is available to all players by default, no quest requirement -> full unlock required a kingdom divided quest completion
     * 
     * @return true (always available)
     */
    public static boolean isArceuusSpellBookUnlocked() {
        return true; // No quest requirement for Arceuus spellbook               
    }
    
    /**
     * Check if spellbook was changed by Lunar magic (for spellbook swap spell)
     * 
     * @return true if spellbook was changed via lunar magic
     */
    public static boolean isSpellbookChangedByLunar() {
        return Microbot.getVarbitValue(VarbitID.LUNAR_SPELLBOOK_CHANGE) == 1;                
    }
    
    /**
     * Check if this spellbook is currently unlocked/available to the player
     * 
     * @return true if the spellbook is unlocked and can be used
     */
    public boolean isUnlocked() {
        switch (this) {
            case MODERN:
                return true; // Always available
            case ANCIENT:
                return isAncientSpellBookUnlocked();
            case LUNAR:
                return isLunarSpellBookUnlocked();
            case ARCEUUS:
                return isArceuusSpellBookUnlocked();
            default:
                return false;
        }
    }

    // ---- switching (actions) ----

    /** As upstream: altars are searched for within this many tiles of the player. */
    private static final int ALTAR_SEARCH_RADIUS = 10;

    /**
     * As upstream: switches to this spellbook where the player already stands (it does not walk):
     * Ancient and Lunar by praying at their altar, Arceuus by talking to Tyss, and Modern through
     * the altar of the current book (Tyss when leaving Arceuus). True when already on it or when
     * the switch is observed.
     */
    public boolean switchTo() {
        if (!isUnlocked()) {
            Microbot.log("Cannot switch to " + this.name() + " spellbook - not unlocked");
            return false;
        }
        Rs2Spellbook current = getCurrentSpellbook();
        if (current == this) {
            return true;
        }
        switch (this) {
            case MODERN:
                return current == ARCEUUS ? switchViaNpc() : switchViaAltar();
            case ANCIENT:
            case LUNAR:
                return switchLocation != null && switchViaAltar();
            case ARCEUUS:
                return switchLocation != null && switchViaNpc();
            default:
                return false;
        }
    }

    /** As upstream: prays at the matching altar within reach, then waits for the book to change. */
    private boolean switchViaAltar() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        Rs2Spellbook altarBook = this;
        if (this == MODERN) {
            Rs2Spellbook current = getCurrentSpellbook();
            if (current == ANCIENT || current == LUNAR) {
                altarBook = current;
            } else if (current == ARCEUUS) {
                if (!Rs2GameObject.getGameObjects(o -> java.util.Objects.equals(ANCIENT.getGameID(), o.getId()), ALTAR_SEARCH_RADIUS).isEmpty()) {
                    altarBook = ANCIENT;
                } else if (!Rs2GameObject.getGameObjects(o -> java.util.Objects.equals(LUNAR.getGameID(), o.getId()), ALTAR_SEARCH_RADIUS).isEmpty()) {
                    altarBook = LUNAR;
                } else {
                    Microbot.log("No suitable altar found to switch from Arceuus to Modern spellbook");
                    return false;
                }
            } else {
                return true;
            }
        }
        Integer altarId = altarBook.getGameID();
        if (altarId == null || altarId == -1 || altarBook.getSwitchLocation() == null || playerLocation == null
            || playerLocation.distanceTo(altarBook.getSwitchLocation()) >= ALTAR_SEARCH_RADIUS) {
            Microbot.log("Not at the " + altarBook.name() + " spellbook altar");
            return false;
        }
        GameObject altar = Rs2GameObject.getGameObjects(o -> altarId.equals(o.getId()), ALTAR_SEARCH_RADIUS).stream()
            .findFirst().orElse(null);
        if (altar == null || !Rs2GameObject.interact(altar, "Pray")) {
            return false;
        }
        Rs2Player.waitForAnimation();
        return Global.sleepUntil(() -> getCurrentSpellbook() == this, 5000);
    }

    /**
     * As upstream: talks to Tyss to switch between Arceuus and Modern ("Spellbook", continue, then
     * the option for the other book). Only from Arceuus or Modern.
     */
    public static boolean switchViaNpc() {
        String option;
        switch (getCurrentSpellbook()) {
            case ARCEUUS:
                option = "I'd like to stop using your spellbook now.";
                break;
            case MODERN:
                option = "Can I try the magicks myself?";
                break;
            default:
                Microbot.log("Tyss NPC switching not supported for current spellbook: " + getCurrentSpellbook());
                return false;
        }
        return Rs2Npc.interact("Tyss", "Spellbook")
            && Rs2Dialogue.sleepUntilHasContinue()
            && Rs2Dialogue.clickContinue()
            && Rs2Dialogue.sleepUntilSelectAnOption()
            && Rs2Dialogue.clickOption(option)
            && Rs2Dialogue.sleepUntilHasContinue();
    }

    /** As upstream: an Ancient or Lunar altar (any book except Arceuus, which uses Tyss) is within 10 tiles. */
    public static boolean isAltarNearby() {
        for (Rs2Spellbook spellbook : values()) {
            if (spellbook.getGameID() != null && spellbook != ARCEUUS
                    && !Rs2GameObject.getGameObjects(o -> spellbook.getGameID().equals(o.getId()), ALTAR_SEARCH_RADIUS).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** As upstream: Tyss (the Arceuus switch NPC) is in the scene. */
    public static boolean isTyssNearby() {
        return Rs2Npc.getNpc(ARCEUUS.getGameID()) != null;
    }

    /** As upstream: the player is in their house (upstream assumes house furniture can switch the book). */
    public static boolean canSwitchInPOH() {
        return net.runelite.client.plugins.microbot.util.poh.PohPresence.isInHouse();
    }
}
