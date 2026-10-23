package net.runelite.client.plugins.microbot.util.npc;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The legacy live NPC wrapper delegates every call on the client thread. */
public class LegacyNpcModelTest
{
	static
	{
		// JavaBridge captures this flag on first load; set it before any test reaches the bridge.
		System.setProperty("runelite.bridge", "true");
	}

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private NPC npc;

	@Before
	public void setUp() throws Exception
	{
		Client client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			onClientThread.set(true);
			try { ((Runnable) call.getArgument(0)).run(); }
			finally { onClientThread.set(false); }
			return null;
		}).when(thread).invoke(any(Runnable.class));
		EventBus events = new EventBus();
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class),
			List.of(new MicrobotReadServices(client, thread, events)));
		npc = mock(NPC.class);
		when(npc.getId()).thenAnswer(i -> live(8000));
		when(npc.getName()).thenAnswer(i -> live("Ritual circle"));
		when(npc.getWorldLocation()).thenAnswer(i -> live(new WorldPoint(3200, 3200, 0)));
		when(npc.isDead()).thenAnswer(i -> live(true));
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	private <T> T live(T value)
	{
		if (!onClientThread.get())
		{
			throw new AssertionError("live NPC read off the client thread");
		}
		return value;
	}

	@Test
	public void gettersDelegateOnTheClientThread()
	{
		Rs2NpcModel model = new Rs2NpcModel(npc);

		assertEquals(8000, model.getId());
		assertEquals("Ritual circle", model.getName());
		assertEquals(new WorldPoint(3200, 3200, 0), model.getWorldLocation());
		assertTrue(model.isDead());
		assertSame(npc, model.getNpc());
		assertSame(npc, model.getActor());
	}

	@Test
	public void settersRunOnTheClientThread()
	{
		doAnswer(i -> live(null)).when(npc).setAnimation(808);

		new Rs2NpcModel(npc).setAnimation(808);

		verify(npc).setAnimation(808);
	}

	@Test
	public void withoutTheRuntimeGettersReturnDefaults()
	{
		MicrobotRuntime.uninstall();
		Rs2NpcModel model = new Rs2NpcModel(npc);

		assertEquals(0, model.getId());
		assertNull(model.getName());
		assertFalse(model.isDead());
	}
}
