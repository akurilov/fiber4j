package com.github.akurilov.fiber4j;

import com.github.akurilov.commons.collection.OptLockArrayBuffer;
import com.github.akurilov.commons.collection.OptLockBuffer;
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
	private final Map<O, OptLockBuffer<T>> buffs;

	public RoundRobinOutputFiber(
		final FibersExecutor executor, final List<O> outputs, final int buffCapacity
	) {
		super(executor);
		this.outputs = outputs;
		this.outputsCount = outputs.size();
		this.buffCapacity = buffCapacity;
		this.buffs = new HashMap<>(this.outputsCount);
		for(int i = 0; i < this.outputsCount; i ++) {
			this.buffs.put(outputs.get(i), new OptLockArrayBuffer<>(buffCapacity));
		}
	}

	private OptLockBuffer<T> selectBuff() {
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
		final OptLockBuffer<T> buff = selectBuff();
		if(buff != null && buff.tryLock()) {
			try {
				return buff.size() < buffCapacity && buff.add(ioTask);
			} finally {
				buff.unlock();
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
		OptLockBuffer<T> buff;
		final int n = to - from;
		if(n > outputsCount) {
			final int nPerOutput = n / outputsCount;
			int nextFrom = from;
			for(int i = 0; i < outputsCount; i ++) {
				buff = selectBuff();
				if(buff != null && buff.tryLock()) {
					try {
						final int m = Math.min(nPerOutput, buffCapacity - buff.size());
						for(final T item : srcBuff.subList(nextFrom, nextFrom + m)) {
							buff.add(item);
						}
						nextFrom += m;
					} finally {
						buff.unlock();
					}
				}
			}
			if(nextFrom < to) {
				buff = selectBuff();
				if(buff != null && buff.tryLock()) {
					try {
						final int m = Math.min(to - nextFrom, buffCapacity - buff.size());
						for(final T item : srcBuff.subList(nextFrom, nextFrom + m)) {
							buff.add(item);
						}
						nextFrom += m;
					} finally {
						buff.unlock();
					}
				}
			}
			return nextFrom - from;
		} else {
			for(int i = from; i < to; i ++) {
				buff = selectBuff();
				if(buff != null && buff.tryLock()) {
					try {
						if(buff.size() < buffCapacity) {
							buff.add(srcBuff.get(i));
						} else {
							return i - from;
						}
					} finally {
						buff.unlock();
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
		final OptLockBuffer<T> buff = buffs.get(output);
		if(buff != null && buff.tryLock()) {
			try {
				int n = buff.size();
				if(n > 0) {
					if(n == 1) {
						if(output.put(buff.get(0))) {
							buff.clear();
						}
					} else {
						n = output.put(buff);
						buff.removeRange(0, n);
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
				buff.unlock();
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
			final OptLockBuffer<T> buff = buffs.get(output);
			if(buff != null) {
				buff.clear();
			}
		}
		buffs.clear();
	}
}
