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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import me.yakar.muster.Muster;
import me.yakar.muster.RuleChain;
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.RequiresRule;
import me.yakar.muster.rule.Rule;
import me.yakar.muster.rule.RuleContext;
import me.yakar.muster.rule.RuleResult;
import me.yakar.muster.rule.ValueRule;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * A check of three rules about the customer of an order: one finds the customer, and two require it
 * and look at the customer. {@code values} shares the customer the first rule found; {@code
 * rereads} has the two rules find it again, in a map in memory, which is the cheapest a second read
 * can be. The difference is what sharing a value costs, or saves, against a read that costs almost
 * nothing. The order's {@link Order#failing()} serves as the id of its customer: 0, which exists.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(5)
public class ValueBenchmark {

  /** A customer of the shop. */
  record Customer(long id, boolean active, long credit) {}

  /** The customers, in memory. */
  static final class Customers {

    private final Map<Long, Customer> byId = new ConcurrentHashMap<>();

    Customers() {
      byId.put(0L, new Customer(0, true, 1_000));
    }

    Customer find(long id) {
      return byId.get(id);
    }
  }

  /** The customer of an order exists, and is left for the rules that require this one. */
  @Rule(id = "customer.exists")
  public static final class CustomerExists implements ValueRule<Order, Customer> {

    private final Customers customers;

    CustomerExists(Customers customers) {
      this.customers = customers;
    }

    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      var customer = customers.find(input.failing());
      return customer == null
          ? RuleResult.failed(ctx.violation().build())
          : RuleResult.passed(customer);
    }
  }

  /** The customer is active, read from the value of {@link CustomerExists}. */
  @Rule(id = "customer.active")
  @RequiresRule(CustomerExists.class)
  public static final class CustomerActive implements BusinessRule<Order> {

    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return ctx.valueOf(CustomerExists.class).active()
          ? RuleResult.passed()
          : RuleResult.failed(ctx.violation().build());
    }
  }

  /** The customer has credit, read from the value of {@link CustomerExists}. */
  @Rule(id = "customer.credit")
  @RequiresRule(CustomerExists.class)
  public static final class CustomerCredit implements BusinessRule<Order> {

    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return ctx.valueOf(CustomerExists.class).credit() > 0
          ? RuleResult.passed()
          : RuleResult.failed(ctx.violation().build());
    }
  }

  /** The customer of an order exists, and leaves nothing. */
  @Rule(id = "customer.found")
  public static final class CustomerFound implements BusinessRule<Order> {

    private final Customers customers;

    CustomerFound(Customers customers) {
      this.customers = customers;
    }

    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return customers.find(input.failing()) == null
          ? RuleResult.failed(ctx.violation().build())
          : RuleResult.passed();
    }
  }

  /** The customer is active, read again. */
  @Rule(id = "customer.active-again")
  @RequiresRule(CustomerFound.class)
  public static final class ActiveAgain implements BusinessRule<Order> {

    private final Customers customers;

    ActiveAgain(Customers customers) {
      this.customers = customers;
    }

    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return customers.find(input.failing()).active()
          ? RuleResult.passed()
          : RuleResult.failed(ctx.violation().build());
    }
  }

  /** The customer has credit, read again. */
  @Rule(id = "customer.credit-again")
  @RequiresRule(CustomerFound.class)
  public static final class CreditAgain implements BusinessRule<Order> {

    private final Customers customers;

    CreditAgain(Customers customers) {
      this.customers = customers;
    }

    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return customers.find(input.failing()).credit() > 0
          ? RuleResult.passed()
          : RuleResult.failed(ctx.violation().build());
    }
  }

  private RuleChain<Order> values;
  private RuleChain<Order> rereads;
  private Order order;

  /** Prepares Muster and the two chains once, as an application does when it starts. */
  @Setup
  public void setUp() {
    var customers = new Customers();
    var muster =
        Muster.builder()
            .rules(
                new CustomerExists(customers),
                new CustomerActive(),
                new CustomerCredit(),
                new CustomerFound(customers),
                new ActiveAgain(customers),
                new CreditAgain(customers))
            .build();
    values = muster.chain(Order.class, CustomerActive.class, CustomerCredit.class);
    rereads = muster.chain(Order.class, ActiveAgain.class, CreditAgain.class);
    order = new Order(0);
  }

  /** The two rules read the customer that the first rule left. */
  @Benchmark
  public void values() {
    values.check(order);
  }

  /** The two rules read the customer again. */
  @Benchmark
  public void rereads() {
    rereads.check(order);
  }
}
