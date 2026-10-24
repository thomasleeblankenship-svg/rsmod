package net.runelite.client.plugins.mobilemicrobot;

import java.util.List;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** The Hub plugins copied unchanged into the client are hosted as Hub plugins and never start on their own. */
public class HubPortedPluginsTest
{
	static
	{
		System.setProperty("runelite.bridge", "true");
	}

	/** Every ported Hub plugin (see docs/microbot/HUB_PORTS.md). */
	private static final List<String> PORTED = List.of(
		"net.runelite.client.plugins.microbot.autochompykiller.AutoChompyKillerPlugin",
		"net.runelite.client.plugins.microbot.fishingtrawler.FishingTrawlerPlugin",
		"net.runelite.client.plugins.microbot.birdhunter.BirdHunterPlugin",
		"net.runelite.client.plugins.microbot.toweroflife_creaturecreation.TowerOfLifeCCPlugin",
		"net.runelite.client.plugins.microbot.salamanders.SalamanderPlugin",
		"net.runelite.client.plugins.microbot.HueycoatlPrayer.HueyPrayerPlugin",
		"net.runelite.client.plugins.microbot.sandcrabs.SandCrabPlugin",
		"net.runelite.client.plugins.microbot.AmmoniteCrabs.AmmoniteCrabPlugin",
		"net.runelite.client.plugins.microbot.animatedarmour.AnimatedArmourPlugin",
		"net.runelite.client.plugins.microbot.anonymous.AnonymousPlugin",
		"net.runelite.client.plugins.microbot.autogauntletprayer.AutoGauntletPrayerPlugin",
		"net.runelite.client.plugins.microbot.baggedplants.BaggedPlantsPlugin",
		"net.runelite.client.plugins.microbot.blessedwine.BlessedWinePlugin",
		"net.runelite.client.plugins.microbot.butterflycatcher.ButterflyCatcherPlugin",
		"net.runelite.client.plugins.microbot.chillRunecraft.AutoRunecraftPlugin",
		"net.runelite.client.plugins.microbot.colosseumprayer.ColosseumPrayerPlugin",
		"net.runelite.client.plugins.microbot.delveprayerhelper.DelvePrayerHelperPlugin",
		"net.runelite.client.plugins.microbot.gauntlethelper.GauntletHelperPlugin",
		"net.runelite.client.plugins.microbot.goldrush.GabulhasGoldRushPlugin",
		"net.runelite.client.plugins.microbot.hunterKabbits.HunterKebbitsPlugin",
		"net.runelite.client.plugins.microbot.jad.JadPlugin",
		"net.runelite.client.plugins.microbot.lunartablets.LunarTabletsPlugin",
		"net.runelite.client.plugins.microbot.minnowsfishing.MinnowsPlugin",
		"net.runelite.client.plugins.microbot.pumper.PumperPlugin",
		"net.runelite.client.plugins.microbot.theatre.TheatrePlugin",
		"net.runelite.client.plugins.microbot.varrockanvil.VarrockAnvilPlugin",
		"net.runelite.client.plugins.microbot.zeroprayer.AttackTimerMetronomePlugin",
		"net.runelite.client.plugins.microbot.zulrah.ZulrahPlugin",
		"net.runelite.client.plugins.microbot.smelting.AutoSmeltingPlugin",
		"net.runelite.client.plugins.microbot.gefiremaker.GEFiremakerPlugin",
		"net.runelite.client.plugins.microbot.autofishing.AutoFishingPlugin",
		"net.runelite.client.plugins.microbot.barbarianvillagefisher.BarbarianVillageFisherPlugin",
		"net.runelite.client.plugins.microbot.banksbankstander.BanksBankStanderPlugin",
		"net.runelite.client.plugins.microbot.kspwillowchopper.KspWillowChopperPlugin",
		"net.runelite.client.plugins.microbot.birdhouseruns.FornBirdhouseRunsPlugin",
		"net.runelite.client.plugins.microbot.bonestobananas.BonesToBananasPlugin",
		"net.runelite.client.plugins.microbot.eelfishing.EelFishingPlugin",
		"net.runelite.client.plugins.microbot.jewelleryenchant.JewelleryEnchantPlugin",
		"net.runelite.client.plugins.microbot.leaguesfiremaking.LeaguesFiremakingPlugin",
		"net.runelite.client.plugins.microbot.leftclickcast.LeftClickCastPlugin",
		"net.runelite.client.plugins.microbot.piebaker.PieBakerPlugin",
		"net.runelite.client.plugins.microbot.varrockcleaner.VarrockCleanerPlugin",
		"net.runelite.client.plugins.microbot.blastoisefurnace.BlastoiseFurnacePlugin",
		"net.runelite.client.plugins.microbot.arrowmaker.ArrowPlugin",
		"net.runelite.client.plugins.microbot.humidifier.HumidifierPlugin",
		"net.runelite.client.plugins.microbot.lunartanner.TanLeatherPlugin",
		"net.runelite.client.plugins.microbot.summergarden.SummerGardenPlugin",
		"net.runelite.client.plugins.microbot.GiantSeaweedFarmer.GiantSeaweedFarmerPlugin",
		"net.runelite.client.plugins.microbot.pyrefox.PyreFoxPlugin",
		"net.runelite.client.plugins.microbot.sisyphusinfernalpact.SisyphusInfernalPactPlugin",
		"net.runelite.client.plugins.microbot.npctanner.npcTannerPlugin",
		"net.runelite.client.plugins.microbot.karambwans.GabulhasKarambwansPlugin",
		"net.runelite.client.plugins.microbot.TearsOfGuthix.TearsOfGuthixPlugin",
		"net.runelite.client.plugins.microbot.mixology.MixologyPlugin",
		"net.runelite.client.plugins.microbot.gildedaltar.GildedAltarPlugin",
		"net.runelite.client.plugins.microbot.flipperschaser.FlippersChaserPlugin",
		"net.runelite.client.plugins.microbot.stonechests.StoneChestThieverPlugin",
		"net.runelite.client.plugins.microbot.aiocamdozaal.AIOCamdozPlugin",
		"net.runelite.client.plugins.microbot.shadeskiller.ShadesKillerPlugin",
		"net.runelite.client.plugins.microbot.chartercrafter.CharterCrafterPlugin",
		"net.runelite.client.plugins.microbot.hsblackjack.HSBlackJackPlugin",
		"net.runelite.client.plugins.microbot.banksshopper.BanksShopperPlugin",
		"net.runelite.client.plugins.microbot.leaguestoolkit.LeaguesToolkitPlugin",
		"net.runelite.client.plugins.microbot.sulphurnaguafigther.SulphurNaguaPlugin",
		"net.runelite.client.plugins.microbot.lunarplankmake.LunarPlankMakePlugin",
		"net.runelite.client.plugins.microbot.kittentracker.KittenPlugin",
		"net.runelite.client.plugins.microbot.giantsfoundry.GiantsFoundryPlugin",
		"net.runelite.client.plugins.microbot.driftnet.DriftNetPlugin",
		"net.runelite.client.plugins.microbot.dailytasks.DailyTasksPlugin",
		"net.runelite.client.plugins.microbot.EnsouledHeadSlayer.EnsouledHeadSlayerPlugin",
		"net.runelite.client.plugins.microbot.DeadFallTrapHunter.DeadFallTrapHunterPlugin",
		"net.runelite.client.plugins.microbot.GemCrabKiller.GemCrabKillerPlugin",
		"net.runelite.client.plugins.microbot.aiomagic.AIOMagicPlugin",
		"net.runelite.client.plugins.microbot.autoessencemining.AutoEssenceMiningPlugin",
		"net.runelite.client.plugins.microbot.cannonballsmelter.CannonballSmelterPlugin",
		"net.runelite.client.plugins.microbot.eventdismiss.EventDismissPlugin",
		"net.runelite.client.plugins.microbot.housethieving.HouseThievingPlugin",
		"net.runelite.client.plugins.microbot.arceuusrc.ArceuusRcPlugin",
		"net.runelite.client.plugins.microbot.attackrangesplus.AttackRangesPlusPlugin",
		"net.runelite.client.plugins.microbot.moonsofperil.MoonsOfPerilPlugin",
		"net.runelite.client.plugins.microbot.plankrunner.PlankRunnerPlugin",
		"net.runelite.client.plugins.microbot.wintertodt.MWintertodtPlugin");

	private static Plugin instance(String name) throws Exception
	{
		return Class.forName(name).asSubclass(Plugin.class).getDeclaredConstructor().newInstance();
	}

	@Test
	public void portedPluginsAreHubPluginsAndDisabledByDefault() throws Exception
	{
		for (String name : PORTED)
		{
			PluginDescriptor descriptor = Class.forName(name).getAnnotation(PluginDescriptor.class);
			assertNotNull(name, descriptor);
			assertFalse(name, descriptor.enabledByDefault());
			assertTrue(name, HubPluginHost.isHubPlugin(instance(name)));
		}
	}

	@Test
	public void hostNamesDropTheAuthorTag() throws Exception
	{
		for (String name : PORTED)
		{
			String shown = HubPluginHost.name(instance(name));
			assertFalse(name + ": " + shown, shown.isEmpty() || shown.contains("<") || shown.startsWith("["));
		}
	}
}
