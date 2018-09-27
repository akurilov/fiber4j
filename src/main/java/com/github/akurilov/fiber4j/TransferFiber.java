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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The <i>exclusive</i> fiber implementation which tries to transfer the items from the given input to the given output.
 * The items got from the input which may not be transferred to the output w/o blocking are stored to the deferred tasks buffer.
 */
public class TransferFiber<T>
extends ExclusiveFiberBase
implements Fiber {

	private static final Logger LOG = Logger.getLogger(TransferFiber.class.getName());

	private final Input<T> input;
	private final Output<T> output;
	private final CircularBuffer<T> itemsBuff;
	private final int capacity;

	private int n;

	public TransferFiber(
		final FibersExecutor executor, final Input<T> input, final Output<T> output, final int capacity
	) {
		this(executor, new CircularArrayBuffer<>(capacity), input, output);
	}

	private TransferFiber(
		final FibersExecutor executor, final CircularBuffer<T> itemsBuff, final Input<T> input, final Output<T> output
	) {
		super(executor);
		this.input = input;
		this.output = output;
		this.itemsBuff = itemsBuff;
		this.capacity = itemsBuff.capacity();
	}

	@Override
	protected final void invokeTimedExclusively(final long startTimeNanos) {
		try {

			input.get(itemsBuff, capacity - itemsBuff.size());

			n = itemsBuff.size();

			if(n > 0) {
				if(1 == n) {
					final T item = itemsBuff.get(0);
					if(output.put(item)) {
						itemsBuff.clear();
					}
				} else {
					n = output.put(itemsBuff, 0, Math.min(n, capacity));
					itemsBuff.removeFirst(n);
				}
			}

		} catch(final NoSuchObjectException | ConnectException ignored) {
		} catch(final EOFException e) {
			try {
				close();
			} catch(final IOException ee) {
				LOG.log(Level.WARNING, "Failed to close self after EOF", ee);
			}
		} catch(final RemoteException e) {
			final Throwable cause = e.getCause();
			if(cause instanceof EOFException) {
				try {
					close();
				} catch(final IOException ee) {
					LOG.log(Level.WARNING, "Failed to close self after EOF", ee);
				}
			} else {
				LOG.log(Level.WARNING, "Failure", e);
			}
		} catch(final IOException e) {
			LOG.log(Level.WARNING, "Failure", e);
		}
	}

	@Override
	protected final void doClose()
	throws IOException {
		itemsBuff.clear();
	}
}
