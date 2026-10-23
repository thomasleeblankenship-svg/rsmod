package net.runelite.client.plugins.microbot.api;

import java.util.Objects;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.MicrobotRuntimeService;

/** Binds the read-service singleton to one runtime installation, with symmetric cleanup. */
public final class MicrobotReadRuntimeService implements MicrobotRuntimeService
{
	private final MicrobotReadServices services;
	private final EventBus eventBus;

	public MicrobotReadRuntimeService(MicrobotReadServices services, EventBus eventBus)
	{
		this.services = Objects.requireNonNull(services);
		this.eventBus = Objects.requireNonNull(eventBus);
	}

	@Override
	public void startUp()
	{
		if (MicrobotReadServices.current() != null)
		{
			throw new IllegalStateException("Microbot read services are already installed");
		}
		MicrobotReadServices.install(services);
		try
		{
			eventBus.register(services);
		}
		catch (RuntimeException exception)
		{
			MicrobotReadServices.uninstall(services);
			throw exception;
		}
	}

	@Override
	public void shutDown()
	{
		eventBus.unregister(services);
		MicrobotReadServices.uninstall(services);
	}
}
