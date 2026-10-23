package net.runelite.client.plugins.microbot.util;

import net.runelite.api.Actor;
import net.runelite.client.plugins.microbot.mobile.MobileActions;

/**
 * Microbot's legacy live actor wrapper: implements {@link Actor} by delegating every call to the
 * wrapped actor on the client thread. Off the client thread each call waits for the client thread;
 * when it cannot be reached (or the runtime is not installed) a getter returns {@code null}, 0 or
 * {@code false} and a setter does nothing. Prefer the snapshot models of the {@code api} caches for
 * new code. Written for RuneMobile; the delegating methods are generated from the {@link Actor}
 * interface.
 */
@SuppressWarnings("deprecation")
public class ActorModel implements Actor
{
	private final Actor actor;

	public ActorModel(Actor actor)
	{
		this.actor = actor;
	}

	/** The wrapped live actor; read it on the client thread. */
	public Actor getActor()
	{
		return actor;
	}

	@Override
	public void clearSpotAnims()
	{
		MobileActions.read(() ->
		{
			actor.clearSpotAnims();
			return null;
		}, null);
	}

	@Override
	public void createSpotAnim(final int p0, final int p1, final int p2, final int p3)
	{
		MobileActions.read(() ->
		{
			actor.createSpotAnim(p0, p1, p2, p3);
			return null;
		}, null);
	}

	@Override
	public int getAnimationFrame()
	{
		return MobileActions.<Integer>read(() -> actor.getAnimationFrame(), 0);
	}

	@Override
	public int getAnimationHeightOffset()
	{
		return MobileActions.<Integer>read(() -> actor.getAnimationHeightOffset(), 0);
	}

	@Override
	public int getAnimation()
	{
		return MobileActions.<Integer>read(() -> actor.getAnimation(), 0);
	}

	@Override
	public net.runelite.api.coords.LocalPoint getCameraFocus()
	{
		return MobileActions.<net.runelite.api.coords.LocalPoint>read(() -> actor.getCameraFocus(), null);
	}

	@Override
	public net.runelite.api.Point getCanvasImageLocation(final java.awt.image.BufferedImage p0, final int p1)
	{
		return MobileActions.<net.runelite.api.Point>read(() -> actor.getCanvasImageLocation(p0, p1), null);
	}

	@Override
	public net.runelite.api.Point getCanvasSpriteLocation(final net.runelite.api.SpritePixels p0, final int p1)
	{
		return MobileActions.<net.runelite.api.Point>read(() -> actor.getCanvasSpriteLocation(p0, p1), null);
	}

	@Override
	public net.runelite.api.Point getCanvasTextLocation(final java.awt.Graphics2D p0, final String p1, final int p2)
	{
		return MobileActions.<net.runelite.api.Point>read(() -> actor.getCanvasTextLocation(p0, p1, p2), null);
	}

	@Override
	public java.awt.Polygon getCanvasTilePoly()
	{
		return MobileActions.<java.awt.Polygon>read(() -> actor.getCanvasTilePoly(), null);
	}

	@Override
	public int getCombatLevel()
	{
		return MobileActions.<Integer>read(() -> actor.getCombatLevel(), 0);
	}

	@Override
	public java.awt.Shape getConvexHull()
	{
		return MobileActions.<java.awt.Shape>read(() -> actor.getConvexHull(), null);
	}

	@Override
	public int getCurrentOrientation()
	{
		return MobileActions.<Integer>read(() -> actor.getCurrentOrientation(), 0);
	}

	@Override
	public int getFootprintSize()
	{
		return MobileActions.<Integer>read(() -> actor.getFootprintSize(), 0);
	}

	@Override
	public int getGraphicHeight()
	{
		return MobileActions.<Integer>read(() -> actor.getGraphicHeight(), 0);
	}

	@Override
	public int getGraphic()
	{
		return MobileActions.<Integer>read(() -> actor.getGraphic(), 0);
	}

	@Override
	public long getHash()
	{
		return MobileActions.<Long>read(() -> actor.getHash(), 0L);
	}

	@Override
	public int getHealthRatio()
	{
		return MobileActions.<Integer>read(() -> actor.getHealthRatio(), 0);
	}

	@Override
	public int getHealthScale()
	{
		return MobileActions.<Integer>read(() -> actor.getHealthScale(), 0);
	}

	@Override
	public int getIdlePoseAnimation()
	{
		return MobileActions.<Integer>read(() -> actor.getIdlePoseAnimation(), 0);
	}

	@Override
	public int getIdleRotateLeft()
	{
		return MobileActions.<Integer>read(() -> actor.getIdleRotateLeft(), 0);
	}

	@Override
	public int getIdleRotateRight()
	{
		return MobileActions.<Integer>read(() -> actor.getIdleRotateRight(), 0);
	}

	@Override
	public Actor getInteracting()
	{
		return MobileActions.<Actor>read(() -> actor.getInteracting(), null);
	}

	@Override
	public net.runelite.api.coords.LocalPoint getLocalLocation()
	{
		return MobileActions.<net.runelite.api.coords.LocalPoint>read(() -> actor.getLocalLocation(), null);
	}

	@Override
	public int getLogicalHeight()
	{
		return MobileActions.<Integer>read(() -> actor.getLogicalHeight(), 0);
	}

	@Override
	public net.runelite.api.Point getMinimapLocation()
	{
		return MobileActions.<net.runelite.api.Point>read(() -> actor.getMinimapLocation(), null);
	}

	@Override
	public int getModelHeight()
	{
		return MobileActions.<Integer>read(() -> actor.getModelHeight(), 0);
	}

	@Override
	public net.runelite.api.Model getModel()
	{
		return MobileActions.<net.runelite.api.Model>read(() -> actor.getModel(), null);
	}

	@Override
	public String getName()
	{
		return MobileActions.<String>read(() -> actor.getName(), null);
	}

	@Override
	public net.runelite.api.Node getNext()
	{
		return MobileActions.<net.runelite.api.Node>read(() -> actor.getNext(), null);
	}

	@Override
	public int getOrientation()
	{
		return MobileActions.<Integer>read(() -> actor.getOrientation(), 0);
	}

	@Override
	public int getOverheadCycle()
	{
		return MobileActions.<Integer>read(() -> actor.getOverheadCycle(), 0);
	}

	@Override
	public String getOverheadText()
	{
		return MobileActions.<String>read(() -> actor.getOverheadText(), null);
	}

	@Override
	public int getPoseAnimationFrame()
	{
		return MobileActions.<Integer>read(() -> actor.getPoseAnimationFrame(), 0);
	}

	@Override
	public int getPoseAnimation()
	{
		return MobileActions.<Integer>read(() -> actor.getPoseAnimation(), 0);
	}

	@Override
	public net.runelite.api.Node getPrevious()
	{
		return MobileActions.<net.runelite.api.Node>read(() -> actor.getPrevious(), null);
	}

	@Override
	public int getRenderMode()
	{
		return MobileActions.<Integer>read(() -> actor.getRenderMode(), 0);
	}

	@Override
	public int getRunAnimation()
	{
		return MobileActions.<Integer>read(() -> actor.getRunAnimation(), 0);
	}

	@Override
	public int getSpotAnimFrame()
	{
		return MobileActions.<Integer>read(() -> actor.getSpotAnimFrame(), 0);
	}

	@Override
	public net.runelite.api.IterableHashTable<net.runelite.api.ActorSpotAnim> getSpotAnims()
	{
		return MobileActions.<net.runelite.api.IterableHashTable<net.runelite.api.ActorSpotAnim>>read(() -> actor.getSpotAnims(), null);
	}

	@Override
	public int getWalkAnimation()
	{
		return MobileActions.<Integer>read(() -> actor.getWalkAnimation(), 0);
	}

	@Override
	public int getWalkRotate180()
	{
		return MobileActions.<Integer>read(() -> actor.getWalkRotate180(), 0);
	}

	@Override
	public int getWalkRotateLeft()
	{
		return MobileActions.<Integer>read(() -> actor.getWalkRotateLeft(), 0);
	}

	@Override
	public int getWalkRotateRight()
	{
		return MobileActions.<Integer>read(() -> actor.getWalkRotateRight(), 0);
	}

	@Override
	public net.runelite.api.coords.WorldArea getWorldArea()
	{
		return MobileActions.<net.runelite.api.coords.WorldArea>read(() -> actor.getWorldArea(), null);
	}

	@Override
	public net.runelite.api.coords.WorldPoint getWorldLocation()
	{
		return MobileActions.<net.runelite.api.coords.WorldPoint>read(() -> actor.getWorldLocation(), null);
	}

	@Override
	public net.runelite.api.WorldView getWorldView()
	{
		return MobileActions.<net.runelite.api.WorldView>read(() -> actor.getWorldView(), null);
	}

	@Override
	public boolean hasSpotAnim(final int p0)
	{
		return MobileActions.<Boolean>read(() -> actor.hasSpotAnim(p0), false);
	}

	@Override
	public boolean isDead()
	{
		return MobileActions.<Boolean>read(() -> actor.isDead(), false);
	}

	@Override
	public boolean isInteracting()
	{
		return MobileActions.<Boolean>read(() -> actor.isInteracting(), false);
	}

	@Override
	public void removeSpotAnim(final int p0)
	{
		MobileActions.read(() ->
		{
			actor.removeSpotAnim(p0);
			return null;
		}, null);
	}

	@Override
	public void setActionFrame(final int p0)
	{
		MobileActions.read(() ->
		{
			actor.setActionFrame(p0);
			return null;
		}, null);
	}

	@Override
	public void setAnimationFrame(final int p0)
	{
		MobileActions.read(() ->
		{
			actor.setAnimationFrame(p0);
			return null;
		}, null);
	}

	@Override
	public void setAnimation(final int p0)
	{
		MobileActions.read(() ->
		{
			actor.setAnimation(p0);
			return null;
		}, null);
	}

	@Override
	public void setDead(final boolean p0)
	{
		MobileActions.read(() ->
		{
			actor.setDead(p0);
			return null;
		}, null);
	}

	@Override
	public void setGraphicHeight(final int p0)
	{
		MobileActions.read(() ->
		{
			actor.setGraphicHeight(p0);
			return null;
		}, null);
	}

	@Override
	public void setGraphic(final int p0)
	{
		MobileActions.read(() ->
		{
			actor.setGraphic(p0);
			return null;
		}, null);
	}

	@Override
	public void setIdlePoseAnimation(final int p0)
	{
		MobileActions.read(() ->
		{
			actor.setIdlePoseAnimation(p0);
			return null;
		}, null);
	}

	@Override
	public void setIdleRotateLeft(final int p0)
	{
		MobileActions.read(() ->
		{
			actor.setIdleRotateLeft(p0);
			return null;
		}, null);
	}

	@Override
	public void setIdleRotateRight(final int p0)
	{
		MobileActions.read(() ->
		{
			actor.setIdleRotateRight(p0);
			return null;
		}, null);
	}

	@Override
	public void setModelHeight(final int p0)
	{
		MobileActions.read(() ->
		{
			actor.setModelHeight(p0);
			return null;
		}, null);
	}

	@Override
	public void setOverheadCycle(final int p0)
	{
		MobileActions.read(() ->
		{
			actor.setOverheadCycle(p0);
			return null;
		}, null);
	}

	@Override
	public void setOverheadText(final String p0)
	{
		MobileActions.read(() ->
		{
			actor.setOverheadText(p0);
			return null;
		}, null);
	}

	@Override
	public void setPoseAnimationFrame(final int p0)
	{
		MobileActions.read(() ->
		{
			actor.setPoseAnimationFrame(p0);
			return null;
		}, null);
	}

	@Override
	public void setPoseAnimation(final int p0)
	{
		MobileActions.read(() ->
		{
			actor.setPoseAnimation(p0);
			return null;
		}, null);
	}

	@Override
	public void setRunAnimation(final int p0)
	{
		MobileActions.read(() ->
		{
			actor.setRunAnimation(p0);
			return null;
		}, null);
	}

	@Override
	public void setSpotAnimFrame(final int p0)
	{
		MobileActions.read(() ->
		{
			actor.setSpotAnimFrame(p0);
			return null;
		}, null);
	}

	@Override
	public void setWalkAnimation(final int p0)
	{
		MobileActions.read(() ->
		{
			actor.setWalkAnimation(p0);
			return null;
		}, null);
	}

	@Override
	public void setWalkRotate180(final int p0)
	{
		MobileActions.read(() ->
		{
			actor.setWalkRotate180(p0);
			return null;
		}, null);
	}

	@Override
	public void setWalkRotateLeft(final int p0)
	{
		MobileActions.read(() ->
		{
			actor.setWalkRotateLeft(p0);
			return null;
		}, null);
	}

	@Override
	public void setWalkRotateRight(final int p0)
	{
		MobileActions.read(() ->
		{
			actor.setWalkRotateRight(p0);
			return null;
		}, null);
	}
}
