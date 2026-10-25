/*
 * Copied from chsami/Microbot a2114401bbbc35e9762db9e8aa666d70ffcd24d8,
 * runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/boat/data/RelativeMove.java.
 * BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api.boat.data;

/** As upstream: a relative boat move. */
public final class RelativeMove
{
    private final int dx;
    private final int dy;

    public RelativeMove(int dx, int dy)
    {
        this.dx = dx;
        this.dy = dy;
    }

    public int getDx()
    {
        return dx;
    }

    public int getDy()
    {
        return dy;
    }

    @Override
    public String toString()
    {
        return String.format("(%+d, %+d)", dx, dy);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        RelativeMove other = (RelativeMove) obj;
        return dx == other.dx && dy == other.dy;
    }

    @Override
    public int hashCode()
    {
        int result = Integer.hashCode(dx);
        result = 31 * result + Integer.hashCode(dy);
        return result;
    }
}
