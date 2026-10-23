/*
 * Copied from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/magic/Rs2Spellbook.java.
 * BSD 2-Clause License; see LICENSE. Mobile adaptation: switching (switchTo, switchViaNpc, altar/Tyss and POH lookups)
 * is not included; it belongs with the actions.
 */
package net.runelite.client.plugins.microbot.util.magic;



import lombok.Getter;
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
}
