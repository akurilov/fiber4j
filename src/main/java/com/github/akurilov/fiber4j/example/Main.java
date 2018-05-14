package com.github.akurilov.fiber4j.example;

import com.github.akurilov.fiber4j.Fiber;
import com.github.akurilov.fiber4j.FibersExecutor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Created by andrey on 23.08.17.
 */
public class Main {

	public static void main(final String... args)
	throws InterruptedException, IOException {

		final FibersExecutor executor = new FibersExecutor();
		final Fiber helloFiber = new HelloWorldFiber(executor);
		helloFiber.start();
		TimeUnit.SECONDS.sleep(10);
		helloFiber.close();
	}
}
