package net.runelite.client.plugins.microbot.util.skills.fletching.data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Log materials for fletching, by item-name prefix (as upstream). */
@Getter
@RequiredArgsConstructor
public enum FletchingMaterial
{
	LOG(""),
	WOOD("Wood"),
	OAK("Oak"),
	WILLOW("Willow"),
	MAPLE("Maple"),
	YEW("Yew"),
	MAGIC("Magic"),
	REDWOOD("Redwood");

	private final String name;


	@Override
	public String toString()
	{
		return name;
	}
}
