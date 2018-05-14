package com.github.akurilov.fiber4j;

import com.github.akurilov.commons.concurrent.ContextAwareThreadFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * The fibers executor. It's suggested to use a single/global/shared executor instance per
 * application. By default the background fibers executor is created. The normal coroutines
 * executor with higher scheduling priority may be created using the custom constructor with
 * <i>false</i> argument.
 */
public class FibersExecutor {

	private final static Logger LOG = Logger.getLogger(FibersExecutor.class.getName());

	private final ThreadPoolExecutor executor;
	private final boolean backgroundFlag;
	private final List<FibersExecutorTask> workers = new ArrayList<>();
	private final Queue<Fiber> fibers = new ConcurrentLinkedQueue<>();

	public FibersExecutor() {
		this(true);
	}

	public FibersExecutor(final boolean backgroundFlag) {
		final int svcThreadCount = Runtime.getRuntime().availableProcessors();
		executor = new ThreadPoolExecutor(
			svcThreadCount, svcThreadCount, 0, TimeUnit.DAYS, new ArrayBlockingQueue<>(1),
			new ContextAwareThreadFactory("fibers-executor-", true, null)
		);
		this.backgroundFlag = backgroundFlag;
		for(int i = 0; i < svcThreadCount; i ++) {
			final FibersExecutorTask svcWorkerTask = new FibersExecutorTask(
				fibers, backgroundFlag
			);
			executor.submit(svcWorkerTask);
			workers.add(svcWorkerTask);
			svcWorkerTask.start();
		}
	}

	public void start(final Fiber fiber) {
		fibers.add(fiber);
	}

	public void stop(final Fiber fiber) {
		fibers.remove(fiber);
	}

	public void setThreadCount(final int threadCount) {
		final int newThreadCount = threadCount > 0 ?
			threadCount : Runtime.getRuntime().availableProcessors();
		final int oldThreadCount = executor.getCorePoolSize();
		if(newThreadCount != oldThreadCount) {
			executor.setCorePoolSize(newThreadCount);
			executor.setMaximumPoolSize(newThreadCount);
			if(newThreadCount > oldThreadCount) {
				for(int i = oldThreadCount; i < newThreadCount; i ++) {
					final FibersExecutorTask execTask = new FibersExecutorTask(
						fibers, backgroundFlag
					);
					executor.submit(execTask);
					workers.add(execTask);
					execTask.start();
				}
			} else { // less, remove some active service worker tasks
				try {
					for(int i = oldThreadCount - 1; i >= newThreadCount; i --) {
						workers.remove(i).close();
					}
				} catch (final Exception e) {
					e.printStackTrace(System.err);
				}
			}
		}
	}
}
