package net.runelite.client.plugins.microbot.util.skills.fletching.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Output choices used by the uploaded AutoWoodcutting configuration. */
@Getter
@RequiredArgsConstructor
public enum FletchingItem
{
	ARROW_SHAFT("Arrow shaft", '1', "arrow shaft", 1),
	SHORT("Short bows", '2', "shortbow", 1),
	LONG("Long bows", '3', "longbow", 1),
	STOCK("Crossbow stock", '4', "stock", 1),
	SHIELD("Shield", '5', "shield", 2);

	private final String name;
	private final char option;
	private final String containsInventoryName;
	private final int amountRequired;

	@Override
	public String toString() { return name; }
}
