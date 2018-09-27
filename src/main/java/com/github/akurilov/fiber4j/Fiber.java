package com.github.akurilov.fiber4j;

import com.github.akurilov.commons.concurrent.AsyncRunnable;

/**
 * Base interface for all fibers
 */
public interface Fiber
extends AsyncRunnable {

	/**
	 The soft limit for the fiber invocation duration.
	 A fiber implementation should use this hint for its own invocation duration.
	 */
	int SOFT_DURATION_LIMIT = 10_000_000;

	/**
	 Any invocation duration exceeding this limit will be logged as warning
	 */
	int WARN_DURATION_LIMIT = 1_000_000_000;

	/**
	 * Perform the work
	 */
	void invoke();
}
