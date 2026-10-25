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

import java.util.List;
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.Rule;
import me.yakar.muster.rule.RuleContext;
import me.yakar.muster.rule.RuleResult;

/**
 * Twenty rules that cost next to nothing, so that a benchmark measures Muster rather than the
 * rules. Each rule passes unless the order sets the bit of its index.
 *
 * <p>Each rule has a {@code check} of its own, as rules in an application do. Were they to share
 * one, the JIT could inline it into the engine's loop and optimize away work that an application
 * pays for, such as the context each rule receives.
 */
final class Rules {

  /** The rules, in the order of their indexes. */
  static final List<IndexedRule> ALL =
      List.of(
          new Rule01(),
          new Rule02(),
          new Rule03(),
          new Rule04(),
          new Rule05(),
          new Rule06(),
          new Rule07(),
          new Rule08(),
          new Rule09(),
          new Rule10(),
          new Rule11(),
          new Rule12(),
          new Rule13(),
          new Rule14(),
          new Rule15(),
          new Rule16(),
          new Rule17(),
          new Rule18(),
          new Rule19(),
          new Rule20());

  private Rules() {}

  /** Returns the first {@code count} rules. */
  static List<IndexedRule> first(int count) {
    return ALL.subList(0, count);
  }

  /** Returns the classes of the rules, for {@code muster.chain}. */
  @SuppressWarnings("unchecked")
  static Class<? extends BusinessRule<?>>[] classes(List<IndexedRule> rules) {
    return rules.stream().map(IndexedRule::getClass).toArray(Class[]::new);
  }

  /** A rule that the order violates when it sets the bit of the rule's index. */
  abstract static class IndexedRule implements BusinessRule<Order> {

    private final long bit;

    // The index comes from the rule's own id, rule-01 being 0; Rules.ALL is not yet built here.
    IndexedRule() {
      var id = getClass().getAnnotation(Rule.class).id();
      this.bit = 1L << (Integer.parseInt(id.substring("rule-".length())) - 1);
    }

    final RuleResult checkBit(Order input, RuleContext ctx) {
      return (input.failing() & bit) == 0
          ? RuleResult.passed()
          : RuleResult.failed(ctx.violation().build());
    }
  }

  @Rule(id = "rule-01")
  static final class Rule01 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }

  @Rule(id = "rule-02")
  static final class Rule02 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }

  @Rule(id = "rule-03")
  static final class Rule03 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }

  @Rule(id = "rule-04")
  static final class Rule04 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }

  @Rule(id = "rule-05")
  static final class Rule05 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }

  @Rule(id = "rule-06")
  static final class Rule06 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }

  @Rule(id = "rule-07")
  static final class Rule07 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }

  @Rule(id = "rule-08")
  static final class Rule08 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }

  @Rule(id = "rule-09")
  static final class Rule09 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }

  @Rule(id = "rule-10")
  static final class Rule10 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }

  @Rule(id = "rule-11")
  static final class Rule11 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }

  @Rule(id = "rule-12")
  static final class Rule12 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }

  @Rule(id = "rule-13")
  static final class Rule13 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }

  @Rule(id = "rule-14")
  static final class Rule14 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }

  @Rule(id = "rule-15")
  static final class Rule15 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }

  @Rule(id = "rule-16")
  static final class Rule16 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }

  @Rule(id = "rule-17")
  static final class Rule17 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }

  @Rule(id = "rule-18")
  static final class Rule18 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }

  @Rule(id = "rule-19")
  static final class Rule19 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }

  @Rule(id = "rule-20")
  static final class Rule20 extends IndexedRule {
    @Override
    public RuleResult check(Order input, RuleContext ctx) {
      return checkBit(input, ctx);
    }
  }
}
