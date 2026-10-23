package net.runelite.client.plugins.mobilemicrobot;

import net.runelite.api.GameObject;

/** Mobile-safe object query and interaction facade. Client thread only. */
public final class Rs2GameObject
{
	private final MobileScriptContext context;

	Rs2GameObject(MobileScriptContext context)
	{
		this.context = context;
	}

	public GameObject findNearest(String name, int maxTiles)
	{
		return context.nearestGameObject(name, maxTiles);
	}

	/** Returns true for an input request, not for an acknowledged in-game action. */
	public boolean interact(GameObject object)
	{
		return context.click(object);
	}
}
