package net.runelite.client.plugins.microbot.woodcutting.forestry;

import java.util.List;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Pairs of shapes/colours must cancel; the unique shape and colour is the target. */
public class RitualEventTest
{
    private static Rs2NpcModel circle(int offset)
    {
        Rs2NpcModel npc = mock(Rs2NpcModel.class);
        when(npc.getId()).thenReturn(NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1 + offset);
        return npc;
    }

    @Test
    public void selectsOnlyCircleWhoseShapeAndColourAreBothUnique()
    {
        Rs2NpcModel odd = circle(10);
        assertSame(odd, new RitualEvent(null).solveCircles(List.of(
                circle(0), circle(1), circle(4), circle(5), odd)));
    }

    @Test
    public void refusesIncompleteSpawnSet()
    {
        assertNull(new RitualEvent(null).solveCircles(List.of(circle(0), circle(1), circle(4), circle(5))));
    }
}
