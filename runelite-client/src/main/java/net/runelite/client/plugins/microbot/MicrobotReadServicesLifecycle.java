package net.runelite.client.plugins.microbot;

import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.api.MicrobotReadServices;

import java.util.Objects;

/**
 * Binds the read-only API provider to one {@link MicrobotRuntime} install, following the
 * lifecycle {@link MicrobotReadServices} documents: subscribe and install on start, uninstall and
 * unsubscribe on stop. Pass it in the services list of {@link MicrobotRuntime#install}.
 * <p>
 * Mobile-only; upstream registers the same handlers from its always-on {@code MicrobotPlugin}.
 */
public final class MicrobotReadServicesLifecycle implements MicrobotRuntimeService {
    private final EventBus eventBus;
    private final MicrobotReadServices services;

    public MicrobotReadServicesLifecycle(EventBus eventBus, MicrobotReadServices services) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.services = Objects.requireNonNull(services, "services");
    }

    @Override
    public void startUp() {
        eventBus.register(services);
        MicrobotReadServices.install(services);
    }

    @Override
    public void shutDown() {
        MicrobotReadServices.uninstall(services);
        eventBus.unregister(services);
    }
}
