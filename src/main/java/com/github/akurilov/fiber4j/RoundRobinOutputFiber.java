package com.github.akurilov.fiber4j;

import com.github.akurilov.commons.collection.CircularArrayBuffer;
import com.github.akurilov.commons.collection.CircularBuffer;
import com.github.akurilov.commons.io.Input;
import com.github.akurilov.commons.io.Output;

import java.io.EOFException;
import java.io.IOException;
import java.rmi.ConnectException;
import java.rmi.NoSuchObjectException;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The fiber implementation which acts like round robin output scattering the objects among the wrapped outputs.
 */
public final class RoundRobinOutputFiber<T, O extends Output<T>>
extends FiberBase
implements OutputFiber<T> {

	private static final Logger LOG = Logger.getLogger(RoundRobinOutputFiber.class.getName());
	
	private final List<O> outputs;
	private final int outputsCount;
	private final AtomicLong putCounter = new AtomicLong(0);
	private final AtomicLong getCounter = new AtomicLong(0);
	private final int buffCapacity;
	private final Map<O, CircularBuffer<T>> buffs;
	private final Map<CircularBuffer<T>, Lock> buffLocks;

	public RoundRobinOutputFiber(
		final FibersExecutor executor, final List<O> outputs, final int buffCapacity
	) {
		super(executor);
		this.outputs = outputs;
		this.outputsCount = outputs.size();
		this.buffCapacity = buffCapacity;
		this.buffs = new HashMap<>(this.outputsCount);
		this.buffLocks = new HashMap<>(this.outputsCount);
		for(int i = 0; i < this.outputsCount; i ++) {
			final CircularBuffer<T> buff = new CircularArrayBuffer<>(buffCapacity);
			this.buffs.put(outputs.get(i), buff);
			this.buffLocks.put(buff, new ReentrantLock());
		}
	}

	private CircularBuffer<T> selectBuff() {
		if(outputsCount > 1) {
			return buffs.get(outputs.get((int) (putCounter.getAndIncrement() % outputsCount)));
		} else {
			return buffs.get(outputs.get(0));
		}
	}

	@Override
	public final boolean put(final T ioTask)
	throws IOException {
		if(isStopped()) {
			throw new EOFException();
		}
		final CircularBuffer<T> buff = selectBuff();
		final Lock buffLock = buffLocks.get(buff);
		if(buff != null && buffLock.tryLock()) {
			try {
				return buff.size() < buffCapacity && buff.add(ioTask);
			} finally {
				buffLock.unlock();
			}
		} else {
			return false;
		}
	}

	@Override
	public final int put(final List<T> srcBuff, final int from, final int to)
	throws IOException {
		if(isStopped()) {
			throw new EOFException();
		}
		CircularBuffer<T> buff;
		Lock buffLock;
		final int n = to - from;
		if(n > outputsCount) {
			final int nPerOutput = n / outputsCount;
			int nextFrom = from;
			for(int i = 0; i < outputsCount; i ++) {
				buff = selectBuff();
				buffLock = buffLocks.get(buff);
				if(buff != null && buffLock.tryLock()) {
					try {
						final int m = Math.min(nPerOutput, buffCapacity - buff.size());
						for(final T item : srcBuff.subList(nextFrom, nextFrom + m)) {
							buff.add(item);
						}
						nextFrom += m;
					} finally {
						buffLock.unlock();
					}
				}
			}
			if(nextFrom < to) {
				buff = selectBuff();
				buffLock = buffLocks.get(buff);
				if(buff != null && buffLock.tryLock()) {
					try {
						final int m = Math.min(to - nextFrom, buffCapacity - buff.size());
						for(final T item : srcBuff.subList(nextFrom, nextFrom + m)) {
							buff.add(item);
						}
						nextFrom += m;
					} finally {
						buffLock.unlock();
					}
				}
			}
			return nextFrom - from;
		} else {
			for(int i = from; i < to; i ++) {
				buff = selectBuff();
				buffLock = buffLocks.get(buff);
				if(buff != null && buffLock.tryLock()) {
					try {
						if(buff.size() < buffCapacity) {
							buff.add(srcBuff.get(i));
						} else {
							return i - from;
						}
					} finally {
						buffLock.unlock();
					}
				} else {
					return i - from;
				}
			}
			return to - from;
		}
	}

	@Override
	public final int put(final List<T> buffer)
	throws IOException {
		return put(buffer, 0, buffer.size());
	}

	@Override
	protected final void invokeTimed(final long startTimeNanos) {
		// select the output using RR
		final O output = outputs.get(
			outputsCount > 1 ? (int) (getCounter.getAndIncrement() % outputsCount) : 0
		);
		// select the corresponding buffer
		final CircularBuffer<T> buff = buffs.get(output);
		final Lock buffLock = buffLocks.get(buff);
		if(buff != null && buffLock.tryLock()) {
			try {
				int n = buff.size();
				if(n > 0) {
					if(n == 1) {
						if(output.put(buff.get(0))) {
							buff.clear();
						}
					} else {
						n = output.put(buff);
						buff.removeFirst(n);
					}
				}
			} catch(final EOFException | NoSuchObjectException | ConnectException ignored) {
			} catch(final RemoteException e) {
				final Throwable cause = e.getCause();
				if(!(cause instanceof EOFException)) {
					LOG.log(Level.WARNING, "Invocation failure", e);
				}
			} catch(final Throwable t) {
				LOG.log(Level.WARNING, "Invocation failure", t);
			} finally {
				buffLock.unlock();
			}
		}
	}

	@Override
	public final Input<T> getInput() {
		throw new AssertionError("Shouldn't be invoked");
	}

	@Override
	protected final void doClose()
	throws IOException {
		for(final O output : outputs) {
			final CircularBuffer<T> buff = buffs.get(output);
			if(buff != null) {
				buff.clear();
			}
		}
		buffs.clear();
		buffLocks.clear();
	}
}
