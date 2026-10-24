package net.runelite.client.plugins.microbot.util;

import java.awt.Rectangle;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.ParamID;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.StructComposition;
import net.runelite.api.TileObject;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.kit.KitType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntime;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;
import net.runelite.client.plugins.microbot.api.ReadApiFixture;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Hub read batch 13 (util-gap plan) against upstream 2.6.38 semantics, with mocked RuneLite interfaces. */
public class HubReadBatchThirteenTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	private final AtomicBoolean onClientThread = new AtomicBoolean(false);
	private final EventBus events = new EventBus();
	private Client client;

	@Before
	public void setUp() throws Exception
	{
		client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(call -> onClientThread.get());
		// LOGIN_SCREEN keeps the varbit cache from holding values between assertions.
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
		MicrobotRuntime.install(client, thread, events, mock(ConfigManager.class), List.of(new MicrobotReadServices(client, thread, events)));
	}

	@After
	public void tearDown()
	{
		MicrobotRuntime.uninstall();
	}

	private static ItemComposition definition(int id, String name, String... actions)
	{
		ItemComposition definition = mock(ItemComposition.class);
		when(definition.getId()).thenReturn(id);
		when(definition.getName()).thenReturn(name);
		when(definition.getNote()).thenReturn(-1);
		when(definition.getLinkedNoteId()).thenReturn(-1);
		when(definition.getInventoryActions()).thenReturn(actions.length == 0 ? new String[5] : actions);
		return definition;
	}

	private void wear(int weaponId)
	{
		Item[] worn = new Item[14];
		for (int i = 0; i < worn.length; i++)
		{
			worn[i] = new Item(-1, 0);
		}
		worn[3] = new Item(weaponId, 1);
		ItemContainer equipment = mock(ItemContainer.class);
		when(equipment.getItems()).thenReturn(worn);
		when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(equipment);
	}

	private void style(String name)
	{
		when(client.getVarpValue(VarPlayerID.COM_MODE)).thenReturn(1);
		when(client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY)).thenReturn(3);
		EnumComposition categories = mock(EnumComposition.class);
		when(categories.getIntValue(3)).thenReturn(777);
		EnumComposition styles = mock(EnumComposition.class);
		when(styles.getIntVals()).thenReturn(new int[]{10, 11});
		when(client.getEnum(EnumID.WEAPON_STYLES)).thenReturn(categories);
		when(client.getEnum(777)).thenReturn(styles);
		StructComposition struct = mock(StructComposition.class);
		when(struct.getStringValue(ParamID.ATTACK_STYLE_NAME)).thenReturn(name);
		when(client.getStructComposition(11)).thenReturn(struct);
	}

	@Test
	public void attackRangeFollowsTheWeaponTableAndStyle()
	{
		when(client.getItemDefinition(anyInt())).thenAnswer(i -> definition(i.getArgument(0), "Weapon"));
		assertEquals("nothing wielded", 1, Rs2Combat.getAttackRange());
		wear(841);
		style("Rapid");
		assertEquals("Rapid", Rs2Combat.getWeaponAttackStyle());
		assertEquals("shortbow", 7, Rs2Combat.getAttackRange());
		style("Longrange");
		assertEquals("longrange adds two", 9, Rs2Combat.getAttackRange());
		wear(4151);
		assertEquals("unlisted weapon", 1, Rs2Combat.getAttackRange());
		assertEquals(1, Rs2Equipment.all(item -> item.getId() == 4151).count());
	}

	@Test
	public void playerEquipmentAndHealthReads()
	{
		Player player = mock(Player.class);
		PlayerComposition composition = mock(PlayerComposition.class);
		when(composition.getEquipmentId(any(KitType.class))).thenReturn(-1);
		when(composition.getEquipmentId(KitType.WEAPON)).thenReturn(4151);
		when(player.getPlayerComposition()).thenReturn(composition);
		when(player.getHealthRatio()).thenReturn(15);
		when(player.getHealthScale()).thenReturn(30);
		ItemComposition whip = definition(4151, "Abyssal whip");
		when(client.getItemDefinition(4151)).thenReturn(whip);
		Rs2PlayerModel model = mock(Rs2PlayerModel.class);
		when(model.getPlayer()).thenReturn(player);
		assertTrue(Rs2Player.hasPlayerEquippedItem(model, 4151));
		assertTrue(Rs2Player.hasPlayerEquippedItem(model, new int[]{1, 4151}));
		assertTrue(Rs2Player.hasPlayerEquippedItem(model, "abyssal WHIP"));
		assertTrue(Rs2Player.hasPlayerEquippedItem(model, List.of("Dragon scimitar", "Abyssal whip")));
		assertFalse(Rs2Player.hasPlayerEquippedItem(model, 1));
		assertEquals(50, Rs2Player.calculateHealthPercentage(model));
		when(player.getHealthRatio()).thenReturn(-1);
		assertEquals(-1, Rs2Player.calculateHealthPercentage(model));
	}

	@Test
	public void npcMatchesIgnoresCase()
	{
		NPC npc = mock(NPC.class);
		when(npc.getName()).thenAnswer(i -> "Master Farmer");
		Rs2NpcModel model = new Rs2NpcModel(npc);
		assertTrue(Rs2NpcModel.matches(false, "farmer").test(model));
		assertTrue(Rs2NpcModel.matches(true, "master farmer").test(model));
		assertFalse(Rs2NpcModel.matches(true, "farmer").test(model));
	}

	@Test
	public void inventoryAndItemIdReads()
	{
		when(client.getItemDefinition(anyInt())).thenAnswer(i -> definition(i.getArgument(0), "Item"));
		ItemComposition noted = definition(1512, "Logs");
		when(noted.getNote()).thenReturn(799);
		when(noted.getLinkedNoteId()).thenReturn(1511);
		when(client.getItemDefinition(1512)).thenReturn(noted);
		assertEquals(1511, Rs2ItemModel.getUnNotedId(1512));
		assertEquals(995, Rs2ItemModel.getUnNotedId(995));

		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		ItemComposition ashes = definition(592, "Ashes", "Scatter", null, null, null, "Drop");
		when(client.getItemDefinition(592)).thenReturn(ashes);
		Item[] items = {new Item(592, 1), new Item(995, 10), new Item(592, 1)};
		ItemContainer inventory = mock(ItemContainer.class);
		when(inventory.getItems()).thenReturn(items);
		when(client.getItemContainer(net.runelite.api.gameval.InventoryID.INV)).thenReturn(inventory);
		onClientThread.set(true);
		events.post(new ItemContainerChanged(net.runelite.api.gameval.InventoryID.INV, inventory));
		onClientThread.set(false);
		assertEquals(2, Rs2Inventory.getLast(592).getSlot());
		assertEquals(2, Rs2Inventory.getAshes().size());
	}

	@Test
	public void rectangleReads()
	{
		when(client.getViewportWidth()).thenReturn(500);
		when(client.getViewportHeight()).thenReturn(300);
		assertTrue(Rs2UiHelper.isRectangleWithinViewport(new Rectangle(10, 10, 5, 5)));
		assertFalse(Rs2UiHelper.isRectangleWithinViewport(new Rectangle(510, 10, 5, 5)));
		assertTrue(Rs2UiHelper.isRectangleWithinRectangle(new Rectangle(0, 0, 10, 10), new Rectangle(2, 2, 3, 3)));
		assertFalse(Rs2UiHelper.isRectangleWithinRectangle(new Rectangle(0, 0, 10, 10), new Rectangle(8, 8, 5, 5)));
		TileObject object = mock(TileObject.class);
		when(object.getClickbox()).thenReturn(new Rectangle(4, 5, 6, 7));
		assertEquals(new Rectangle(4, 5, 6, 7), Rs2UiHelper.getObjectClickbox(object));
		when(client.getCanvasWidth()).thenReturn(800);
		when(client.getCanvasHeight()).thenReturn(600);
		assertEquals(Rs2UiHelper.getDefaultRectangle(), Rs2UiHelper.getObjectClickbox(null));
	}
}
