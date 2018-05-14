package com.github.akurilov.fiber4j;

import com.github.akurilov.commons.concurrent.AsyncRunnableBase;

/**
 * The base class for all fibers.
 */
public abstract class FiberBase
extends AsyncRunnableBase
implements Fiber {

	private final FibersExecutor executor;

	protected FiberBase(final FibersExecutor executor) {
		this.executor = executor;
	}

	@Override
	protected void doStart() {
		executor.start(this);
	}

	/**
	 * Decorates the invocation method with timing.
	 */
	@Override
	public final void invoke() {
		invokeTimed(System.nanoTime());
	}

	/**
	 * The method implementation should use the start time to check its own duration in order to not
	 * to exceed the invocation time limit (100 ms)
	 * @param startTimeNanos the time when the invocation started
	 */
	protected abstract void invokeTimed(final long startTimeNanos);

	@Override
	protected void doStop() {
		executor.stop(this);
	}
}
