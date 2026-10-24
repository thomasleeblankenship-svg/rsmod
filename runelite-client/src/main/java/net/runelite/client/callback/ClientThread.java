/*
 * Copyright (c) 2018 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.callback;

import com.google.inject.Inject;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

@Singleton
@Slf4j
public class ClientThread
{
	private final ConcurrentLinkedQueue<BooleanSupplier> invokes = new ConcurrentLinkedQueue<>();
	private final ConcurrentLinkedQueue<BooleanSupplier> invokesAtTickEnd = new ConcurrentLinkedQueue<>();

	/**
	 * Default wait for {@link #runOnClientThreadOptional(Callable)}, matching Microbot
	 * {@code e01e0d77d79b354472a5737b3aaab2449eb034fc}.
	 */
	public static final long DEFAULT_CLIENT_THREAD_TIMEOUT_MILLIS = 10000;
	private static final long TIMEOUT_WARNING_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);

	private final AtomicLong lastTimeoutWarningNanos = new AtomicLong();

	@Inject
	private Client client;

	/**
	 * @return true when the calling thread is the RuneLite client thread
	 */
	public boolean isClientThread()
	{
		return client.isClientThread();
	}

	/**
	 * As Microbot's fork of this class: runs {@code supplier} on the client thread and returns its
	 * result (inline when already on it). Waits at most 10 s; a timeout or interrupt throws, as upstream.
	 */
	public <T> T invoke(java.util.function.Supplier<T> supplier)
	{
		if (client.isClientThread())
		{
			return supplier.get();
		}
		java.util.concurrent.CompletableFuture<T> result = new java.util.concurrent.CompletableFuture<>();
		invoke(() ->
		{
			try
			{
				result.complete(supplier.get());
			}
			catch (Throwable t)
			{
				result.completeExceptionally(t);
			}
		});
		try
		{
			return result.get(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted waiting for client thread", e);
		}
		catch (TimeoutException e)
		{
			throw new RuntimeException("Timed out waiting for client thread", e);
		}
		catch (ExecutionException e)
		{
			throw new RuntimeException("Client thread task failed", e);
		}
	}

	/** One worker for {@link #runOnSeperateThread}, created on first use; never holds the VM open. */
	private java.util.concurrent.ExecutorService separateThread;
	private java.util.concurrent.Future<?> separateTask;

	/**
	 * As Microbot's fork of this class (spelling kept): runs {@code method} off the client thread on
	 * one shared worker, unless the previous such task is still running, in which case it is
	 * skipped. Exceptions are logged, not thrown.
	 */
	public synchronized void runOnSeperateThread(Callable<?> method)
	{
		if (separateTask != null && !separateTask.isDone())
		{
			return;
		}
		if (separateThread == null)
		{
			separateThread = java.util.concurrent.Executors.newSingleThreadExecutor(r ->
			{
				Thread thread = new Thread(r, "client-separate");
				thread.setDaemon(true);
				return thread;
			});
		}
		separateTask = separateThread.submit(() ->
		{
			try
			{
				method.call();
			}
			catch (Exception e)
			{
				log.error("Exception in separate-thread task", e);
			}
		});
	}

	/**
	 * Runs {@code method} on the client thread and waits for its result, as Microbot's fork of
	 * this class does. On the client thread it runs inline, so the client thread never waits on
	 * itself. Off the client thread the caller blocks for at most the default timeout.
	 *
	 * @return the result, or empty on a null result, exception, timeout or interrupt
	 */
	public <T> Optional<T> runOnClientThreadOptional(Callable<T> method)
	{
		return runOnClientThreadOptional(method, DEFAULT_CLIENT_THREAD_TIMEOUT_MILLIS);
	}

	/**
	 * Same as {@link #runOnClientThreadOptional(Callable)} with an explicit timeout. A timed-out
	 * or interrupted task is cancelled, so it never runs later on the client thread with stale
	 * intent. An interrupt restores the caller's interrupt flag.
	 */
	public <T> Optional<T> runOnClientThreadOptional(Callable<T> method, long timeoutMillis)
	{
		if (client.isClientThread())
		{
			try
			{
				return Optional.ofNullable(method.call());
			}
			catch (Exception e)
			{
				log.error("Exception in client thread execution", e);
				return Optional.empty();
			}
		}

		final FutureTask<T> task = new FutureTask<>(method);
		invoke(task);
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
			logClientThreadTimeout(timeoutMillis);
			return Optional.empty();
		}
		catch (ExecutionException e)
		{
			log.error("Exception during client thread task execution", e.getCause());
			return Optional.empty();
		}
	}

	private void logClientThreadTimeout(long timeoutMillis)
	{
		long now = System.nanoTime();
		long last = lastTimeoutWarningNanos.get();
		if ((last == 0 || now - last >= TIMEOUT_WARNING_INTERVAL_NANOS) && lastTimeoutWarningNanos.compareAndSet(last, now))
		{
			log.warn("Client thread did not run task within {}ms (caller thread {}); further timeouts suppressed for {}s",
				timeoutMillis, Thread.currentThread().getName(), TimeUnit.NANOSECONDS.toSeconds(TIMEOUT_WARNING_INTERVAL_NANOS));
		}
		else
		{
			log.debug("Client thread did not run task within {}ms (caller thread {})", timeoutMillis, Thread.currentThread().getName());
		}
	}

	public void invoke(Runnable r)
	{
		invoke(() ->
		{
			r.run();
			return true;
		});
	}

	/**
	 * Will run r on the game thread, at an unspecified point in the future.
	 * If r returns false, r will be ran again, at a later point
	 */
	public void invoke(BooleanSupplier r)
	{
		if (client.isClientThread())
		{
			if (!r.getAsBoolean())
			{
				invokes.add(r);
			}
			return;
		}

		invokeLater(r);
	}

	/**
	 * Will run r on the game thread after this method returns
	 * If r returns false, r will be ran again, at a later point
	 */
	public void invokeLater(Runnable r)
	{
		invokeLater(() ->
		{
			r.run();
			return true;
		});
	}

	public void invokeLater(BooleanSupplier r)
	{
		invokes.add(r);
	}

	public void invokeAtTickEnd(Runnable r)
	{
		invokesAtTickEnd.add(() ->
		{
			r.run();
			return true;
		});
	}

	void invoke()
	{
		invokeList(invokes);
	}

	void invokeTickEnd()
	{
		invokeList(invokesAtTickEnd);
	}

	private void invokeList(ConcurrentLinkedQueue<BooleanSupplier> invokes)
	{
		assert client.isClientThread();
		Iterator<BooleanSupplier> ir = invokes.iterator();
		while (ir.hasNext())
		{
			BooleanSupplier r = ir.next();
			boolean remove = true;
			try
			{
				remove = r.getAsBoolean();
			}
			catch (ThreadDeath d)
			{
				throw d;
			}
			catch (Throwable e)
			{
				log.error("Exception in invoke", e);
			}
			if (remove)
			{
				ir.remove();
			}
			else
			{
				log.trace("Deferring task {}", r);
			}
		}
	}
}
