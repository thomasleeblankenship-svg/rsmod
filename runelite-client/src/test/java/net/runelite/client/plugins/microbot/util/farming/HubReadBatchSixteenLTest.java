package net.runelite.client.plugins.microbot.util.farming;

import com.google.inject.Injector;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.CropState;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingPatch;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingWorld;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Read batch 16l: upstream 2.6.38 farming reads over the farm-run model and the injector. */
public class HubReadBatchSixteenLTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private final EventBus events = new EventBus();
	private final ConfigManager config = mock(ConfigManager.class);
	private Client client;
	private Injector previousInjector;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		ClientThread thread = ReadApiFixture.clientThreadFor(client);
		doAnswer(call ->
		{
			onClientThread.set(true);
			try
			{
				((Runnable) call.getArgument(0)).run();
			}
			finally
			{
				onClientThread.set(false);
			}
			return null;
		}).when(thread).invoke(any(Runnable.class));
		MicrobotRuntime.install(client, thread, events, config, List.of(new MicrobotReadServices(client, thread, events)));
		java.lang.reflect.Constructor<FarmingWorld> constructor = FarmingWorld.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		FarmingWorld world = constructor.newInstance();
		Injector injector = mock(Injector.class);
		when(injector.getInstance(FarmingWorld.class)).thenReturn(world);
		previousInjector = RuneLite.getInjector();
		RuneLite.setInjector(injector);
	}

	@After
	public void tearDown()
	{
		RuneLite.setInjector(previousInjector);
		MicrobotRuntime.uninstall();
	}

	@Test
	public void patchTablesPredictionAndDistance()
	{
		List<FarmingPatch> herbs = Rs2Farming.getHerbPatches();
		assertFalse(herbs.isEmpty());
		assertFalse(Rs2Farming.getTreePatches().isEmpty());

		when(config.getRSProfileKey()).thenReturn("profile");
		when(config.getConfiguration(eq("timetracking"), eq("profile"), anyString())).thenReturn("9:" + Instant.now().getEpochSecond());
		FarmingPatch first = herbs.get(0);
		assertEquals("guam ready", CropState.HARVESTABLE, Rs2Farming.predictPatchState(first));
		assertEquals(herbs.size(), Rs2Farming.getHarvestablePatches(herbs).size());

		Player player = mock(Player.class);
		when(player.getWorldLocation()).thenReturn(new WorldPoint(2813, 3463, 0));
		when(client.getLocalPlayer()).thenReturn(player);
		assertEquals("the Catherby herb patch is closest", 2815, Rs2Farming.getClosestPatch(herbs).get().getLocation().getX());
		assertEquals(1, Rs2Farming.getPatchesWithinDistance(herbs, new WorldPoint(2813, 3463, 0), 10).size());

		when(client.getRealSkillLevel(Skill.FARMING)).thenReturn(30);
		assertTrue(Rs2Farming.hasRequiredFarmingLevel(30));
		assertFalse(Rs2Farming.hasRequiredFarmingLevel(31));
		assertTrue(SpiritTree.findByLocation(SpiritTree.values()[0].getLocation()).isPresent());
	}
}
