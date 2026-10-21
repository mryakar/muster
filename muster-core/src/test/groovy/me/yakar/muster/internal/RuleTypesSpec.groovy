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
package me.yakar.muster.internal

import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import spock.lang.Specification

class RuleTypesSpec extends Specification {

  static class Direct implements BusinessRule<String> {
    RuleResult check(String input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static abstract class Base<X> implements BusinessRule<X> {
    RuleResult check(X input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static class ThroughBase extends Base<Long> {}

  static abstract class Middle<Y> extends Base<Y> {}

  static class ThroughTwoBases extends Middle<Integer> {}

  static interface StringRule extends BusinessRule<String> {}

  static class ThroughInterface implements StringRule {
    RuleResult check(String input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static interface Renamed<A> extends BusinessRule<A> {}

  static class ThroughGenericInterface implements Renamed<BigDecimal> {
    RuleResult check(BigDecimal input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static class AmongOtherInterfaces implements Serializable, Comparable<String>, BusinessRule<Character> {
    int compareTo(String other) {
      0
    }
    RuleResult check(Character input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static class OfList implements BusinessRule<List<String>> {
    RuleResult check(List<String> input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static class OfArray implements BusinessRule<String[]> {
    RuleResult check(String[] input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static class Open<T> implements BusinessRule<T> {
    RuleResult check(T input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  @SuppressWarnings('rawtypes')
  static class Raw implements BusinessRule {
    RuleResult check(Object input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  def 'the input type is read from the class declaration'() {
    expect:
    RuleTypes.inputType(ruleClass) == inputType

    where:
    ruleClass || inputType
    Direct || String
    ThroughBase || Long
    ThroughTwoBases || Integer
    ThroughInterface || String
    ThroughGenericInterface || BigDecimal
    AmongOtherInterfaces || Character
    OfList || List
    OfArray || String[]
  }

  def 'a class that does not name its input type is rejected'() {
    when:
    RuleTypes.inputType(ruleClass)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "cannot tell what ${ruleClass.name} checks; a rule names its input type, as in 'implements BusinessRule<Input>'"

    where:
    ruleClass << [Open, Raw, String]
  }
}
