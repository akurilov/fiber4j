# Introduction

The library supporting the cooperative multitasking. Introduces the
fibers which are useful to execute a lot of periodic/long tasks using
a fixed count of the threads.

In the context of the library, the fiber is a stoppable task which
executes for some very short time avoiding any blocks multiple times
([in the reentrant way](https://en.wikipedia.org/wiki/Microthread)).
A basic fiber instance may be executing by several different threads
at any moment of time so it should have thread-safe user code. To
implement the protected, thread-safe fiber the special
`ExclusiveFiberBase` class is provided. An exclusive fiber is
being executed by only one thread at any moment of time.

The fibers are being executed by the fibers executor. Any fiber executor
has the thread-safe registry. The fibers executor threads iterate the
fibers registry and invoke the fibers sequentially.
As far as fibers executor is multithreaded the fibers are being
executed concurrently also.

# Usage

## Gradle

```groovy
compile group: 'com.github.akurilov', name: 'fiber4j', version: '1.1.0'
```

## Implementing Basic Fiber

To implement the simplest fiber one should extend the `FiberBase` class:

```java
package com.github.akurilov.fiber4j.example;

import com.github.akurilov.fiber4j.FiberBase;
import com.github.akurilov.fiber4j.FibersExecutor;

public class HelloWorldFiber
extends FiberBase {

    public HelloWorldFiber(final FibersExecutor fibersExecutor) {
        super(fibersExecutor);
    }

    @Override
    protected void invokeTimed(final long startTimeNanos) {
        System.out.println("Hello world");
    }

    @Override
    protected void doClose()
    throws IOException {

    }
}
```

The method `invokeTimed` does the useful work. The example code below
utilizes that fiber:

```java
package com.github.akurilov.fiber4j.example;

import com.github.akurilov.fiber4j.Fiber;
import com.github.akurilov.fiber4j.FibersProcessor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(final String... args)
    throws InterruptedException, IOException {

        final FibersExecutor fibersExecutor = new FibersExecutor();
        final Fiber helloFiber = new HelloWorldFiber(fibersExecutor);
        helloFiber.start();
        TimeUnit.SECONDS.sleep(10);
        helloFiber.close();
    }
}
```

### invokeTimed notes

The code executed in the `invokeTimed` method should follow the rules:
* Do not block if possible
* Take care of own thread safety
* Do not exceed the timeout (`Fiber.TIMEOUT_NANOS`)

The invoked code should take the responsibility on the time of its
execution. Example:

```java
    @Override
    protected void invokeTimed(long startTimeNanos) {
        for(int i = workBegin; i < workEnd; i ++) {
            doSomeUsefulWork(i);
            // yes, I know that the statement below may invoke Satan
            // but for simplicity it doesn't expect the negative result
            if(System.nanoTime() - startTimeNanos > TIMEOUT_NANOS) {
                break;
            }
        }
    }
```

## Implementing Exclusive Fiber

An exclusive fiber is restricted by a single thread. It allows:
* Consume less CPU resources (useful for "background" tasks)
* Don't care of thread safety

```java
package com.github.akurilov.fiber4j.example;

...
import com.github.akurilov.fiber4j.ExclusiveFiberBase;

public class HelloWorldExclusiveFiber
extends ExclusiveFiberBase {

    ...

    @Override
    protected void invokeTimedExclusively(long startTimeNanos) {
        System.out.println("Hello world!");
    }
    ...
```

## Other Fiber Implementations

There are some other fiber implementations included into the library
 for the user reference. These fibers are used in the
[Mongoose](https://github.com/emc-mongoose/mongoose-base) project widely
and proved the fibers approach efficiency.
