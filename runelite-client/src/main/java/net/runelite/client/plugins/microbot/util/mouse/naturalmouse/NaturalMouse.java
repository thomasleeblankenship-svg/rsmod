package net.runelite.client.plugins.microbot.util.mouse.naturalmouse;

import java.awt.Dimension;
import java.awt.Point;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mobile.MobileActions;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseInfoAccessor;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SystemCalls;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultMouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FactoryTemplates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Upstream Microbot's humanised mouse ({@code util/mouse/naturalmouse/NaturalMouse}, chsami/Microbot
 * {@code a2114401}, BSD 2-Clause), driving RuneMobile's synthetic game pointer: each step of a curved
 * path ({@link FactoryTemplates}, picked by the activity intensity) is a {@code MOUSE_MOVED} posted
 * through {@link MobileActions#pointerMove}, and a path off the canvas ends with the pointer leaving it.
 * <p>
 * Mobile scope: the anti-ban's random and off-screen moves. Script clicks are taps and do not travel
 * a path first. A path stops at once when the user touches the screen or the script's input session
 * ends. Called on the client thread, the path runs on its own thread so the client never waits.
 */
public class NaturalMouse
{
	private static final Logger LOG = LoggerFactory.getLogger(NaturalMouse.class);

	public final MouseMotionNature nature;
	private final ExecutorService executorService = Executors.newSingleThreadExecutor(runnable ->
	{
		Thread thread = new Thread(runnable, "natural-mouse");
		thread.setDaemon(true);
		return thread;
	});
	private volatile MouseMotionFactory cachedFactory;
	private volatile ActivityIntensity cachedIntensity;

	public NaturalMouse()
	{
		nature = new DefaultMouseMotionNature();
		nature.setSystemCalls(new SystemCallsImpl());
		nature.setMouseInfo(new MouseInfoImpl());
	}

	/** As upstream: a humanised move to the canvas point {@code (dx, dy)}. */
	public void moveTo(int dx, int dy)
	{
		Point mousePos = Microbot.getMouse().getMousePosition();
		if (mousePos.x == dx && mousePos.y == dy)
		{
			return;
		}
		run(() -> move(dx, dy));
	}

	/** As upstream: {@link #moveOffScreen(double)} with a 100% chance. */
	public void moveOffScreen()
	{
		moveOffScreen(100.0);
	}

	/**
	 * As upstream: with {@code chancePercentage} (0 to 100) chance, travels to a canvas edge and
	 * leaves the canvas there.
	 */
	public void moveOffScreen(double chancePercentage)
	{
		if (chancePercentage < 100 && !Rs2Random.dicePercentage(chancePercentage))
		{
			return;
		}
		Dimension canvas = canvasSize();
		if (canvas.width <= 0 || canvas.height <= 0)
		{
			return;
		}
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int horizontal = random.nextBoolean() ? -1 : canvas.width + 1;
		int vertical = random.nextBoolean() ? -1 : canvas.height + 1;
		boolean exitHorizontally = random.nextBoolean();
		int targetX = exitHorizontally ? horizontal : random.nextInt(0, canvas.width + 1);
		int targetY = exitHorizontally ? random.nextInt(0, canvas.height + 1) : vertical;

		// One task: the path clamps to the canvas, so it reaches the edge and the last step crosses it.
		run(() ->
		{
			if (move(targetX, targetY) && MobileActions.pointerMove(targetX, targetY))
			{
				Microbot.getMouse().move(targetX, targetY);
			}
		});
	}

	/** As upstream: a humanised move to a random canvas point. */
	public void moveRandom()
	{
		Dimension canvas = canvasSize();
		if (canvas.width <= 0 || canvas.height <= 0)
		{
			return;
		}
		ThreadLocalRandom random = ThreadLocalRandom.current();
		moveTo(random.nextInt(0, canvas.width), random.nextInt(0, canvas.height));
	}

	/** As upstream: the motion factory for the current activity intensity. */
	public MouseMotionFactory getFactory()
	{
		ActivityIntensity intensity = Rs2Antiban.getActivityIntensity();
		if (cachedFactory != null && intensity == cachedIntensity)
		{
			return cachedFactory;
		}
		MouseMotionFactory factory;
		if (intensity == ActivityIntensity.VERY_LOW)
		{
			factory = FactoryTemplates.createAverageComputerUserMotionFactory(nature);
		}
		else if (intensity == ActivityIntensity.LOW)
		{
			factory = FactoryTemplates.createNormalGamerMotionFactory(nature);
		}
		else if (intensity == ActivityIntensity.MODERATE || intensity == ActivityIntensity.HIGH)
		{
			factory = FactoryTemplates.createFastGamerMotionFactory(nature);
		}
		else
		{
			factory = FactoryTemplates.createSuperFastGamerMotionFactory(nature);
		}
		cachedIntensity = intensity;
		cachedFactory = factory;
		return factory;
	}

	/** Runs {@code task} here, or on the mouse thread when this is the client thread (which must not wait). */
	private void run(Runnable task)
	{
		Client client = Microbot.getClient();
		if (client != null && client.isClientThread())
		{
			executorService.submit(task);
		}
		else
		{
			task.run();
		}
	}

	/** Travels the path; false when it was cut short (user touch, session ended, interrupt). */
	private synchronized boolean move(int dx, int dy)
	{
		try
		{
			getFactory().build(dx, dy).move();
			return true;
		}
		catch (PathRefused refused)
		{
			return false;
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
			return false;
		}
		catch (RuntimeException exception)
		{
			LOG.debug("Mouse path failed", exception);
			return false;
		}
	}

	private static Dimension canvasSize()
	{
		Client client = Microbot.getClient();
		return client == null ? new Dimension() : new Dimension(client.getCanvasWidth(), client.getCanvasHeight());
	}

	/** Thrown from a path step the bridge refused, to stop the rest of the path. */
	private static final class PathRefused extends RuntimeException
	{
		PathRefused()
		{
			super(null, null, false, false);
		}
	}

	private static class MouseInfoImpl implements MouseInfoAccessor
	{
		@Override
		public Point getMousePosition()
		{
			return Microbot.getMouse().getMousePosition();
		}
	}

	private static class SystemCallsImpl implements SystemCalls
	{
		@Override
		public long currentTimeMillis()
		{
			return System.currentTimeMillis();
		}

		@Override
		public void sleep(long time)
		{
			Global.sleep((int) time);
		}

		@Override
		public Dimension getScreenSize()
		{
			return canvasSize();
		}

		@Override
		public void setMousePosition(int x, int y)
		{
			if (Thread.currentThread().isInterrupted() || !MobileActions.pointerMove(x, y))
			{
				throw new PathRefused();
			}
			Microbot.getMouse().move(x, y);
		}
	}
}
