package net.runelite.client.plugins.microbot.woodcutting;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingTree;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Progressive mode re-runs its tree and location search (one client-thread read per requirement)
 * only when the woodcutting level or the world changes, not on every 100 ms loop pass.
 */
public class AutoWoodcuttingProgressiveTest
{
    static
    {
        System.setProperty("runelite.bridge", "true");
    }

    private final AtomicInteger level = new AtomicInteger(59);
    private final AtomicInteger world = new AtomicInteger(301);
    private Client client;
    private AutoWoodcuttingScript script;
    private AutoWoodcuttingConfig config;

    @Before
    public void setUp() throws Exception
    {
        client = mock(Client.class);
        when(client.isClientThread()).thenReturn(true);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getRealSkillLevel(Skill.WOODCUTTING)).thenAnswer(i -> level.get());
        when(client.getWorld()).thenAnswer(i -> world.get());
        when(client.getWorldType()).thenAnswer(i -> world.get() == 301
            ? EnumSet.noneOf(WorldType.class) : EnumSet.of(WorldType.MEMBERS));
        Player player = mock(Player.class);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3230, 0));
        when(client.getLocalPlayer()).thenReturn(player);
        ClientThread thread = ReadApiFixture.clientThreadFor(client);
        EventBus events = new EventBus();
        MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
            List.of(new MicrobotReadServices(client, thread, events)));
        config = new AutoWoodcuttingConfig()
        {
            @Override
            public boolean progressiveMode()
            {
                return true;
            }
        };
        script = new AutoWoodcuttingPlugin(config, mock(OverlayManager.class)).autoWoodcuttingScript;
    }

    @After
    public void tearDown()
    {
        MicrobotRuntime.uninstall();
    }

    private long worldTypeReads()
    {
        return mockingDetails(client).getInvocations().stream()
            .filter(call -> call.getMethod().getName().equals("getWorldType")).count();
    }

    @Test
    public void selectionIsReusedUntilTheLevelOrWorldChanges()
    {
        script.updateActiveTree(config);
        assertEquals("level 59 on a free world: teak and maple spots are members-only", WoodcuttingTree.WILLOW, script.getActiveTree());
        assertNotNull(script.getActiveLocation());
        int searchReads = mockingDetails(client).getInvocations().size();
        long membershipChecks = worldTypeReads();
        assertTrue("the first search checks each members-only spot", membershipChecks > 0);

        for (int pass = 0; pass < 10; pass++)
        {
            script.updateActiveTree(config);
        }
        assertEquals("no requirement is re-checked", membershipChecks, worldTypeReads());
        assertTrue("an unchanged pass reads only the level and the world",
            mockingDetails(client).getInvocations().size() - searchReads <= 10 * 4);
        assertEquals(WoodcuttingTree.WILLOW, script.getActiveTree());

        world.set(302);
        script.updateActiveTree(config);
        assertEquals("members world: Seers' maples", WoodcuttingTree.MAPLE, script.getActiveTree());

        level.set(15);
        script.updateActiveTree(config);
        assertEquals(WoodcuttingTree.OAK, script.getActiveTree());
    }
}
