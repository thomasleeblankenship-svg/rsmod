package net.runelite.client.plugins.mobilemicrobot;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingConfig;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingPrimaryAction;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingSecondaryAction;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingTree;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingWalkBack;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.skills.fletching.data.FletchingItem;

@ConfigGroup("mobilemicrobot")
public interface MobileMicrobotConfig extends AutoWoodcuttingConfig
{
	@ConfigItem(keyName = "selectedScript", name = "Script", description = "Which on-device script to run", position = 0)
	default ScriptKind selectedScript()
	{
		return ScriptKind.DIAGNOSTICS;
	}

	@ConfigItem(keyName = "runScript", name = "Run script", description = "Start or stop the selected script", position = 1)
	default boolean runScript()
	{
		return false;
	}

	@ConfigItem(keyName = "usePathfinder", name = "Webwalker: whole-world pathfinder",
		description = "Plan walks with the whole-world pathfinder (stairs, doors, ladders and agility shortcuts). "
			+ "Off uses the older same-floor planner only.", position = 4)
	default boolean usePathfinder()
	{
		return true;
	}

	/** Section key of the example woodcutting script's settings (see {@link ScriptKind}). */
	String EXAMPLE_WOODCUTTING_SECTION = "exampleWoodcutting";

	@ConfigSection(
			name = "Woodcutting (example)",
			description = "Settings for the example woodcutting script",
			position = -1
	)
	String exampleWoodcuttingSection = EXAMPLE_WOODCUTTING_SECTION;

	@ConfigItem(keyName = "treeType", name = "Example woodcutting tree", description = "Tree type for the example woodcutting script", position = 2,
		section = EXAMPLE_WOODCUTTING_SECTION)
	default TreeType treeType()
	{
		return TreeType.NORMAL;
	}

	@ConfigItem(keyName = "maxDistance", name = "Example tree distance", description = "Maximum number of tiles to search from the player", position = 3,
		section = EXAMPLE_WOODCUTTING_SECTION)
	default int maxDistance()
	{
		return 8;
	}
	// ConfigManager seeds defaults and discovers sections from declared members only.
	// Forward defaults to the uploaded config while declaring its UI metadata here.
	@ConfigSection(
			name = "General",
			description = "General",
			position = 0
	)
	String generalSection = "general";
	@ConfigSection(
			name = "Inventory management",
			description = "Configure how to handle full inventory",
			position = 1
	)
	String inventorySection = "inventory";

	@ConfigSection(
			name = "Forestry",
			description = "Forestry events",
			position = 2,
			closedByDefault = true
	)
	String forestrySection = "forestry";

	@ConfigItem(
			keyName = "enableWoodcutting",
			name = "Enable auto woodcutting",
			description = "Turn off to keep forestry helpers active without cutting trees automatically",
			position = 0,
			section = generalSection
	)
	@Override
	default boolean enableWoodcutting()
	{
		return AutoWoodcuttingConfig.super.enableWoodcutting();
	}

	@ConfigItem(
			keyName = "progressiveMode",
			name = "Progressive mode",
			description = "Automatically switch to the best tree based on your Woodcutting level",
			position = 1,
			section = generalSection
	)
	@Override
	default boolean progressiveMode()
	{
		return AutoWoodcuttingConfig.super.progressiveMode();
	}

	@ConfigItem(
			keyName = "Tree",
			name = "Tree",
			description = "Choose the tree (ignored when Progressive mode is enabled)",
			position = 2,
			section = generalSection
	)
	@Override
	default WoodcuttingTree TREE()
	{
		return AutoWoodcuttingConfig.super.TREE();
	}

	@ConfigItem(
			keyName = "DistanceToStray",
			name = "Distance to Stray",
			description = "Set how far you can travel from your initial position in tiles",
			position = 3,
			section = generalSection
	)
	@Override
	default int distanceToStray()
	{
		return AutoWoodcuttingConfig.super.distanceToStray();
	}

	@ConfigItem(
			keyName = "Firemake",
			name = "Firemake only",
			description = "Turns into an Auto Firemaker only mode , start plugin initially at desired firemaking starting position , tested only at GE - North East ",
			position = 5,
			section = generalSection
	)
	@Override
	default boolean firemakeOnly()
	{
		return AutoWoodcuttingConfig.super.firemakeOnly();
	}

	@ConfigItem(
			keyName = "HardwoodTreePatch",
			name = "Woodcut at Hardwood Tree Patch",
			description = "Woodcut at Hardwood Tree Patch",
			position = 6,
			section = generalSection
	)
	@Override
	default boolean HardwoodTreePatch()
	{
		return AutoWoodcuttingConfig.super.HardwoodTreePatch();
	}

	@ConfigItem(
			keyName = "LootNests",
			name = "Loot Bird Nests",
			description = "Loot bird nests from trees and events",
			position = 7,
			section = generalSection
	)
	@Override
	default boolean lootBirdNests()
	{
		return AutoWoodcuttingConfig.super.lootBirdNests();
	}

	@ConfigItem(
			keyName = "LootSeeds",
			name = "Loot Seeds",
			description = "Loot seeds from events",
			position = 8,
			section = generalSection
	)
	@Override
	default boolean lootSeeds()
	{
		return AutoWoodcuttingConfig.super.lootSeeds();
	}

	@ConfigItem(
			keyName = "LootMyItemsOnly",
			name = "Loot my items only",
			description = "Only loot your items (Ironman)",
			position = 9,
			section = generalSection
	)
	@Override
	default boolean lootMyItemsOnly()
	{
		return AutoWoodcuttingConfig.super.lootMyItemsOnly();
	}

	@ConfigItem(
			keyName = "PrimaryAction",
			name = "Primary action",
			description = "What to do when inventory is full",
			position = 0,
			section = inventorySection
	)
	@Override
	default WoodcuttingPrimaryAction primaryAction()
	{
		return AutoWoodcuttingConfig.super.primaryAction();
	}

	@ConfigItem(
			keyName = "FletchingType",
			name = "Fletching type",
			description = "Type of item to fletch (only applies if primary action is FLETCH)",
			position = 1,
			section = inventorySection
	)
	@Override
	default FletchingItem fletchingType()
	{
		return AutoWoodcuttingConfig.super.fletchingType();
	}

	@ConfigItem(
			keyName = "SecondaryAction",
			name = "Secondary action",
			description = "What to do with fletched items (only applies after fletching)",
			position = 2,
			section = inventorySection
	)
	@Override
	default WoodcuttingSecondaryAction secondaryAction()
	{
		return AutoWoodcuttingConfig.super.secondaryAction();
	}

	@ConfigItem(
			keyName = "dropOrder",
			name = "Drop order",
			description = "Order to drop items",
			position = 4,
			section = inventorySection
	)
	@Override
	default InteractOrder interactOrder()
	{
		return AutoWoodcuttingConfig.super.interactOrder();
	}

	@ConfigItem(
			keyName = "ItemsToBank",
			name = "Additional items to bank",
			description = "Extra items to bank (comma separated)",
			position = 5,
			section = inventorySection
	)
	@Override
	default String itemsToBank()
	{
		return AutoWoodcuttingConfig.super.itemsToBank();
	}

	@ConfigItem(
			keyName = "ItemsToKeep",
			name = "Items to keep when dropping",
			description = "Items to keep in inventory (comma separated)",
			position = 6,
			section = inventorySection
	)
	@Override
	default String itemsToKeep()
	{
		return AutoWoodcuttingConfig.super.itemsToKeep();
	}

	@ConfigItem(
			keyName = "WalkBack",
			name = "Walk back",
			description = "Walk back to initial spot or last cut down",
			position = 5,
			section = inventorySection
	)
	@Override
	default WoodcuttingWalkBack walkBack()
	{
		return AutoWoodcuttingConfig.super.walkBack();
	}

	@ConfigItem(
			keyName = "StringBows",
			name = "String bows",
			description = "String unstrung bows if bowstring is available",
			position = 8,
			section = inventorySection
	)
	@Override
	default boolean stringBows()
	{
		return AutoWoodcuttingConfig.super.stringBows();
	}

	@ConfigItem(
			keyName = "enableForestry",
			name = "Enable forestry",
			description = "Enable forestry features",
			position = 0,
			section = forestrySection
	)
	@Override
	default boolean enableForestry()
	{
		return AutoWoodcuttingConfig.super.enableForestry();
	}

	@ConfigItem(
			keyName = "eggEvent",
			name = "Enable Egg Event",
			description = "Enable the Egg forestry event",
			position = 1,
			section = forestrySection
	)
	@Override
	default boolean eggEvent()
	{
		return AutoWoodcuttingConfig.super.eggEvent();
	}

	@ConfigItem(
			keyName = "entlingsEvent",
			name = "Enable Entlings Event",
			description = "Enable the Entlings forestry event",
			position = 2,
			section = forestrySection
	)
	@Override
	default boolean entlingsEvent()
	{
		return AutoWoodcuttingConfig.super.entlingsEvent();
	}

	@ConfigItem(
			keyName = "flowersEvent",
			name = "Enable Flowers Event",
			description = "Enable the Flowers forestry event",
			position = 3,
			section = forestrySection,
			hidden = false //TODO: Remove this when the event is implemented
	)
	@Override
	default boolean flowersEvent()
	{
		return AutoWoodcuttingConfig.super.flowersEvent();
	}

	@ConfigItem(
			keyName = "foxEvent",
			name = "Enable Fox Event",
			description = "Enable the Fox forestry event",
			position = 4,
			section = forestrySection
	)
	@Override
	default boolean foxEvent()
	{
		return AutoWoodcuttingConfig.super.foxEvent();
	}

	@ConfigItem(
			keyName = "hivesEvent",
			name = "Enable Hives Event",
			description = "Enable the Hives forestry event",
			position = 5,
			section = forestrySection
	)
	@Override
	default boolean hivesEvent()
	{
		return AutoWoodcuttingConfig.super.hivesEvent();
	}

	@ConfigItem(
			keyName = "leprechaunEvent",
			name = "Enable Leprechaun Event",
			description = "Enable the Leprechaun forestry event",
			position = 6,
			section = forestrySection
	)
	@Override
	default boolean leprechaunEvent()
	{
		return AutoWoodcuttingConfig.super.leprechaunEvent();
	}

	@ConfigItem(
			keyName = "ritualEvent",
			name = "Enable Ritual Event",
			description = "Enable the Ritual forestry event",
			position = 7,
			section = forestrySection
	)
	@Override
	default boolean ritualEvent()
	{
		return AutoWoodcuttingConfig.super.ritualEvent();
	}

	@ConfigItem(
			keyName = "rootEvent",
			name = "Enable Root Event",
			description = "Enable the Root forestry event",
			position = 8,
			section = forestrySection
	)
	@Override
	default boolean rootEvent()
	{
		return AutoWoodcuttingConfig.super.rootEvent();
	}

	@ConfigItem(
			keyName = "saplingEvent",
			name = "Enable Struggling Sapling Event",
			description = "Enable the Struggling Sapling forestry event",
			position = 9,
			section = forestrySection
	)
	@Override
	default boolean saplingEvent()
	{
		return AutoWoodcuttingConfig.super.saplingEvent();
	}

}
