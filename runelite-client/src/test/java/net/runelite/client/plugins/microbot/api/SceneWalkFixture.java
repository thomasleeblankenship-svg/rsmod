package net.runelite.client.plugins.microbot.api;

import java.awt.Canvas;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.headless.JavaBridge;
import org.mockito.MockedStatic;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Simulated renderer/picking, with the real bridge settle gate on the fake client thread. */
final class SceneWalkFixture implements AutoCloseable
{
	private final ReadApiFixture fixture;
	private MockedStatic<Perspective> projection;
	private ScheduledExecutorService frames;
	private final AtomicReference<WorldPoint> picked = new AtomicReference<>();

	SceneWalkFixture(ReadApiFixture fixture) throws Exception
	{
		this.fixture = fixture;
		when(fixture.client.getCanvas()).thenReturn(new Canvas());
		when(fixture.client.getCanvasWidth()).thenReturn(800);
		when(fixture.client.getCanvasHeight()).thenReturn(600);
		when(fixture.client.getViewportWidth()).thenReturn(800);
		when(fixture.client.getViewportHeight()).thenReturn(600);
		when(fixture.client.getMouseCanvasPosition()).thenReturn(new Point(100, 120));
		// LocalPoint.fromWorld rejects every tile of a zero-size scene; use the standard 104x104 scene.
		when(fixture.topLevel.getSizeX()).thenReturn(104);
		when(fixture.topLevel.getSizeY()).thenReturn(104);
		Tile selected = mock(Tile.class);
		when(selected.getWorldLocation()).thenAnswer(call -> picked.get());
		when(fixture.topLevel.getSelectedSceneTile()).thenReturn(selected);
		fixture.clientExecutor.submit(() ->
		{
			projection = mockStatic(Perspective.class);
			projection.when(() -> Perspective.localToCanvas(eq(fixture.client), any(LocalPoint.class), anyInt()))
				.thenAnswer(call ->
				{
					LocalPoint local = call.getArgument(1);
					picked.set(new WorldPoint(3200 + local.getSceneX(), 3200 + local.getSceneY(), call.getArgument(2)));
					return new Point(100, 120);
				});
			JavaBridge.register(fixture.client);
			JavaBridge.setClientThread(fixture.clientThread);
		}).get();
	}

	void settle() throws Exception
	{
		fixture.clientExecutor.submit(() ->
		{
			for (int i = 0; i < 4; i++) { JavaBridge.tapTick(fixture.client); }
		}).get();
	}

	void animate()
	{
		frames = Executors.newSingleThreadScheduledExecutor();
		frames.scheduleWithFixedDelay(() -> fixture.clientExecutor.execute(() -> JavaBridge.tapTick(fixture.client)),
			20, 20, TimeUnit.MILLISECONDS);
	}

	@Override
	public void close() throws Exception
	{
		if (frames != null) { frames.shutdownNow(); frames.awaitTermination(2, TimeUnit.SECONDS); }
		fixture.clientExecutor.submit(() ->
		{
			JavaBridge.scriptInputBusy(); // Discard the cancelled owning session before the next test.
			projection.close();
			JavaBridge.register(null);
			JavaBridge.setClientThread(null);
		}).get();
	}
}
