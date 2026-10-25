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

import java.util.concurrent.ConcurrentMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import me.yakar.muster.internal.rule.ChainPlan.Selection
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.rule.RuleTarget
import spock.lang.Specification

/** The chains of the classes checked so far, as Muster and bindings keep them. */
class ClassChainsSpec extends Specification {

  @Rule(id = 'target.any')
  static class AnyTarget implements BusinessRule<RuleTarget> {
    RuleResult check(RuleTarget input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  def muster = Muster.builder().rules(new AnyTarget()).build()

  def 'reading a bound class takes no lock, even while another class in its bucket is being bound'() {
    given: 'three classes in one bucket of the map, the first two of them bound'
    def (first, second, third) = sameBucket(3)
    def binding = new CountDownLatch(1)
    def release = new CountDownLatch(1)
    def chains = new ClassChains({ type ->
      if (type == third) {
        binding.countDown()
        release.await()
      }
      muster.bind(type, Selection.NONE, CheckMode.FAIL_FAST)
    })
    chains.of(first)
    def bound = chains.of(second)
    def pool = Executors.newFixedThreadPool(2)

    when: 'one thread binds the third class'
    def binder = pool.submit { chains.of(third) }
    assert binding.await(1, TimeUnit.MINUTES)

    and: 'another thread waits on the bucket for the second class, as computeIfAbsent does'
    def map = ClassChains.getDeclaredField('chains').with {
      accessible = true
      get(chains) as ConcurrentMap
    }
    def waiting = Thread.start {
      map.computeIfAbsent(second) {
        throw new AssertionError()
      }
    }
    awaitBlockedInComputeIfAbsent(waiting)

    then: 'the second class is read all the same'
    pool.submit { chains.of(second) }.get(1, TimeUnit.MINUTES).is(bound)

    cleanup:
    release.countDown()
    binder?.get(1, TimeUnit.MINUTES)
    waiting?.join(TimeUnit.MINUTES.toMillis(1))
    pool.shutdownNow()
  }

  /**
   * Waits until a thread is blocked on a lock inside computeIfAbsent. If the classes were not in one
   * bucket, it would finish instead, and the test would not show what it means to.
   */
  private static void awaitBlockedInComputeIfAbsent(Thread thread) {
    def deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(1)
    while (!(thread.state == Thread.State.BLOCKED && thread.stackTrace*.methodName.contains('computeIfAbsent'))) {
      assert thread.alive: 'the classes are not in one bucket'
      assert System.nanoTime() <deadline
      Thread.sleep(1)
    }
  }

  /**
   * Classes whose hashes fall into one bucket of a map of sixteen. It mirrors how ConcurrentHashMap
   * spreads hashes on JDK 21 and 25; the test checks that it still holds.
   */
  private static List<Class<?>> sameBucket(int count) {
    def loader = new GroovyClassLoader(ClassChainsSpec.classLoader)
    def buckets = [:].withDefault { [] }
    for (def i = 0; ; i++) {
      def type = loader.parseClass("class Probe$i implements ${RuleTarget.name} {}")
      def hash = type.hashCode()
      def bucket = buckets[(hash ^ (hash >>> 16)) & 0x7fffffff & 15]
      bucket << type
      if (bucket.size() == count) {
        return bucket
      }
    }
  }
}
