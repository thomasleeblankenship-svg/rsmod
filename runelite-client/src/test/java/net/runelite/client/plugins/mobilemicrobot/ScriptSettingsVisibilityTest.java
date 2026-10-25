package net.runelite.client.plugins.mobilemicrobot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The Microbot Mobile panel shows only the selected script's settings sections. */
public class ScriptSettingsVisibilityTest
{
	private static Set<String> declaredSections() throws IllegalAccessException
	{
		Set<String> sections = new HashSet<>();
		for (Field field : MobileMicrobotConfig.class.getDeclaredFields())
		{
			if (field.isAnnotationPresent(ConfigSection.class))
			{
				sections.add((String) field.get(null));
			}
		}
		return sections;
	}

	@Test
	public void everySectionBelongsToExactlyOneScript() throws IllegalAccessException
	{
		Map<String, ScriptKind> owner = new HashMap<>();
		for (ScriptKind kind : ScriptKind.values())
		{
			for (String section : kind.getSettingsSections())
			{
				assertFalse(section + " claimed twice", owner.containsKey(section));
				owner.put(section, kind);
			}
		}
		assertEquals("each declared section has a script, and each script's sections exist",
			declaredSections(), owner.keySet());
	}

	@Test
	public void exampleWoodcuttingItemsSitInTheirOwnSection() throws NoSuchMethodException
	{
		for (String name : new String[]{"treeType", "maxDistance"})
		{
			Method method = MobileMicrobotConfig.class.getMethod(name);
			assertEquals(name, MobileMicrobotConfig.EXAMPLE_WOODCUTTING_SECTION, method.getAnnotation(ConfigItem.class).section());
		}
		for (String shared : new String[]{"selectedScript", "runScript", "usePathfinder"})
		{
			assertEquals(shared + " stays shared", "", MobileMicrobotConfig.class.getMethod(shared).getAnnotation(ConfigItem.class).section());
		}
	}

	@Test
	public void onlyTheSelectedScriptsSectionsShow()
	{
		assertTrue("shared controls always show", ScriptKind.isSectionShown("DIAGNOSTICS", ""));
		assertTrue(ScriptKind.isSectionShown("AUTO_WOODCUTTING", "general"));
		assertTrue(ScriptKind.isSectionShown("AUTO_WOODCUTTING", "forestry"));
		assertFalse("the reported bug: auto woodcutting settings under another script",
			ScriptKind.isSectionShown("DIAGNOSTICS", "general"));
		assertFalse(ScriptKind.isSectionShown("WOODCUTTING", "inventory"));
		assertTrue(ScriptKind.isSectionShown("WOODCUTTING", MobileMicrobotConfig.EXAMPLE_WOODCUTTING_SECTION));
		assertFalse(ScriptKind.isSectionShown("AUTO_WOODCUTTING", MobileMicrobotConfig.EXAMPLE_WOODCUTTING_SECTION));
		assertFalse("missing or stale selection uses the default script", ScriptKind.isSectionShown(null, "general"));
		assertFalse(ScriptKind.isSectionShown("REMOVED_SCRIPT", "general"));
	}

	@Test
	public void panelJsonCarriesStatusAndScriptNames()
	{
		com.google.gson.JsonObject panel = new com.google.gson.JsonParser().parse(MobileScriptBridge.panelJson()).getAsJsonObject();
		assertTrue(panel.has("running"));
		assertTrue(panel.has("status"));
		com.google.gson.JsonArray scripts = panel.getAsJsonArray("scripts");
		assertEquals(ScriptKind.values().length, scripts.size());
		com.google.gson.JsonObject auto = scripts.get(ScriptKind.AUTO_WOODCUTTING.ordinal()).getAsJsonObject();
		assertEquals("AUTO_WOODCUTTING", auto.get("id").getAsString());
		assertEquals("Auto Woodcutting (mobile port)", auto.get("name").getAsString());
	}

	@Test
	public void hubScriptsPointAtRealPluginsAndTheirConfigGroups() throws Exception
	{
		int hubs = 0;
		for (ScriptKind kind : ScriptKind.values())
		{
			if (!kind.isHub())
			{
				assertEquals(kind + " has no plugin group", null, kind.getConfigGroup());
				continue;
			}
			hubs++;
			Class<?> plugin = Class.forName(kind.getHubPluginClass());
			assertTrue(kind + " is a plugin", net.runelite.client.plugins.Plugin.class.isAssignableFrom(plugin));
			Set<String> groups = new HashSet<>();
			java.nio.file.Path dir = java.nio.file.Paths.get(plugin.getProtectionDomain().getCodeSource().getLocation().toURI())
				.resolve(plugin.getPackage().getName().replace('.', '/'));
			try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(dir))
			{
				for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) files::iterator)
				{
					String name = file.getFileName().toString();
					if (name.endsWith(".class") && !name.contains("$"))
					{
						Class<?> type = Class.forName(plugin.getPackage().getName() + "." + name.substring(0, name.length() - 6));
						net.runelite.client.config.ConfigGroup group = type.getAnnotation(net.runelite.client.config.ConfigGroup.class);
						if (group != null)
						{
							groups.add(group.value());
						}
					}
				}
			}
			assertTrue(kind + " settings group " + kind.getConfigGroup() + " in " + groups, groups.contains(kind.getConfigGroup()));
			assertTrue(kind + " starts at no Microbot section", kind.getSettingsSections().isEmpty());
		}
		assertEquals("the 12 F2P scripts, 12 members scripts and Tutorial Island", 25, hubs);
	}
}
