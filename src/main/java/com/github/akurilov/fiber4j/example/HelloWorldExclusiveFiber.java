package com.github.akurilov.fiber4j.example;

import com.github.akurilov.fiber4j.FibersExecutor;
import com.github.akurilov.fiber4j.ExclusiveFiberBase;

import java.io.IOException;

/**
 * Created by andrey on 23.08.17.
 */
public class HelloWorldExclusiveFiber
extends ExclusiveFiberBase {

	public HelloWorldExclusiveFiber(final FibersExecutor executor) {
		super(executor);
	}

	@Override
	protected void invokeTimedExclusively(long startTimeNanos) {
		System.out.println("Hello world!");
	}

	@Override
	protected void doClose()
	throws IOException {

	}
}
