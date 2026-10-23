/*
 * Adapted from chsami/Microbot e01e0d77d79b354472a5737b3aaab2449eb034fc,
 * runelite-client/src/main/java/net/runelite/client/callback/ClientThread.java
 * (runOnClientThreadOptional). BSD 2-Clause License; see LICENSE.
 */
package net.runelite.client.plugins.microbot.api;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;

/**
 * Runs a read on RuneLite's client thread and returns its result to the caller.
 * <p>
 * RuneMobile's {@link ClientThread} does not carry the Microbot fork's
 * {@code runOnClientThreadOptional}; this class reproduces its semantics on top
 * of the real {@link ClientThread#invoke(Runnable)}: run inline when already on
 * the client thread, otherwise enqueue and wait up to a timeout. A timeout,
 * interrupt or exception yields {@link Optional#empty()} and never blocks the
 * client thread itself.
 */
@Slf4j
public final class ClientThreadCalls
{
	/** Upstream default ({@code ClientThread.clientThreadTimeoutMillis}). */
	public static final long DEFAULT_TIMEOUT_MILLIS = 10_000;

	private final Client client;
	private final ClientThread clientThread;
	private final long timeoutMillis;

	public ClientThreadCalls(Client client, ClientThread clientThread)
	{
		this(client, clientThread, DEFAULT_TIMEOUT_MILLIS);
	}

	public ClientThreadCalls(Client client, ClientThread clientThread, long timeoutMillis)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.timeoutMillis = timeoutMillis;
	}

	public boolean isClientThread()
	{
		return client.isClientThread();
	}

	/**
	 * Runs {@code method} on the client thread and returns its non-null result.
	 */
	public <T> Optional<T> call(Callable<T> method)
	{
		if (client.isClientThread())
		{
			try
			{
				return Optional.ofNullable(method.call());
			}
			catch (Exception e)
			{
				log.warn("Exception in client thread read", e);
				return Optional.empty();
			}
		}

		final FutureTask<T> task = new FutureTask<>(method);
		clientThread.invoke(task);
		try
		{
			return Optional.ofNullable(task.get(timeoutMillis, TimeUnit.MILLISECONDS));
		}
		catch (InterruptedException e)
		{
			task.cancel(false);
			Thread.currentThread().interrupt();
			return Optional.empty();
		}
		catch (TimeoutException e)
		{
			task.cancel(false);
			log.debug("Client thread did not run read within {}ms", timeoutMillis);
			return Optional.empty();
		}
		catch (ExecutionException e)
		{
			log.warn("Exception in client thread read", e.getCause());
			return Optional.empty();
		}
	}
}
