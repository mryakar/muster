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
package me.yakar.muster.spring.internal.problem;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import me.yakar.muster.BusinessRuleViolationException;
import me.yakar.muster.rule.Violation;
import me.yakar.muster.spi.MessageResolver;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers a {@link BusinessRuleViolationException} with problem details: the status that the
 * categories of the violations decide, and a {@code violations} property of {@link ViolationEntry}
 * items.
 *
 * <p>Its order is 0, as for Spring Boot's own problem details, so it comes before controller advice
 * without an order, whose handler of {@link Exception} would answer violations as server errors.
 * {@link ViolationHandlerRemover} removes it when the application handles the exception itself. Not
 * final, so that an advisor of the application can proxy it.
 */
@RestControllerAdvice
@Order(0)
@Conditional(ViolationExceptionHandler.NotScanned.class)
class ViolationExceptionHandler {

  /**
   * Keeps component scanning from registering the handler, since a controller advice is a
   * component: an application that scans the packages of Muster, such as {@code me.yakar}, would
   * register it a second time, without its configuration. {@link
   * me.yakar.muster.spring.MusterAutoConfiguration} registers it through a bean method, which reads
   * the conditions of the method only.
   */
  static final class NotScanned implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      return false;
    }
  }

  private static final System.Logger LOGGER = System.getLogger("me.yakar.muster");

  private final MessageResolver messages;
  private final Supplier<Locale> locale;
  private final int mixedStatus;

  ViolationExceptionHandler(MessageResolver messages, Supplier<Locale> locale, int mixedStatus) {
    this.messages = messages;
    this.locale = locale;
    this.mixedStatus = mixedStatus;
  }

  @ExceptionHandler(BusinessRuleViolationException.class)
  ProblemDetail handle(BusinessRuleViolationException exception) {
    var violations = exception.violations();
    var problem = ProblemDetail.forStatus(status(violations));
    LOGGER.log(
        Level.DEBUG,
        () ->
            "Answering " + problem.getStatus() + " to the business rule violations " + violations);
    var callerLocale = locale.get();
    problem.setProperty(
        "violations",
        violations.stream().map(violation -> entry(violation, callerLocale)).toList());
    return problem;
  }

  /**
   * Returns the status of the category that all violations share, and {@link #mixedStatus} if they
   * do not share one. Having no category counts as a category of its own.
   */
  private int status(List<Violation> violations) {
    var category = violations.getFirst().category();
    if (violations.stream().anyMatch(violation -> !violation.category().equals(category))) {
      return mixedStatus;
    }
    var status =
        category
            .map(
                shared ->
                    switch (shared) {
                      case FORBIDDEN -> HttpStatus.FORBIDDEN;
                      case NOT_FOUND -> HttpStatus.NOT_FOUND;
                      case CONFLICT -> HttpStatus.CONFLICT;
                      case INVALID_STATE -> HttpStatus.UNPROCESSABLE_CONTENT;
                    })
            .orElse(HttpStatus.UNPROCESSABLE_CONTENT);
    return status.value();
  }

  private ViolationEntry entry(Violation violation, Locale callerLocale) {
    return new ViolationEntry(
        violation.code(),
        messages.resolve(violation, callerLocale),
        violation.target().orElse(null),
        violation.category().map(Enum::name).orElse(null),
        violation.clientArgs());
  }
}
