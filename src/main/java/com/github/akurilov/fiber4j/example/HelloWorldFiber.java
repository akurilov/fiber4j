package com.github.akurilov.fiber4j.example;

import com.github.akurilov.fiber4j.FiberBase;
import com.github.akurilov.fiber4j.FibersExecutor;

import java.io.IOException;

/**
 * Created by andrey on 23.08.17.
 */
public class HelloWorldFiber
extends FiberBase {

	public HelloWorldFiber(final FibersExecutor executor) {
		super(executor);
	}

	@Override
	protected void invokeTimed(long startTimeNanos) {
		System.out.println("Hello world!");
	}

	@Override
	protected void doClose()
	throws IOException {

	}
}
