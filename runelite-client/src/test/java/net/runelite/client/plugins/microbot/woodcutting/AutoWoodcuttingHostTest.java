package net.runelite.client.plugins.microbot.woodcutting;

import java.util.List;
import java.util.Optional;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real host/event lifecycle with mocked engine boundaries; no device or live-game claim. */
public class AutoWoodcuttingHostTest
{
    static
    {
        // JavaBridge captures this flag on first load; set it before any test reaches the bridge.
        System.setProperty("runelite.bridge", "true");
    }

    private AutoWoodcuttingPlugin host;
    private OverlayManager overlays;

    @Before
    public void setUp()
    {
        Client client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        ClientThread thread = mock(ClientThread.class);
        when(thread.runOnClientThreadOptional(any(), anyLong())).thenReturn(Optional.empty());
        EventBus events = new EventBus();
        MicrobotReadServices reads = new MicrobotReadServices(client, thread, events);
        MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class), List.of(reads));
        overlays = mock(OverlayManager.class);
        AutoWoodcuttingConfig config = new AutoWoodcuttingConfig()
        {
            @Override
            public boolean enableForestry()
            {
                return true;
            }
        };
        host = new AutoWoodcuttingPlugin(config, overlays);
    }

    @After
    public void tearDown()
    {
        if (host != null)
        {
            host.stopMobile();
        }
        MicrobotRuntime.uninstall();
    }

    @Test
    public void startingHostRegistersEventsWithoutStartingSecondScriptLoop()
    {
        host.startMobile();
        assertEquals(8, Microbot.getBlockingEventManager().getEvents().size());
        assertFalse(host.autoWoodcuttingScript.isRunning());
        verify(overlays).add(any(Overlay.class));
        assertFalse(host.isActive()); // No mobile input permission was explicitly bound.
        assertTrue(Microbot.getBlockingEventManager().getEvents().stream().noneMatch(event -> event.validate()));
    }

    @Test
    public void stopRemovesEveryEventAndOverlayAndResetsCounter()
    {
        host.startMobile();
        host.incrementForestryEventCompleted();
        assertEquals(1, host.getCompletedForestryEventCount());
        host.stopMobile();
        assertTrue(Microbot.getBlockingEventManager().getEvents().isEmpty());
        assertEquals(0, host.getCompletedForestryEventCount());
        verify(overlays).remove(any(Overlay.class));
        assertFalse(host.autoWoodcuttingScript.isRunning());
    }

    @Test
    public void repeatedForestryEnableDoesNotDuplicateEvents()
    {
        host.startMobile();
        ConfigChanged event = new ConfigChanged();
        event.setGroup(AutoWoodcuttingConfig.configGroup);
        event.setKey("enableForestry");
        event.setNewValue("true");
        host.onConfigChanged(event);
        host.onConfigChanged(event);
        assertEquals(8, Microbot.getBlockingEventManager().getEvents().size());
        host.stopMobile();
        host.onConfigChanged(event);
        assertTrue(Microbot.getBlockingEventManager().getEvents().isEmpty());
    }
    @Test
    public void terminalScriptStopClosesOnlyItsCapturedPermission()
    {
        net.runelite.client.headless.ScriptInputSession owned = new net.runelite.client.headless.ScriptInputSession();
        net.runelite.client.plugins.microbot.mobile.MobileActions.bind(owned);
        host = new AutoWoodcuttingPlugin(new AutoWoodcuttingConfig() {}, overlays);
        host.autoWoodcuttingScript.shutdown();
        assertFalse(owned.isActive());
        net.runelite.client.headless.ScriptInputSession newer = new net.runelite.client.headless.ScriptInputSession();
        net.runelite.client.plugins.microbot.mobile.MobileActions.bind(newer);
        try
        {
            host.autoWoodcuttingScript.shutdown();
            assertTrue(newer.isActive());
        }
        finally
        {
            net.runelite.client.plugins.microbot.mobile.MobileActions.unbind(newer);
        }
    }

}
