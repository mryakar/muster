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

import java.io.Serial;
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
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * What the Spring Boot starter adds to a call of a service method: the method checked by the
 * starter's advice against a method that checks the same binding by hand, alone or next to method
 * security and a transaction, whose advice the starter orders itself after.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(5)
public class StarterBenchmark {

  /** How many rules bind to the input. */
  @Param({"0", "20"})
  public int rules;

  /** The other advice on the method: none, method security, a transaction, or both. */
  @Param({"none", "security", "transactional", "both"})
  public String advice;

  private ConfigurableApplicationContext context;
  private AdvisedService advised;
  private ManualService manual;
  private Order order;

  /** Starts an application with the rules, as Spring Boot does, and signs a user in. */
  @Setup
  public void setUp() {
    var sources =
        switch (advice) {
          case "none" -> new Class<?>[] {Application.class};
          case "security" -> new Class<?>[] {Application.class, Security.class};
          case "transactional" -> new Class<?>[] {Application.class, Transactions.class};
          case "both" -> new Class<?>[] {Application.class, Security.class, Transactions.class};
          default -> throw new IllegalArgumentException("unknown advice: " + advice);
        };
    context = Applications.start(rules, sources);
    advised = context.getBean(AdvisedService.class);
    manual = context.getBean(ManualService.class);
    order = new Order(0);
    // One context for every thread: JMH may run the benchmark on another thread than the setup.
    SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_GLOBAL);
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("user", "", "ROLE_USER"));
  }

  /** Stops the application. */
  @TearDown
  public void tearDown() {
    SecurityContextHolder.clearContext();
    context.close();
  }

  /** Calls a service method that the starter checks. */
  @Benchmark
  public void advised() {
    advised.save(order);
  }

  /** Calls a method with the same other advice that checks the same rules by hand. */
  @Benchmark
  public void manual() {
    manual.save(order);
  }

  /** The application: Muster's auto-configuration and the two services. */
  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration(MusterAutoConfiguration.class)
  static class Application {
    @Bean
    AdvisedService advisedService() {
      return new AdvisedService();
    }

    @Bean
    ManualService manualService(Muster muster) {
      return new ManualService(muster.binding(Order.class).build());
    }
  }

  /** Method security, which checks the user before the rules. */
  @Configuration(proxyBeanMethods = false)
  @EnableMethodSecurity
  static class Security {}

  /** Transactions without a database: the rules run inside one, as the starter orders them. */
  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  static class Transactions {
    @Bean
    PlatformTransactionManager transactionManager() {
      return new NoResourceTransactionManager();
    }
  }

  /** A service whose rules bind to its input by type, through the starter's advice. */
  @Service
  static class AdvisedService {
    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void save(Order order) {}
  }

  /**
   * A component that checks the rules itself, before it does its work, under the same other advice;
   * not a service, so that the starter leaves it alone.
   */
  @Component
  static class ManualService {
    private final Binding<Order> binding;

    ManualService(Binding<Order> binding) {
      this.binding = binding;
    }

    @PreAuthorize("hasRole('USER')")
    @Transactional
    public void save(Order order) {
      binding.check(order);
    }
  }

  /** A transaction manager that begins and commits nothing, so that only Spring's advice counts. */
  static final class NoResourceTransactionManager extends AbstractPlatformTransactionManager {
    @Serial private static final long serialVersionUID = 1L;

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {}

    @Override
    protected void doRollback(DefaultTransactionStatus status) {}
  }
}
