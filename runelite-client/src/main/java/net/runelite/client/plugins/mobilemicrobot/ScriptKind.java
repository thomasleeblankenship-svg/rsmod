package net.runelite.client.plugins.mobilemicrobot;

import java.util.Set;

/**
 * The on-device scripts the Microbot Mobile panel can run, each with the settings sections
 * ({@link MobileMicrobotConfig} section keys) that belong to it. Items outside any section are
 * shared controls (script, run, webwalker) and always shown.
 */
public enum ScriptKind
{
	DIAGNOSTICS("Diagnostics (read-only)"),
	SCENE_SCAN("Scene scan (read-only)"),
	STATE_MACHINE_EXAMPLE("Upstream state machine example (read-only)"),
	WOODCUTTING("Woodcutting (example)", MobileMicrobotConfig.EXAMPLE_WOODCUTTING_SECTION),
	AUTO_WOODCUTTING("Auto Woodcutting (mobile port)", "general", "inventory", "forestry"),
	// Free-to-play Hub scripts, run through HubPluginHost with their own plugin settings.
	F2P_RUNECRAFTING("F2P: Auto Runecrafter", hub("chillRunecraft.AutoRunecraftPlugin", "autoRunecraft")),
	F2P_FIREMAKING("F2P: Auto Firemaking Plus", hub("firemakingplus.AutoFiremakingPlusPlugin", "FiremakingPlus")),
	F2P_COOKING("F2P: Auto Cooking", hub("cooking.AutoCookingPlugin", "autocooking")),
	F2P_SMELTING("F2P: Auto Smelting", hub("smelting.AutoSmeltingPlugin", "Smithing")),
	F2P_VARROCK_ANVIL("F2P: Varrock Anvil", hub("varrockanvil.VarrockAnvilPlugin", "VarrockAnvil")),
	F2P_ESSENCE_MINING("F2P: Essence Mining", hub("autoessencemining.AutoEssenceMiningPlugin", "EssenceMining")),
	F2P_BARBARIAN_FISHING("F2P: Barbarian Village Fisher", hub("barbarianvillagefisher.BarbarianVillageFisherPlugin", "BarbarianVillageFisher")),
	F2P_GE_COOKER("F2P: GE Cooker", hub("gecooker.GECookerPlugin", "GECooker")),
	F2P_GE_FIREMAKER("F2P: GE Firemaker", hub("gefiremaker.GEFiremakerPlugin", "GEFiremaker")),
	F2P_MUSEUM_CLEANER("F2P: Varrock Museum Cleaner", hub("varrockcleaner.VarrockCleanerPlugin", "varrockmuseum")),
	F2P_FISHING("F2P: Auto Fishing", hub("autofishing.AutoFishingPlugin", "AutoFishing")),
	F2P_BANK_SELLER("F2P: Bank Seller (GE)", hub("bankseller.BankSellerPlugin", "bankseller")),
	F2P_DA_BRUTUS("F2P: Da Brutus (Brutus boss)", hub("dabrutus.DaBrutusPlugin", "dabrutus")),
	// Members skilling Hub scripts (non-combat), ported for mobile like the free-to-play batch.
	P2P_FLETCHING("Members: Fletcher", hub("fletching.FletchingPlugin", "Fletching")),
	P2P_STALL_THIEVING("Members: Stall Thieving", hub("thievingstalls.StallThievingPlugin", "stallthieving")),
	P2P_MOTHERLODE_MINE("Members: Motherlode Mine", hub("motherloadmine.MotherloadMinePlugin", "micro-motherloadmine")),
	P2P_KARAMBWANS("Members: Karambwan Fisher", hub("karambwans.GabulhasKarambwansPlugin", "GabulhasKarambwans")),
	P2P_GLASS_MAKE("Members: Glass Make", hub("glassmake.GabulhasGlassMakePlugin", "GabulhasGlassMake")),
	P2P_CANNONBALLS("Members: Cannonball Smelter", hub("cannonballsmelter.CannonballSmelterPlugin", "CannonballSmelter")),
	P2P_AMETHYST("Members: Amethyst Miner", hub("amethystminer.AmethystMiningPlugin", "AmethystMining")),
	P2P_SANDSTONE("Members: Sandstone Miner", hub("sandminer.GabulhasSandMinerPlugin", "GabulhasSandMiner")),
	P2P_PIE_BAKER("Members: Pie Baker", hub("piebaker.PieBakerPlugin", "piebaker")),
	P2P_LUNAR_TANNER("Members: Lunar Tanner", hub("lunartanner.TanLeatherPlugin", "tanLeather")),
	P2P_JEWELLERY_ENCHANT("Members: Jewellery Enchant", hub("jewelleryenchant.JewelleryEnchantPlugin", "jewelleryenchant")),
	P2P_ARROW_MAKER("Members: Arrow Maker", hub("arrowmaker.ArrowPlugin", "ArrowMaking")),
	TUTORIAL_ISLAND("Tutorial Island", hub("tutorialisland.TutorialIslandPlugin", "MicroTutIsland"));

	private static final String HUB_PACKAGE = "net.runelite.client.plugins.microbot.";

	private final String display;
	private final Set<String> settingsSections;
	private final String hubPluginClass;
	private final String configGroup;

	ScriptKind(String display, String... settingsSections)
	{
		this.display = display;
		this.settingsSections = Set.of(settingsSections);
		this.hubPluginClass = null;
		this.configGroup = null;
	}

	ScriptKind(String display, HubPlugin hub)
	{
		this.display = display;
		this.settingsSections = Set.of();
		this.hubPluginClass = HUB_PACKAGE + hub.className;
		this.configGroup = hub.configGroup;
	}

	private static HubPlugin hub(String className, String configGroup)
	{
		return new HubPlugin(className, configGroup);
	}

	/** A Hub plugin a script kind runs: its class (relative to the Microbot package) and config group. */
	private static final class HubPlugin
	{
		private final String className;
		private final String configGroup;

		private HubPlugin(String className, String configGroup)
		{
			this.className = className;
			this.configGroup = configGroup;
		}
	}

	/** Whether this script is a Hub plugin started through {@link HubPluginHost}. */
	public boolean isHub()
	{
		return hubPluginClass != null;
	}

	/** The Hub plugin's class name, or {@code null} for a built-in script. */
	public String getHubPluginClass()
	{
		return hubPluginClass;
	}

	/** The Hub plugin's own config group (its settings), or {@code null} for a built-in script. */
	public String getConfigGroup()
	{
		return configGroup;
	}

	/** The settings sections this script uses. */
	public Set<String> getSettingsSections()
	{
		return settingsSections;
	}

	/**
	 * Whether settings in {@code section} should be shown while the script stored as
	 * {@code selectedName} (an enum name, possibly missing or stale) is selected. Shared items (no
	 * section) always show; an unknown selection falls back to the config's default script.
	 */
	public static boolean isSectionShown(String selectedName, String section)
	{
		if (section == null || section.isEmpty())
		{
			return true;
		}
		ScriptKind selected = DIAGNOSTICS;
		for (ScriptKind kind : values())
		{
			if (kind.name().equals(selectedName))
			{
				selected = kind;
			}
		}
		return selected.settingsSections.contains(section);
	}

	@Override
	public String toString()
	{
		return display;
	}
}
