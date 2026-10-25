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
package me.yakar.muster.spring.fixture.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.util.concurrent.CopyOnWriteArrayList
import me.yakar.muster.rule.BusinessRule
import me.yakar.muster.rule.Rule
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.RuleResult
import me.yakar.muster.spring.Rules
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.validation.annotation.Validated

/** Notes the steps of a call, in order: the constraint, the rule, and whether a transaction was active. */
class Steps {
  static final List<String> NOTED = new CopyOnWriteArrayList<>()

  static void note(String step) {
    NOTED << (TransactionSynchronizationManager.actualTransactionActive ? "$step in transaction" : step)
  }
}

@Rule(id = 'validation.probe')
class ValidationProbe implements BusinessRule<String> {
  RuleResult check(String input, RuleContext ctx) {
    Steps.note('rule')
    RuleResult.passed()
  }
}

/** A constraint that notes when it is checked and rejects a blank value. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NotBlankNoted)
@interface Noted {
  String message() default 'must not be blank'

  Class<?>[] groups() default []

  Class<? extends Payload>[] payload() default []
}

class NotBlankNoted implements ConstraintValidator<Noted, String> {
  boolean isValid(String value, ConstraintValidatorContext context) {
    Steps.note('constraint')
    !value.blank
  }
}

@Validated
class ValidatedService {
  @Rules(ValidationProbe)
  void create(@Noted String name) {}

  @Transactional
  @Rules(ValidationProbe)
  void createInTransaction(@Noted String name) {}
}
