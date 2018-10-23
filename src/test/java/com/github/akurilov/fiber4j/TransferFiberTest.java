package com.github.akurilov.fiber4j;

import com.github.akurilov.commons.collection.CircularArrayBuffer;
import com.github.akurilov.commons.collection.CircularBuffer;
import com.github.akurilov.commons.io.Input;
import com.github.akurilov.commons.io.Output;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.EOFException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public class TransferFiberTest {

	private static final class CountingInput
	implements Input<Object> {

		private final LongAdder counter;

		private CountingInput(final LongAdder counter) {
			this.counter = counter;
		}

		@Override
		public Object get()
		throws EOFException, IOException {
			counter.increment();
			return new Object();
		}

		@Override
		public int get(final List<Object> buffer, final int limit)
		throws IOException {
			final int n = (int) (Math.random() * limit);
			for(int i = 0; i < n; i ++) {
				buffer.add(new Object());
			}
			counter.add(n);
			return n;
		}

		@Override
		public long skip(final long count)
		throws IOException {
			return 0;
		}

		@Override
		public void reset()
		throws IOException {
		}

		@Override
		public void close()
		throws IOException {
		}
	}

	private static final class CountingOutput
	implements Output<Object> {

		private final LongAdder counter;

		private CountingOutput(final LongAdder counter) {
			this.counter = counter;
		}

		@Override
		public boolean put(final Object item)
		throws IOException {
			counter.increment();
			return true;
		}

		@Override
		public int put(final List<Object> buffer, final int from, final int to)
		throws IOException {
			final int n = (int) (Math.random() * (to - from));
			counter.add(n);
			return n;
		}

		@Override
		public int put(final List<Object> buffer)
		throws IOException {
			final int n = (int) (Math.random() * buffer.size());
			counter.add(n);
			return n;
		}

		@Override
		public Input<Object> getInput()
		throws IOException {
			return null;
		}

		@Override
		public void close()
		throws IOException {
		}
	}

	@Test
	public final void test()
	throws Exception {
		final FibersExecutor fibersExecutor = new FibersExecutor();
		final int buffSize = 1234;
		final LongAdder inputCounter = new LongAdder();
		final Input<Object> input = new CountingInput(inputCounter);
		final LongAdder outputCounter = new LongAdder();
		final Output<Object> output = new CountingOutput(outputCounter);
		final CircularBuffer<Object> exchangeBuff = new CircularArrayBuffer<>(buffSize);
		final Fiber transferFiber = new TransferFiber<>(fibersExecutor, exchangeBuff, input, output);
		transferFiber.start();
		TimeUnit.SECONDS.sleep(10);
		transferFiber.stop();
		assertNotEquals(0, inputCounter.sum() - outputCounter.sum());
		while(!exchangeBuff.isEmpty()) {
			transferFiber.invoke();
		}
		assertEquals(0, inputCounter.sum() - outputCounter.sum());
	}
}
