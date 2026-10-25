/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.yakar.muster

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Runs a task on many threads at once, for tests of shared state. */
final class Concurrency {

  private Concurrency() {
  }

  /**
   * Runs a task on each of the threads, released together once every one is ready, and returns
   * the results by thread number. A deadlock fails the test instead of hanging it, once the whole
   * run has taken a minute.
   */
  static <T> List<T> concurrently(int threads = 16, Closure<T> task) {
    def ready = new CountDownLatch(threads)
    def start = new CountDownLatch(1)
    def pool = Executors.newFixedThreadPool(threads)
    try {
      def results = (0..<threads).collect { n ->
        pool.submit({
          ready.countDown()
          start.await()
          task(n)
        } as Callable<T>)
      }
      def deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(1)
      assert ready.await(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)
      start.countDown()
      results.collect { it.get(deadline - System.nanoTime(), TimeUnit.NANOSECONDS) }
    } finally {
      pool.shutdownNow()
    }
  }
}
