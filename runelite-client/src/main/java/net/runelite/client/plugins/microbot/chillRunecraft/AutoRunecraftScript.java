package net.runelite.client.plugins.microbot.chillRunecraft;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.chillRunecraft.Altars;
import net.runelite.client.plugins.microbot.chillRunecraft.AutoRunecraftConfig;
import net.runelite.client.plugins.microbot.chillRunecraft.States;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

/**
 * ChillX's auto runecrafter: banks for essence and a talisman or tiara, enters the selected altar's ruins,
 * crafts runes and leaves through the portal. Adapted for Microbot Mobile (rune essence on free worlds,
 * steps that advance only once their click is accepted); see MOBILE_PORT.md in this package.
 */
@Slf4j
public class AutoRunecraftScript extends Script
{
    // Mobile: free-to-play accounts can only use rune essence; pure essence is used on members worlds.
    static final int RUNE_ESSENCE = ItemID.BLANKRUNE;
    static final int PURE_ESSENCE = ItemID.BLANKRUNE_HIGH;

    private States state;
    private Altars altar;
    private boolean initialise;
    public static int runsCompleted = 0;
    public static int initialRunecraftLevel;
    public static int runecraftLevel;
    public static int initialRunecraftXp;
    public static int runecraftXp;
    // Mobile: enableAutoRunOn is global; the value before start is put back on stop.
    private boolean autoRunBefore = true;

    public boolean run(AutoRunecraftConfig config)
    {
        autoRunBefore = Microbot.enableAutoRunOn;
        Microbot.enableAutoRunOn = true;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyRunecraftingSetup();
        Rs2Antiban.setActivity(Activity.GENERAL_RUNECRAFT);

        altar = config.ALTAR();

        initialise = true;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                long startTime = System.currentTimeMillis();

                if (initialise)
                {
                    // Mobile: the Nature altar is members-only; stop instead of walking there on a free world.
                    if (altar == Altars.NATURE_ALTAR && !Rs2Player.isInMemberWorld())
                    {
                        stop("The Nature altar needs a members world.");
                        return;
                    }
                    initialRunecraftXp = runecraftExperience();
                    initialRunecraftLevel = Rs2Player.getRealSkillLevel(Skill.RUNECRAFT);

                    if (!Rs2Inventory.hasItem(altar.getTalismanName()) && !Rs2Equipment.isWearing(altar.getTiaraName()))
                    {
                        Microbot.log("No Talisman / Tiara found - banking");
                        state = States.BANKING;
                        initialise = false;
                        return;
                    }

                    if (!Rs2Inventory.hasItem(PURE_ESSENCE, RUNE_ESSENCE))
                    {
                        Microbot.log("No essence in inventory - banking");
                        state = States.BANKING;
                        initialise = false;
                        return;
                    }
                    state = States.WALKING_TO_ALTAR;
                }

                if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;

                if (Rs2Player.isInteracting()) return;

                switch(state)
                {
                    case BANKING:
                        initialise = false;
                        boolean isBankOpen = Rs2Bank.walkToBankAndUseBank();
                        Microbot.status = "Walking to bank";
                        if (!isBankOpen || !Rs2Bank.isOpen()) return;

                        if (!Rs2Inventory.hasItem(altar.getTalismanName()) && !Rs2Equipment.isWearing(altar.getTiaraName()))
                        {
                            Microbot.status = "Withdrawing Tiara";

                            if (!Rs2Bank.hasBankItem(altar.getTiaraName()))
                            {
                                Microbot.status = "Withdrawing Talisman";
                                if (!Rs2Bank.hasBankItem(altar.getTalismanName()))
                                {
                                    stop("No tiara / talisman in bank!");
                                    return;
                                }
                                Rs2Bank.withdrawOne(altar.getTalismanName());
                            }
                            Rs2Bank.withdrawAndEquip(altar.getTiaraName());
                            Rs2Random.wait(800,1600);
                        }

                        if (Rs2Inventory.hasItem(altar.getRuneName(), false))
                        {
                            Microbot.status = "Depositing runes";
                            Rs2Bank.depositAll(altar.getRuneName(), false);
                            Rs2Random.wait(800, 1600);
                        }

                        Microbot.status = "Withdrawing pure essence";

                        // Mobile: pure essence on a members world when banked, otherwise rune essence.
                        String essence = Rs2Player.isInMemberWorld() && Rs2Bank.hasBankItem("Pure essence", true)
                                ? "Pure essence" : "Rune essence";
                        if (!Rs2Bank.hasBankItem(essence, true))
                        {
                            stop("No essence in bank!");
                            return;
                        }
                        Rs2Bank.withdrawAll(essence, true);
                        Rs2Random.wait(800, 1600);
                        state = States.WALKING_TO_ALTAR;
                        Rs2Bank.closeBank();
                        break;

                    case WALKING_TO_ALTAR:
                        initialise = false;
                        Microbot.status = "Walking to altar";
                        boolean walked = Rs2Walker.walkTo(altar.getAltarWorldPoint(), 2);
                        if (!walked) return;
                        Rs2Random.wait(800, 1600);
                        state = States.ENTERING_ALTAR;
                        break;

                    case ENTERING_ALTAR:
                        initialise = false;
                        Microbot.status = "Entering altar";
                        boolean entered;
                        if (!Rs2Equipment.isWearing(altar.getTiaraName()))
                        {
                            entered = Rs2Inventory.useItemOnObject(altar.getTalismanID(), altar.getAltarRuinsID());
                        }
                        else
                        {
                            entered = Microbot.getRs2TileObjectCache().query().withId(altar.getAltarRuinsID()).interact("Enter");
                        }
                        // Mobile: a refused click (one action per game tick) is retried next loop rather than
                        // moving on to crafting outside the altar.
                        if (!entered)
                        {
                            return;
                        }
                        Rs2Random.wait(800, 1600);
                        sleepUntil(() -> !Rs2Player.isMoving());
                        Rs2Random.wait(2000, 2400);
                        if (!sleepUntil(() -> isObjectNearby(altar.getAltarID()), 5000))
                        {
                            return;
                        }
                        state = States.CRAFTING_RUNES;
                        break;

                    case CRAFTING_RUNES:
                        initialise = false;
                        Microbot.status = "Crafting runes";
                        // Mobile: use whichever essence is carried (rune essence on free worlds).
                        int essenceId = Rs2Inventory.hasItem(PURE_ESSENCE) ? PURE_ESSENCE : RUNE_ESSENCE;
                        if (!Rs2Inventory.useItemOnObject(essenceId, altar.getAltarID())) //could just interact with altar, but I'm too lazy to change this now
                        {
                            return;
                        }
                        Rs2Random.wait(800, 1600);
                        // Mobile: count the run only once the essence is gone; otherwise craft again next loop.
                        sleepUntil(() -> Rs2Player.waitForXpDrop(Skill.RUNECRAFT));
                        Rs2Random.wait(800, 1600);
                        if (Rs2Inventory.hasItem(PURE_ESSENCE, RUNE_ESSENCE))
                        {
                            return;
                        }
                        runsCompleted++;
                        updateLevelXp();
                        state = States.EXITING_ALTAR;
                        break;

                    case EXITING_ALTAR:
                        Microbot.status = "Exiting altar";
                        // Mobile: retry a refused portal click next loop instead of banking from inside the altar.
                        if (!Microbot.getRs2TileObjectCache().query().withId(altar.getPortalID()).interact("Use"))
                        {
                            return;
                        }
                        sleepUntil(() -> !Rs2Player.isMoving());
                        Rs2Random.wait(800, 1600);
                        state = States.BANKING;
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                // Mobile: debug level, not stdout, so a 1 s loop does not flood the device log.
                log.debug("Total time for loop {}", totalTime);

            } catch (Exception ex)
            {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
                // Mobile: stop rather than retry a failing step every second.
                stop("Auto Runecraft stopped after an error: " + ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
        Microbot.enableAutoRunOn = autoRunBefore;
    }

    /** Stops with {@code message}, turning the plugin off (not just this loop) so the Microbot Mobile screen shows it. */
    private void stop(String message)
    {
        Microbot.showMessage(message);
        shutdown();
        Microbot.stopPlugin(AutoRunecraftPlugin.class);
    }

    public void updateLevelXp()
    {
        runecraftLevel = Rs2Player.getRealSkillLevel(Skill.RUNECRAFT);
        runecraftXp = runecraftExperience();
    }

    // Mobile: client reads run on the client thread.
    private static int runecraftExperience()
    {
        return MobileActions.read(() -> Microbot.getClient().getSkillExperience(Skill.RUNECRAFT), 0);
    }

    private static boolean isObjectNearby(int objectId)
    {
        return MobileActions.read(() -> Microbot.getRs2TileObjectCache().query().withId(objectId).first() != null, false);
    }
}