package net.runelite.client.plugins.microbot.accountselector;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import net.runelite.api.GameState;

/**
 * Tracks the requested logout separately from a subsequent manual login.
 * As upstream (chsami/Microbot {@code a2114401}); public here for the mobile auto-login.
 */
public final class AutoLoginSuppression {
    private enum State { ALLOWED, AWAITING_LOGOUT, LOGGED_OUT }

    static final long AWAIT_LOGOUT_MILLIS = AutoLoginSuppressionRequest.PENDING_LOGOUT_MILLIS;
    private final LongSupplier clock;
    private State state = State.ALLOWED;
    private long requestedAt;

    public AutoLoginSuppression() {
        this(() -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    }

    AutoLoginSuppression(LongSupplier clock) {
        this.clock = clock;
    }

    public synchronized void request() {
        expirePendingRequest();
        if (state == State.ALLOWED) {
            requestedAt = clock.getAsLong();
            state = State.AWAITING_LOGOUT;
        }
    }

    public synchronized void renew() {
        expirePendingRequest();
        if (state == State.AWAITING_LOGOUT) {
            requestedAt = clock.getAsLong();
        }
    }

    public synchronized void holdLoggedOut(GameState gameState) {
        if (gameState == GameState.LOGIN_SCREEN) {
            state = State.LOGGED_OUT;
        }
    }

    public synchronized void observe(GameState gameState) {
        expirePendingRequest();
        if (state == State.AWAITING_LOGOUT && gameState == GameState.LOGIN_SCREEN) {
            state = State.LOGGED_OUT;
        } else if (state == State.LOGGED_OUT && gameState == GameState.LOGGED_IN) {
            state = State.ALLOWED;
        }
    }

    public synchronized boolean isSuppressed() {
        // Also expire during polling when no game-state transition has occurred.
        expirePendingRequest();
        return state != State.ALLOWED;
    }

    public synchronized void clear() {
        state = State.ALLOWED;
    }

    private void expirePendingRequest() {
        if (state == State.AWAITING_LOGOUT
                && clock.getAsLong() - requestedAt >= AWAIT_LOGOUT_MILLIS) {
            state = State.ALLOWED;
        }
    }
}
