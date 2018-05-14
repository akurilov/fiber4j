package com.github.akurilov.fiber4j;

import com.github.akurilov.commons.concurrent.AsyncRunnable;

/**
 * Base interface for all fibers
 */
public interface Fiber
extends AsyncRunnable {

	/**
	 The soft limit for the fiber invocation duration.
	 A fiber implementation should care about its own invocation duration.
	 */
	int TIMEOUT_NANOS = 100_000_000;

	/**
	 * Perform the work
	 */
	void invoke();
}
