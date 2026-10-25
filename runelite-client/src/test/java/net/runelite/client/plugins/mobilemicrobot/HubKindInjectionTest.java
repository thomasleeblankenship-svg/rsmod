package net.runelite.client.plugins.mobilemicrobot;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * Every Hub script on the Microbot screen must be loadable by Guice. The microbot {@code api} read classes (the
 * caches) have no injectable constructor, so a plugin that injects one, directly or through its script, fails
 * to load and the picker reports it "Not available" (Motherlode Mine did, before its mobile port).
 */
public class HubKindInjectionTest
{
	private static final String API_PACKAGE = "net.runelite.client.plugins.microbot.api.";

	private static boolean injected(java.lang.reflect.AnnotatedElement element)
	{
		return element.isAnnotationPresent(javax.inject.Inject.class) || element.isAnnotationPresent(com.google.inject.Inject.class);
	}

	private static boolean guiceCanBuild(Class<?> type)
	{
		if (type.isInterface() || Modifier.isAbstract(type.getModifiers()))
		{
			return true;
		}
		for (Constructor<?> constructor : type.getDeclaredConstructors())
		{
			if (injected(constructor) || (constructor.getParameterCount() == 0 && !Modifier.isPrivate(constructor.getModifiers())))
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void noHubScriptInjectsAReadCacheGuiceCannotBuild() throws Exception
	{
		List<String> problems = new ArrayList<>();
		for (ScriptKind kind : ScriptKind.values())
		{
			if (!kind.isHub())
			{
				continue;
			}
			Class<?> plugin = Class.forName(kind.getHubPluginClass());
			String pluginPackage = plugin.getPackage().getName();
			Set<Class<?>> seen = new HashSet<>();
			ArrayDeque<Class<?>> queue = new ArrayDeque<>(List.of(plugin));
			while (!queue.isEmpty())
			{
				Class<?> current = queue.removeFirst();
				if (!seen.add(current))
				{
					continue;
				}
				List<Class<?>> needs = new ArrayList<>();
				for (Class<?> c = current; c != null && c != Object.class; c = c.getSuperclass())
				{
					for (Field field : c.getDeclaredFields())
					{
						if (injected(field))
						{
							needs.add(field.getType());
						}
					}
				}
				for (Constructor<?> constructor : current.getDeclaredConstructors())
				{
					if (injected(constructor))
					{
						needs.addAll(List.of(constructor.getParameterTypes()));
					}
				}
				for (Class<?> need : needs)
				{
					if (need.getName().startsWith(API_PACKAGE) && !guiceCanBuild(need))
					{
						problems.add(kind + ": " + current.getSimpleName() + " injects " + need.getSimpleName());
					}
					if (need.getPackage() != null && need.getPackage().getName().startsWith(pluginPackage))
					{
						queue.add(need);
					}
				}
			}
		}
		assertTrue(String.join("; ", problems), problems.isEmpty());
	}
}
