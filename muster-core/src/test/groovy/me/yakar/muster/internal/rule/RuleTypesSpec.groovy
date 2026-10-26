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
package me.yakar.muster.internal.rule

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

  static abstract class ArrayRule<E> implements BusinessRule<E[]> {
    RuleResult check(E[] input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static class OfBoundArray extends ArrayRule<String> {}

  static class OfBoundArrayOfArrays extends ArrayRule<Integer[]> {}

  static class OfGenericArray implements BusinessRule<List<String>[]> {
    RuleResult check(List<String>[] input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static abstract class Pair<A, B> implements BusinessRule<B> {
    RuleResult check(B input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static abstract class Swap<C, D> extends Pair<D, C> {}

  static class Swapped extends Swap<Long, String> {}

  static interface Generic<Z> extends BusinessRule<Z> {}

  static abstract class ThroughSuperclassInterface<Q> implements Generic<Q> {
    RuleResult check(Q input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static class ThroughSuperclassAndInterface extends ThroughSuperclassInterface<Short> {}

  static class OpenArray<T> implements BusinessRule<T[]> {
    RuleResult check(T[] input, RuleContext ctx) {
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
    OfBoundArray || String[]
    OfBoundArrayOfArrays || Integer[][]
    OfGenericArray || List[]
    Swapped || Long
    ThroughSuperclassAndInterface || Short
  }

  static abstract class CollectionRule<E> implements BusinessRule<List<E>> {
    RuleResult check(List<E> input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static class OfNames extends CollectionRule<String> {}

  static class OfBoundElements<N extends Number> extends CollectionRule<N> {}

  static class OfOpenElements<T> extends CollectionRule<T> {}

  static class OfOpenArrays<T> extends CollectionRule<T[]> {}

  static class OfStringArrays extends CollectionRule<String[]> {}

  static class OfLists extends CollectionRule<List<String>> {}

  static class OfWildcard implements BusinessRule<Collection<? extends Number>> {
    RuleResult check(Collection<? extends Number> input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static class OfAnything implements BusinessRule<Set<?>> {
    RuleResult check(Set<?> input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static class OfLowerBound implements BusinessRule<List<? super Integer>> {
    RuleResult check(List<? super Integer> input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  @SuppressWarnings('rawtypes')
  static class OfRawList implements BusinessRule<List> {
    RuleResult check(List input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  @SuppressWarnings('rawtypes')
  static class OfRawCollection implements BusinessRule<Collection> {
    RuleResult check(Collection input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static class Fleet extends ArrayList<Long> {}

  static class OfFleet implements BusinessRule<Fleet> {
    RuleResult check(Fleet input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static abstract class Mid<X> implements BusinessRule<X> {
    RuleResult check(X input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  static abstract class ListThroughMid<E> extends Mid<List<E>> {}

  static class LongsThroughMid extends ListThroughMid<Long> {}

  static abstract class ArraysThroughMid<E> extends Mid<List<E[]>> {}

  static class LongArraysThroughMid extends ArraysThroughMid<Long> {}

  static interface MidRule<X> extends BusinessRule<X> {}

  static interface ListRule<E> extends MidRule<List<E>> {}

  static class LongsThroughInterfaces implements ListRule<Long> {
    RuleResult check(List<Long> input, RuleContext ctx) {
      RuleResult.passed()
    }
  }

  def 'the element type of a rule that checks collections is read from the class declaration: #ruleClass.simpleName'() {
    expect:
    RuleTypes.elementType(ruleClass) == elementType

    where:
    ruleClass || elementType
    OfList || String
    OfNames || String
    OfBoundElements || Number
    OfOpenElements || Object
    OfOpenArrays || Object[]
    OfStringArrays || String[]
    OfLists || List
    OfWildcard || Number
    OfAnything || Object
    OfLowerBound || Object
    OfRawList || Object
    OfRawCollection || Object
    OfFleet || Long
    LongsThroughMid || Long
    LongArraysThroughMid || Long[]
    LongsThroughInterfaces || Long
  }

  def 'a rule whose input is not a collection has no element type: #ruleClass.simpleName'() {
    expect:
    RuleTypes.elementType(ruleClass) == null

    where:
    ruleClass << [Direct, ThroughBase, OfArray, OfGenericArray]
  }

  def 'a class that does not name its input type is rejected'() {
    when:
    RuleTypes.inputType(ruleClass)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "cannot tell what ${ruleClass.typeName} checks; a rule names its input type, as in 'implements BusinessRule<Input>'"

    where:
    ruleClass << [Open, OpenArray, Raw, String]
  }
}
