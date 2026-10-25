package fakegamepack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Stand-ins for obfuscated gamepack classes (outside net.runelite, which Rs2Reflection skips), for Rs2ReflectionTest. */
public final class FakeItems
{
	private FakeItems()
	{
	}

	/** The ground actions as a list of strings inside an object field. */
	public static final class StringListItem
	{
		private final Holder holder;

		public StringListItem(String... actions)
		{
			holder = new Holder(new ArrayList<>(Arrays.asList(actions)));
		}
	}

	/** Holds a list field, as the gamepack's op container does. */
	public static final class Holder
	{
		private final int unused = 7;
		private final List<Object> ops;

		Holder(List<Object> ops)
		{
			this.ops = ops;
		}
	}

	/** The ground actions as beans, each with one string field. */
	public static final class BeanListItem
	{
		private final Holder holder;

		public BeanListItem(String... actions)
		{
			List<Object> beans = new ArrayList<>();
			for (String action : actions)
			{
				beans.add(action == null ? null : new Op(action));
			}
			holder = new Holder(beans);
		}
	}

	/** One menu op. */
	public static final class Op
	{
		private final int index = 0;
		private final String text;

		Op(String text)
		{
			this.text = text;
		}
	}

	/** No list anywhere: the reader must fall back to upstream's default. */
	public static final class NoActionsItem
	{
		private final int id = 1;
	}
}
