/*
 * Copied from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/misc/Operation.java.
 * BSD 2-Clause License; see LICENSE. Formatting adapted to this repository's style.
 */
package net.runelite.client.plugins.microbot.util.misc;

import java.util.function.BiFunction;
import lombok.Getter;

/** As upstream: a comparison a script configures (for example "agility level >= 50"). */
public enum Operation
{
	GREATER(">", (x, y) -> x > y),
	LESS("<", (x, y) -> x < y),
	LESS_EQUAL("<=", (x, y) -> x <= y),
	EQUAL("==", Integer::equals),
	GREATER_EQUAL(">=", (x, y) -> x >= y),
	NOT_EQUAL("=/=", (x, y) -> !x.equals(y));

	private final BiFunction<Integer, Integer, Boolean> operation;
	@Getter
	private final String displayText;

	Operation(String displayText, BiFunction<Integer, Integer, Boolean> operation)
	{
		this.displayText = displayText;
		this.operation = operation;
	}

	/** Whether {@code numberToCheck} relates to {@code numberToCheckAgainst} by this operation. */
	public boolean check(int numberToCheck, int numberToCheckAgainst)
	{
		return operation.apply(numberToCheck, numberToCheckAgainst);
	}
}
