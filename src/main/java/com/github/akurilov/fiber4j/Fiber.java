package com.github.akurilov.fiber4j;

import com.github.akurilov.commons.concurrent.AsyncRunnable;

/**
 * Base interface for all fibers
 */
public interface Fiber
extends AsyncRunnable {

	/**
	 * The soft limit for the fiber invocation duration.
	 * A fiber implementation should use this hint for its own invocation duration.
	 */
	long SOFT_DURATION_LIMIT_NANOS = 10_000_000L;

	/**
	 * Any invocation duration exceeding this limit will be logged with fine level
	 */
	long DEBUG_DURATION_LIMIT_NANOS = 1_000_000_000L;

	/**
	 * Any invocation duration exceeding this limit will be logged as warning
	 */
	long WARN_DURATION_LIMIT_NANOS = 10_000_000_000L;

	/**
	 * Perform the work
	 */
	void invoke();
}
