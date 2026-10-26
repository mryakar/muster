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
package me.yakar.muster.rule;

import java.util.ArrayList;
import java.util.List;

/**
 * A business rule that a call must satisfy before the service method runs.
 *
 * <p>A rule is a class annotated with {@link Rule}, which gives it a stable id. The input type
 * {@code T} is read from the class declaration, so a rule cannot be a lambda or a class that leaves
 * {@code T} open:
 *
 * {@snippet :
 * @Rule(id = "vehicle.name-unique")
 * final class VehicleNameUnique implements BusinessRule<VehicleFields> {
 *
 *   @Override
 *   public RuleResult check(VehicleFields input, RuleContext ctx) {
 *     return vehicles.existsByName(input.name())
 *         ? RuleResult.failed(ctx.violation().clientArg("name", input.name()).build())
 *         : RuleResult.passed();
 *   }
 * }
 * }
 *
 * <p>A single instance serves every call, possibly from many threads at once, so a rule must be
 * thread-safe.
 *
 * @param <T> the type of input the rule checks
 */
public interface BusinessRule<T> {

  /**
   * Checks the input of a call. Report what is wrong as violations created from {@code ctx}; a
   * violation of another rule fails the check with an {@link IllegalStateException}. A technical
   * failure, such as a lost database connection, is not a violation: let it propagate.
   *
   * @param input the input of the call
   * @param ctx the context of the call, which also creates the violations
   * @return {@link RuleResult#passed()}, or a failed result with the violations; must not be {@code
   *     null}, or the check fails with a {@link NullPointerException}
   */
  RuleResult check(T input, RuleContext ctx);

  /**
   * Checks the inputs of a call that takes a collection, all at once. Override it when checking
   * them one by one costs too much, such as a query per element: one query can look them all up,
   * and it can also find conflicts within the collection, such as two new vehicles with the same
   * name.
   *
   * <p>Report the problems of an element as violations created from {@code ctx.element(i)}, with
   * {@code i} the index of the input in {@code inputs}: their target starts with the position of
   * the element, as in {@code [3].name}, and rules that require this one still check the other
   * elements. A violation created from {@code ctx} itself is about the whole collection, and no
   * rule that requires this one checks any of it.
   *
   * <p>Muster asks {@link #appliesTo} once for the collection. A rule that does not override this
   * method is checked element by element, by Muster, which logs a warning once; override it and
   * call {@code BusinessRule.super.checkAll(inputs, ctx)} to keep that and drop the warning.
   *
   * @param inputs the inputs of the call, in the order of the collection; the elements that failed
   *     a rule this rule requires are left out
   * @param ctx the context of the call; {@link RuleContext#element(int)} gives the context of each
   *     input
   * @return {@link RuleResult#passed()}, or a failed result with the violations; must not be {@code
   *     null}, or the check fails with a {@link NullPointerException}
   */
  default RuleResult checkAll(List<? extends T> inputs, RuleContext ctx) {
    var violations = new ArrayList<Violation>();
    for (var i = 0; i < inputs.size(); i++) {
      var element = ctx.element(i);
      var result = check(inputs.get(i), element);
      if (result == null) {
        throw new NullPointerException(
            "rule '"
                + ctx.ruleId()
                + "' returned null for the element at ["
                + element.elementIndex().getAsInt()
                + "]");
      }
      if (result instanceof RuleResult.Failed failed) {
        violations.addAll(failed.violations());
      }
    }
    return violations.isEmpty() ? RuleResult.passed() : RuleResult.failed(violations);
  }

  /**
   * Returns whether this rule applies to a call. A rule that does not apply is skipped: it is not
   * checked and does not count as passed.
   *
   * <p>Decide from the context only, such as the caller or the locale. A condition on the input
   * belongs in {@link #check}.
   *
   * @param ctx the context of the call
   * @return {@code true} unless overridden
   */
  default boolean appliesTo(RuleContext ctx) {
    return true;
  }
}
