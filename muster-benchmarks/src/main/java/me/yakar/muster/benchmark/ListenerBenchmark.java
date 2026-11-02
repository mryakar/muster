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
package me.yakar.muster.benchmark;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.concurrent.TimeUnit;
import me.yakar.muster.Muster;
import me.yakar.muster.spi.CheckListener;
import me.yakar.muster.spring.MusterAutoConfiguration;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What a check listener adds to {@code muster.check(target)}, with Muster as the starter builds it:
 * none, one that does nothing, and the starter's, which reports each check and rule as a Micrometer
 * observation to a registry whose handler accepts them all.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(5)
public class ListenerBenchmark {

  /** How many rules the input is checked against. */
  @Param({"0", "20"})
  public int rules;

  /** The listener: none, one that does nothing, or the starter's observations. */
  @Param({"none", "noop", "observation"})
  public String listener;

  private ConfigurableApplicationContext context;
  private Muster muster;
  private Order order;

  /** Starts an application with the rules and the listener, as Spring Boot does. */
  @Setup
  public void setUp() {
    var sources =
        switch (listener) {
          case "none" -> new Class<?>[] {Application.class};
          case "noop" -> new Class<?>[] {Application.class, Noop.class};
          case "observation" -> new Class<?>[] {Application.class, Observations.class};
          default -> throw new IllegalArgumentException("unknown listener: " + listener);
        };
    context = Applications.start(rules, sources);
    muster = context.getBean(Muster.class);
    order = new Order(0);
  }

  /** Stops the application. */
  @TearDown
  public void tearDown() {
    context.close();
  }

  /** Checks the input with the rules that bind to its class, told to the listener. */
  @Benchmark
  public void target() {
    muster.check(order);
  }

  /** The application: Muster's auto-configuration. */
  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration(MusterAutoConfiguration.class)
  static class Application {}

  /** A listener that does nothing, which the starter registers with Muster. */
  @Configuration(proxyBeanMethods = false)
  static class Noop {
    @Bean
    CheckListener noopListener() {
      return check -> new CheckListener.Observer() {};
    }
  }

  /** An observation registry that keeps nothing, which the starter observes checks with. */
  @Configuration(proxyBeanMethods = false)
  static class Observations {
    @Bean
    ObservationRegistry observationRegistry() {
      var registry = ObservationRegistry.create();
      registry.observationConfig().observationHandler(new AcceptingHandler());
      return registry;
    }
  }

  /** Accepts every observation, so that each is created and started, and does nothing with it. */
  static final class AcceptingHandler implements ObservationHandler<Observation.Context> {
    @Override
    public boolean supportsContext(Observation.Context context) {
      return true;
    }
  }
}
