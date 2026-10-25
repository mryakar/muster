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

import java.util.concurrent.TimeUnit;
import me.yakar.muster.Binding;
import me.yakar.muster.Muster;
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
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.stereotype.Service;

/**
 * What the Spring Boot starter adds to a service method: the same rules checked through the
 * starter's advice, for a {@code @Service} method that takes a rule target, and by a bean that
 * calls the same binding by hand. {@code manual} is the baseline: the difference to it is the proxy
 * and the advice. There is no transaction, so that the numbers show Muster.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class StarterBenchmark {

  /** How many rules bind to the input. */
  @Param({"0", "1", "20"})
  public int rules;

  private ConfigurableApplicationContext context;
  private AdvisedService advised;
  private ManualService manual;
  private Order order;

  /** Starts an application with the rules, as Spring Boot does. */
  @Setup
  public void setUp() {
    context =
        new SpringApplicationBuilder(Application.class)
            .web(WebApplicationType.NONE)
            .bannerMode(Banner.Mode.OFF)
            .logStartupInfo(false)
            .properties("logging.level.root=warn")
            .initializers(
                applicationContext ->
                    Rules.first(rules)
                        .forEach(
                            rule ->
                                ((GenericApplicationContext) applicationContext)
                                    .registerBean(ruleClass(rule), () -> rule)))
            .run();
    advised = context.getBean(AdvisedService.class);
    manual = new ManualService(context.getBean(Muster.class).binding(Order.class).build());
    order = new Order(0);
  }

  /** Stops the application. */
  @TearDown
  public void tearDown() {
    context.close();
  }

  /** Calls a service method that the starter checks. */
  @Benchmark
  public void advised() {
    advised.save(order);
  }

  /** Calls a method that checks the same rules by hand. */
  @Benchmark
  public void manual() {
    manual.save(order);
  }

  @SuppressWarnings("unchecked")
  private static Class<Rules.IndexedRule> ruleClass(Rules.IndexedRule rule) {
    return (Class<Rules.IndexedRule>) rule.getClass();
  }

  /** The application: Muster's auto-configuration and a service. */
  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration(MusterAutoConfiguration.class)
  static class Application {

    @Bean
    AdvisedService advisedService() {
      return new AdvisedService();
    }
  }

  /** A service whose rules bind to its input by type, through the starter's advice. */
  @Service
  static class AdvisedService {

    /**
     * Saves an order.
     *
     * @param order the order
     */
    public void save(Order order) {}
  }

  /** A class that checks the rules itself, before it does its work. */
  static final class ManualService {

    private final Binding<Order> binding;

    ManualService(Binding<Order> binding) {
      this.binding = binding;
    }

    void save(Order order) {
      binding.check(order);
    }
  }
}
