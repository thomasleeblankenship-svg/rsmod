package net.runelite.client.plugins.microbot.util.prayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Round 2 item (2): prayer reads over varbits and levels, through the real read services. */
public class Rs2PrayerReadTest
{
	static
	{
		// JavaBridge captures this flag on first load; set it before any test reaches the bridge.
		System.setProperty("runelite.bridge", "true");
	}

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private Client client;
	private int level = 99;
	private int points = 99;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		// LOGIN_SCREEN keeps the varbit cache from holding values between assertions.
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(client.getVarbitValue(anyInt())).thenAnswer(i -> varbits.getOrDefault((int) i.getArgument(0), 0));
		when(client.getRealSkillLevel(Skill.PRAYER)).thenAnswer(i -> level);
		when(client.getBoostedSkillLevel(Skill.PRAYER)).thenAnswer(i -> points);
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
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	@Test
	public void enumCarriesTheBookButtonLevelAndVarbit()
	{
		assertEquals(InterfaceID.Prayerbook.PRAYER19, Rs2PrayerEnum.SHARP_EYE.getIndex());
		assertEquals(Rs2PrayerEnum.EAGLE_EYE.getIndex(), Rs2PrayerEnum.DEAD_EYE.getIndex());
		assertEquals(43, Rs2PrayerEnum.PROTECT_MELEE.getLevel());
		assertEquals(VarbitID.PRAYER_PIETY, Rs2PrayerEnum.PIETY.getVarbit());
		assertEquals("Protect from Missiles", Rs2PrayerEnum.PROTECT_RANGE.getName());
	}

	@Test
	public void activePrayersAndProtection()
	{
		assertNull(Rs2Prayer.getActiveProtectionPrayer());
		assertTrue(Rs2Prayer.getActivePrayers().isEmpty());

		varbits.put(VarbitID.PRAYER_PROTECTFROMMISSILES, 1);
		varbits.put(VarbitID.PRAYER_RIGOUR, 1);
		varbits.put(VarbitID.QUICKPRAYER_ACTIVE, 1);

		assertTrue(Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE));
		assertFalse(Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE));
		assertFalse(Rs2Prayer.isPrayerActive(null));
		assertEquals(Rs2PrayerEnum.PROTECT_RANGE, Rs2Prayer.getActiveProtectionPrayer());
		assertEquals(List.of(Rs2PrayerEnum.PROTECT_RANGE, Rs2PrayerEnum.RIGOUR), Rs2Prayer.getActivePrayers());
		assertTrue(Rs2Prayer.isQuickPrayerEnabled());
	}

	@Test
	public void pointsComeFromTheBoostedLevel()
	{
		points = 42;
		assertEquals(42, Rs2Prayer.getPrayerPoints());
		assertFalse(Rs2Prayer.isOutOfPrayer());
		points = 0;
		assertTrue(Rs2Prayer.isOutOfPrayer());
	}

	@Test
	public void bestPrayersFollowLevelAndUnlocks()
	{
		level = 99;
		assertEquals(Rs2PrayerEnum.PIETY, Rs2Prayer.getBestMeleePrayer());
		assertEquals("Rigour not unlocked", Rs2PrayerEnum.EAGLE_EYE, Rs2Prayer.getBestRangePrayer());
		assertEquals(Rs2PrayerEnum.MYSTIC_MIGHT, Rs2Prayer.getBestMagePrayer());

		varbits.put(VarbitID.PRAYER_RIGOUR_UNLOCKED, 1);
		varbits.put(VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED, 1);
		assertTrue(Rs2Prayer.isRigourUnlocked());
		assertEquals(Rs2PrayerEnum.RIGOUR, Rs2Prayer.getBestRangePrayer());
		assertEquals(Rs2PrayerEnum.MYSTIC_VIGOUR, Rs2Prayer.getBestMagePrayer());

		level = 30;
		assertEquals(Rs2PrayerEnum.SUPERHUMAN_STRENGTH, Rs2Prayer.getBestMeleePrayer());
		assertEquals(Rs2PrayerEnum.HAWK_EYE, Rs2Prayer.getBestRangePrayer());
		assertEquals(Rs2PrayerEnum.MYSTIC_LORE, Rs2Prayer.getBestMagePrayer());

		level = 3;
		assertNull(Rs2Prayer.getBestMeleePrayer());
	}
}
