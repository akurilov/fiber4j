package com.github.akurilov.fiber4j;

import com.github.akurilov.commons.concurrent.AsyncRunnableBase;

import java.util.Queue;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FibersExecutorTask
extends AsyncRunnableBase
implements Runnable {

	private final static Logger LOG = Logger.getLogger(FibersExecutorTask.class.getName());

	private final Queue<Fiber> fibers;
	private final boolean backgroundFlag;

	public FibersExecutorTask(
		final Queue<Fiber> fibers, final boolean backgroundFlag
	) {
		this.fibers = fibers;
		this.backgroundFlag = backgroundFlag;
	}

	@Override
	public final void run() {
		while(isStarted()) {
			if(fibers.size() == 0) {
				try {
					Thread.sleep(1);
				} catch(final InterruptedException e) {
					break;
				}
			} else {
				for(final Fiber nextFiber : fibers) {
					try {
						if(nextFiber.isStarted() || nextFiber.isShutdown()) {
							nextFiber.invoke();
						}
					} catch(final RuntimeException e) {
						throw e; // don't catch the unchecked exceptions
					} catch(final Throwable t) {
						LOG.log(Level.WARNING, "Fiber \"" + nextFiber + "\" failed", t);
					}
					if(backgroundFlag) {
						LockSupport.parkNanos(1);
					}
				}
			}
		}
	}
}
