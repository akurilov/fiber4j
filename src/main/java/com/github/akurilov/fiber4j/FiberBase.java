package com.github.akurilov.fiber4j;

import com.github.akurilov.commons.concurrent.AsyncRunnableBase;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The base class for all fibers.
 */
public abstract class FiberBase
extends AsyncRunnableBase
implements Fiber {

	private static final Logger LOG = Logger.getLogger(FiberBase.class.getSimpleName());

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
		long t = System.nanoTime();
		invokeTimed(t);
		t = System.nanoTime() - t;
		if(t > DEBUG_DURATION_LIMIT_NANOS) {
			LOG.log(
				t > WARN_DURATION_LIMIT_NANOS ? Level.WARNING : Level.FINE,
				"Fiber \"" + this + "\" invocation duration (" + TimeUnit.NANOSECONDS.toMillis(t) + "[ms]) exceeds "
					+ "the limit (" + TimeUnit.NANOSECONDS.toMillis(SOFT_DURATION_LIMIT_NANOS) + "[ms])"
			);
		}
	}

	/**
	 * The method implementation should use the start time to check its own duration in order to not
	 * to exceed the invocation time limit
	 * @param startTimeNanos the time when the invocation started
	 */
	protected abstract void invokeTimed(final long startTimeNanos);

	@Override
	protected void doStop() {
		executor.stop(this);
	}
}
