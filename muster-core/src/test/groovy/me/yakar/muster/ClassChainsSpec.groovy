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

import static me.yakar.muster.Concurrency.concurrently

import java.util.concurrent.ConcurrentMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import me.yakar.muster.internal.rule.ChainPlan.Selection
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.rule.RuleTarget

/** The chains of the classes checked so far, as Muster and bindings keep them. */
class ClassChainsSpec extends EngineSpecification {

  static record Target() implements RuleTarget {}

  @Rule(id = 'target.any')
  static class AnyTarget implements BusinessRule<RuleTarget> {
    RuleResult check(RuleTarget input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  def muster = Muster.builder().rules(new AnyTarget()).build()

  def 'reading a bound class takes no lock, even while its bucket of the map is locked'() {
    given: 'three classes in one bucket of the map, the first two of them bound'
    def (first, second, third) = sameBucket(3)
    def chains = new ClassChains({ type -> muster.bind(type, Selection.NONE, CheckMode.FAIL_FAST) })
    chains.of(first)
    def bound = chains.of(second)
    def locked = new CountDownLatch(1)
    def release = new CountDownLatch(1)
    def pool = Executors.newFixedThreadPool(2)

    when: 'one thread holds the lock of the bucket, as computeIfAbsent does while it computes'
    def map = ClassChains.getDeclaredField('chains').with {
      accessible = true
      get(chains) as ConcurrentMap
    }
    def locking = pool.submit {
      map.computeIfAbsent(third) {
        locked.countDown()
        release.await()
        chains.of(first)
      }
    }
    assert locked.await(1, TimeUnit.MINUTES)

    then: 'the second class is read all the same'
    pool.submit { chains.of(second) }.get(1, TimeUnit.MINUTES).is(bound)

    cleanup:
    release.countDown()
    locking?.get(1, TimeUnit.MINUTES)
    pool.shutdownNow()
  }

  def 'binding a class takes no lock: a class of the same bucket is bound while another is being bound'() {
    given:
    def (slow, quick) = sameBucket(2)
    def binding = new CountDownLatch(1)
    def release = new CountDownLatch(1)
    def chains = new ClassChains({ type ->
      if (type == slow) {
        binding.countDown()
        release.await()
      }
      muster.bind(type, Selection.NONE, CheckMode.FAIL_FAST)
    })
    def pool = Executors.newFixedThreadPool(2)

    when:
    def binder = pool.submit { chains.of(slow) }
    assert binding.await(1, TimeUnit.MINUTES)

    then:
    pool.submit { chains.of(quick) }.get(1, TimeUnit.MINUTES) != null

    cleanup:
    release.countDown()
    binder?.get(1, TimeUnit.MINUTES)
    pool.shutdownNow()
  }

  def 'threads that bind one class at once all use the chain put first, whose plan alone is logged'() {
    given:
    log.level = Level.FINE
    def both = new CountDownLatch(2)
    def chains = new ClassChains({ type ->
      both.countDown()
      assert both.await(1, TimeUnit.MINUTES)
      muster.bind(type, Selection.NONE, CheckMode.FAIL_FAST)
    })

    when:
    def seen = concurrently(2) { chains.of(Target) }

    then:
    seen[0].is(seen[1])
    chains.of(Target).is(seen[0])
    logged*.message == ["Rules bound to ${Target.typeName}: [target.any]".toString()]
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
