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
import java.util.Objects;
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
	private final Map<O, Lock> buffLocks;

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
			this.buffLocks.put(outputs.get(i), new ReentrantLock());
		}
	}

	private O selectOutput() {
		if(outputsCount > 1) {
			return outputs.get((int) (putCounter.getAndIncrement() % outputsCount));
		} else {
			return outputs.get(0);
		}
	}

	@Override
	public final boolean put(final T ioTask)
	throws IOException {

		if(isStopped()) {
			throw new EOFException();
		}

		final O output = selectOutput();
		final CircularBuffer<T> dstBuff = buffs.get(output);
		final Lock dstBuffLock = buffLocks.get(output);

		if(dstBuff != null && dstBuffLock != null && dstBuffLock.tryLock()) {
			try {
				return dstBuff.add(ioTask);
			} finally {
				dstBuffLock.unlock();
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

		O output;
		CircularBuffer<T> dstBuff;
		Lock dstBuffLock;

		final int n = to - from;
		int offset = from;

		if(n > outputsCount) {

			final int nPerOutput = n / outputsCount;
			List<T> items;

			while(offset < to) {

				output = selectOutput();
				dstBuff = buffs.get(output);
				dstBuffLock = buffLocks.get(output);

				if(dstBuff != null && dstBuffLock != null && dstBuffLock.tryLock()) {
					try {
						final int m = Math.min(Math.min(nPerOutput, to - offset), buffCapacity - dstBuff.size());
						items = srcBuff.subList(offset, offset + m);
						if(dstBuff.addAll(items)) {
							offset += m;
						} else {
							throw new AssertionError();
						}
					} finally {
						dstBuffLock.unlock();
					}
				}
			}

			return offset - from;

		} else {

			while(offset < to) {

				output = selectOutput();
				dstBuff = buffs.get(output);
				dstBuffLock = buffLocks.get(output);

				if(dstBuff != null && dstBuffLock != null && dstBuffLock.tryLock()) {
					try {
						if(!dstBuff.add(srcBuff.get(offset))) {
							return offset - from;
						}
					} finally {
						dstBuffLock.unlock();
					}
				} else {
					return offset - from;
				}

				offset ++;
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
		final O output = outputs.get(outputsCount > 1 ? (int) (getCounter.getAndIncrement() % outputsCount) : 0);
		// select the corresponding buffer
		final CircularBuffer<T> srcBuff = buffs.get(output);
		final Lock srcBuffLock = buffLocks.get(output);

		if(srcBuff != null && srcBuffLock != null && srcBuffLock.tryLock()) {
			try {
				int n = srcBuff.size();
				if(n > 0) {
					if(n == 1) {
						if(output.put(srcBuff.get(0))) {
							srcBuff.clear();
						}
					} else {
						n = output.put(srcBuff);
						srcBuff.removeFirst(n);
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
				srcBuffLock.unlock();
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
		outputs
			.stream()
			.map(buffs::get)
			.filter(Objects::nonNull)
			.forEach(CircularBuffer::clear);
		buffs.clear();
		buffLocks.clear();
	}
}
