package net.runelite.client.plugins.microbot;

/**
 * A service whose lifetime is bound to one {@link MicrobotRuntime} install, such as the
 * read-only entity caches the API stage provides.
 * <p>
 * Mobile-only; upstream Microbot has no equivalent because Guice owns its singletons for the life
 * of the process. Here the runtime starts each service after binding {@link Microbot}'s client
 * services and stops it, in reverse order, before unbinding them, so a service never sees a
 * half-installed or torn-down runtime.
 */
public interface MicrobotRuntimeService {

    /**
     * Called once per install, after {@link Microbot}'s client services are bound. Register event
     * subscribers here. Must not block; it may run on the client thread.
     *
     * @throws Exception to abort the install; services already started are stopped again
     */
    void startUp() throws Exception;

    /**
     * Called once per install, before {@link Microbot}'s client services are cleared. Unregister
     * subscribers and drop every cached entity: the next install may be a different login.
     * Exceptions are logged and do not stop other services from shutting down.
     */
    void shutDown() throws Exception;
}
