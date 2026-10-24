package net.runelite.client.plugins.microbot.util.camera;

import java.awt.Polygon;
import java.awt.Rectangle;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.ScriptID;
import net.runelite.api.TileObject;
import net.runelite.api.VarClientInt;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.actor.Rs2ActorModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.mobile.MobileActions;

/**
 * Camera reads and moves.
 * <p>
 * Provenance: adapted from chsami/Microbot {@code util/camera/Rs2Camera.java} at
 * {@code a2114401bbbc35e9762db9e8aa666d70ffcd24d8} (2.6.38, BSD 2-Clause). Reads, angle maths and
 * the legacy angle units (yaw 0..2047, pitch 128..383) match upstream.
 * <p>
 * Mobile adaptations:
 * <ul>
 *     <li>Moves set the engine's camera yaw/pitch <em>target</em> on the client thread, so the
 *     engine turns the camera itself. Upstream holds arrow keys and eases the turn over a randomised
 *     duration; neither key simulation nor humanised timing is ported.</li>
 *     <li>Overloads accept the mobile snapshot models ({@link Rs2ActorModel},
 *     {@link Rs2TileObjectModel}), which do not implement {@code Actor}/{@code TileObject}.</li>
 *     <li>All client reads go through the client thread; a read that cannot reach it returns 0
 *     (or {@code false}).</li>
 *     <li>Not ported: NPC tracking ({@code trackNpc}), {@code centerTileOnScreen}.</li>
 * </ul>
 */
public final class Rs2Camera
{
	private static final int LEGACY_ANGLE_UNITS = 2048;
	private static final int CLIENT_ANGLE_SCALE = 8;
	private static final int LEGACY_MIN_PITCH = 128;
	private static final int LEGACY_MAX_PITCH = 383;

	private Rs2Camera()
	{
	}

	private static Client client()
	{
		return Microbot.getClient();
	}

	// --- angles -----------------------------------------------------------------------------

	public static int angleToTile(WorldPoint target)
	{
		if (target == null)
		{
			return 0;
		}
		return MobileActions.read(() ->
		{
			net.runelite.api.Player local = client().getLocalPlayer();
			WorldPoint player = local == null ? null : local.getWorldLocation();
			if (player == null)
			{
				int facing = (int) (fromClientAngleUnits(client().getCameraYaw()) * (360.0 / LEGACY_ANGLE_UNITS));
				return (facing + 90) % 360;
			}
			int angle = (int) Math.toDegrees(Math.atan2(target.getY() - player.getY(), target.getX() - player.getX()));
			return angle >= 0 ? angle : 360 + angle;
		}, 0);
	}

	public static int angleToTile(Actor actor)
	{
		return actor == null ? 0 : angleToTile(MobileActions.read(actor::getWorldLocation, null));
	}

	public static int angleToTile(TileObject object)
	{
		return object == null ? 0 : angleToTile(MobileActions.read(object::getWorldLocation, null));
	}

	public static int angleToTile(Rs2ActorModel actor)
	{
		return actor == null ? 0 : angleToTile(actor.getWorldLocation());
	}

	public static int angleToTile(Rs2TileObjectModel object)
	{
		return object == null ? 0 : angleToTile(object.getWorldLocation());
	}

	public static int angleToTile(LocalPoint localPoint)
	{
		if (localPoint == null)
		{
			return 0;
		}
		return angleToTile(MobileActions.read(() -> WorldPoint.fromLocal(client(), localPoint), null));
	}

	private static int bearing(int angleToTile)
	{
		int a = (angleToTile - 90) % 360;
		return a < 0 ? a + 360 : a;
	}

	public static int getCharacterAngle(Actor actor)
	{
		return bearing(angleToTile(actor));
	}

	public static int getObjectAngle(TileObject object)
	{
		return bearing(angleToTile(object));
	}

	public static int getObjectAngle(Rs2TileObjectModel object)
	{
		return bearing(angleToTile(object));
	}

	public static int getTileAngle(Actor actor)
	{
		return bearing(angleToTile(actor));
	}

	public static int getTileAngle(TileObject object)
	{
		return bearing(angleToTile(object));
	}

	/** Upstream semantics: true when the camera is within {@code desiredMaxAngle} of the target. */
	public static boolean isAngleGood(int targetAngle, int desiredMaxAngle)
	{
		return Math.abs(getAngleTo(targetAngle)) <= desiredMaxAngle;
	}

	public static int getAngleTo(int degrees)
	{
		int ca = getAngle();
		if (ca < degrees)
		{
			ca += 360;
		}
		int da = ca - degrees;
		if (da > 180)
		{
			da -= 360;
		}
		return da;
	}

	public static int getAngle()
	{
		return (int) (getYaw() * (360.0 / LEGACY_ANGLE_UNITS));
	}

	/** Upstream formula: camera yaw (0..2047) that faces an entity at {@code npcAngle} degrees. */
	public static int calculateCameraYaw(int npcAngle)
	{
		return (1536 + (int) Math.round(npcAngle * (LEGACY_ANGLE_UNITS / 360.0))) % LEGACY_ANGLE_UNITS;
	}

	// --- turning ----------------------------------------------------------------------------

	/** As upstream: the tile's canvas outline lies inside a box {@code marginPercentage}% of the viewport around its centre. */
	public static boolean isTileCenteredOnScreen(LocalPoint tile, double marginPercentage)
	{
		return tile != null && MobileActions.read(() ->
		{
			Client client = Microbot.getClient();
			Polygon poly = Perspective.getCanvasTilePoly(client, tile);
			if (poly == null)
			{
				return false;
			}
			int width = client.getViewportWidth();
			int height = client.getViewportHeight();
			int marginX = (int) (width * (marginPercentage / 100.0));
			int marginY = (int) (height * (marginPercentage / 100.0));
			return new Rectangle(width / 2 - marginX / 2, height / 2 - marginY / 2, marginX, marginY).contains(poly.getBounds());
		}, false);
	}

	/** As upstream, with a 10% margin. */
	public static boolean isTileCenteredOnScreen(LocalPoint tile)
	{
		return isTileCenteredOnScreen(tile, 10);
	}

	/** As upstream: turns the camera to face {@code tile} unless it is already centred within the margin. */
	public static void centerTileOnScreen(LocalPoint tile, double marginPercentage)
	{
		if (tile == null || isTileCenteredOnScreen(tile, marginPercentage))
		{
			return;
		}
		int raw = angleToTile(tile) - 90;
		setAngle(raw < 0 ? raw + 360 : raw, 5);
	}

	/** As upstream, with a 10% margin. */
	public static void centerTileOnScreen(LocalPoint tile)
	{
		centerTileOnScreen(tile, 10.0);
	}

	/** Turns until the target is within {@code maxAngle}; mobile sets the yaw target directly. */
	public static void setAngle(int targetDegrees, int maxAngle)
	{
		if (Math.abs(getAngleTo(targetDegrees)) > maxAngle)
		{
			// getAngle() is yaw * 360 / 2048, so this yaw makes getAngleTo(targetDegrees) zero.
			setYawInstant((int) Math.round(((targetDegrees % 360 + 360) % 360) * (LEGACY_ANGLE_UNITS / 360.0)) % LEGACY_ANGLE_UNITS);
		}
	}

	public static void turnTo(Actor actor)
	{
		turnTo(actor, 40);
	}

	public static void turnTo(Actor actor, int maxAngle)
	{
		setAngle(getCharacterAngle(actor), maxAngle);
	}

	public static void turnTo(TileObject object)
	{
		turnTo(object, 40);
	}

	public static void turnTo(TileObject object, int maxAngle)
	{
		setAngle(getObjectAngle(object), maxAngle);
	}

	public static void turnTo(Rs2ActorModel actor)
	{
		turnTo(actor, 40);
	}

	public static void turnTo(Rs2ActorModel actor, int maxAngle)
	{
		setAngle(bearing(angleToTile(actor)), maxAngle);
	}

	public static void turnTo(Rs2TileObjectModel object)
	{
		turnTo(object, 40);
	}

	public static void turnTo(Rs2TileObjectModel object, int maxAngle)
	{
		setAngle(getObjectAngle(object), maxAngle);
	}

	public static void turnTo(LocalPoint localPoint)
	{
		turnTo(localPoint, 40);
	}

	public static void turnTo(LocalPoint localPoint, int maxAngle)
	{
		setAngle(bearing(angleToTile(localPoint)), maxAngle);
	}

	// --- pitch, yaw, zoom -------------------------------------------------------------------

	public static int getPitch()
	{
		return fromClientAngleUnits(MobileActions.read(() -> client().getCameraPitch(), 0));
	}

	public static void setPitch(int pitch)
	{
		setPitchInstant(pitch);
	}

	public static void setPitchInstant(int pitch)
	{
		setTarget(Math.max(LEGACY_MIN_PITCH, Math.min(LEGACY_MAX_PITCH, pitch)), true);
	}

	public static float cameraPitchPercentage()
	{
		return (getPitch() - LEGACY_MIN_PITCH) / (float) (LEGACY_MAX_PITCH - LEGACY_MIN_PITCH);
	}

	/** Upstream semantics (0..1 of the pitch range); mobile sets the pitch target directly. */
	public static void adjustPitch(float percentage)
	{
		float clamped = Math.max(0f, Math.min(1f, percentage));
		setPitchInstant(LEGACY_MIN_PITCH + Math.round(clamped * (LEGACY_MAX_PITCH - LEGACY_MIN_PITCH)));
	}

	public static void resetPitch()
	{
		if (getPitch() < 280)
		{
			setPitch(280);
		}
	}

	public static int getYaw()
	{
		return fromClientAngleUnits(MobileActions.read(() -> client().getCameraYaw(), 0));
	}

	public static void setYaw(int yaw)
	{
		setYawInstant(yaw);
	}

	public static void setYawInstant(int yaw)
	{
		if (yaw >= 0 && yaw <= LEGACY_ANGLE_UNITS)
		{
			setTarget(yaw % LEGACY_ANGLE_UNITS, false);
		}
	}

	public static int getZoom()
	{
		return MobileActions.read(() -> client().getVarcIntValue(VarClientInt.CAMERA_ZOOM_RESIZABLE_VIEWPORT), 0);
	}

	public static void setZoom(int zoom)
	{
		if (Microbot.getClientThread() != null)
		{
			Microbot.getClientThread().invokeLater(() -> client().runScript(ScriptID.CAMERA_DO_ZOOM, zoom, zoom));
		}
	}

	public static void resetZoom()
	{
		if (getZoom() > 200)
		{
			setZoom(200);
		}
	}

	private static void setTarget(int legacyTarget, boolean pitch)
	{
		int target = legacyTarget * CLIENT_ANGLE_SCALE;
		if (Microbot.getClientThread() == null)
		{
			return;
		}
		Microbot.getClientThread().invoke(() ->
		{
			if (pitch)
			{
				client().setCameraPitchTarget(target);
			}
			else
			{
				client().setCameraYawTarget(target);
			}
		});
	}

	static int fromClientAngleUnits(int clientAngle)
	{
		return clientAngle / CLIENT_ANGLE_SCALE;
	}

	// --- visibility -------------------------------------------------------------------------

	public static boolean isTileOnScreen(TileObject object)
	{
		return object != null && isTileOnScreen(MobileActions.read(object::getLocalLocation, null));
	}

	public static boolean isTileOnScreen(Rs2TileObjectModel object)
	{
		return object != null && isTileOnScreen(object.getLocalLocation());
	}

	/** Upstream semantics: the tile polygon intersects the viewport and projects in front of the camera. */
	public static boolean isTileOnScreen(LocalPoint localPoint)
	{
		if (localPoint == null)
		{
			return false;
		}
		return MobileActions.read(() ->
		{
			Client client = client();
			WorldView view = client.getWorldView(localPoint.getWorldView());
			int sceneX = localPoint.getSceneX();
			int sceneY = localPoint.getSceneY();
			if (view == null || sceneX < 0 || sceneY < 0 || sceneX >= view.getSizeX() || sceneY >= view.getSizeY())
			{
				return false;
			}
			Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);
			if (poly == null || !poly.intersects(new Rectangle(0, 0, client.getViewportWidth(), client.getViewportHeight())))
			{
				return false;
			}
			return Perspective.localToCanvas(client, localPoint, client.getPlane()) != null;
		}, false);
	}

	// ---- NPC tracking (actions) ----

	/** Upstream's tracker period. */
	private static final long TRACK_PERIOD_MILLIS = 200;
	private static volatile java.util.concurrent.ScheduledExecutorService trackScheduler;
	private static volatile java.util.concurrent.ScheduledFuture<?> trackingTask;

	/**
	 * As upstream: keeps the camera turned towards the nearest NPC with this ID, every 200 ms
	 * while logged in, until {@link #stopTrackingNpc()}. Nothing when already tracking one.
	 */
	public static synchronized void trackNpc(int npcId)
	{
		if (trackingTask != null)
		{
			return;
		}
		if (trackScheduler == null)
		{
			trackScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r ->
			{
				Thread thread = new Thread(r, "rs2camera-track");
				thread.setDaemon(true);
				return thread;
			});
		}
		trackingTask = trackScheduler.scheduleAtFixedRate(() ->
		{
			if (!Microbot.isLoggedIn())
			{
				return;
			}
			Actor npc = net.runelite.client.plugins.microbot.util.npc.Rs2Npc.getNpc(npcId);
			if (npc != null)
			{
				turnTo(npc, 0);
			}
		}, 0, TRACK_PERIOD_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
	}

	/** As upstream: stops {@link #trackNpc(int)}. */
	public static synchronized void stopTrackingNpc()
	{
		if (trackingTask != null)
		{
			trackingTask.cancel(false);
			trackingTask = null;
		}
	}

	/** As upstream: whether an NPC is being tracked. */
	public static boolean isTrackingNpc()
	{
		return trackingTask != null;
	}
}
