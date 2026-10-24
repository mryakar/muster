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
package me.yakar.muster.spring;

import java.lang.System.Logger.Level;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import me.yakar.muster.BusinessRuleViolationException;
import me.yakar.muster.rule.Violation;
import me.yakar.muster.spi.MessageResolver;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers a {@link BusinessRuleViolationException} with problem details: a status that the
 * categories of the violations decide, and the violations in the {@code violations} property, each
 * with its code, its message in the locale of the call, its target and category if it has them, and
 * its client arguments.
 *
 * <p>It comes before the controller advice of the application, as Spring Boot's own problem details
 * do, so that a handler of {@link Exception} does not answer violations as errors of the server. An
 * application that handles the exception itself, by naming it in an {@link ExceptionHandler} of a
 * controller advice, has no such handler.
 */
@RestControllerAdvice
@Order(0)
@Conditional(ViolationExceptionHandler.NotScanned.class)
final class ViolationExceptionHandler {

  /**
   * Keeps component scanning from registering the handler, since a controller advice is a
   * component: an application that scans the packages of Muster, such as {@code me.yakar}, would
   * register it a second time, without its configuration. {@link MusterAutoConfiguration} registers
   * it through a bean method, which reads the conditions of the method only.
   */
  static final class NotScanned implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      return false;
    }
  }

  private static final System.Logger LOGGER = System.getLogger("me.yakar.muster");

  private static final ConfigurationPropertyName MIXED_STATUS =
      ConfigurationPropertyName.of("muster.problem-details.mixed-status");

  private final MessageResolver messages;
  private final Supplier<Locale> locale;
  private final int mixedStatus;

  ViolationExceptionHandler(MessageResolver messages, Supplier<Locale> locale, int mixedStatus) {
    this.messages = messages;
    this.locale = locale;
    this.mixedStatus = mixedStatus;
  }

  /**
   * Returns the status that answers violations of different categories, which {@code
   * muster.problem-details.mixed-status} sets.
   *
   * @throws IllegalStateException if the property is set to nothing, or to a status that is not a
   *     client error
   */
  static int mixedStatus(Environment environment) {
    var binder = Binder.get(environment);
    var values = "a status code from 400 to 499";
    RuleSwitches.requireValue(binder, MIXED_STATUS, values);
    int status =
        binder
            .bind(MIXED_STATUS, Bindable.of(Integer.class))
            .orElse(HttpStatus.UNPROCESSABLE_CONTENT.value());
    if (status < 400 || status > 499) {
      throw new IllegalStateException(
          MIXED_STATUS
              + " is "
              + status
              + "; set it to "
              + values
              + ", since a violation is an error of the client");
    }
    return status;
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

  /**
   * Returns what the response says about a violation. It is a map, not a type of its own, so that
   * the response looks the same whichever JSON library writes it and however it is configured.
   */
  private Map<String, Object> entry(Violation violation, Locale callerLocale) {
    var entry = new LinkedHashMap<String, Object>();
    entry.put("code", violation.code());
    entry.put("message", messages.resolve(violation, callerLocale));
    violation.target().ifPresent(target -> entry.put("target", target));
    violation.category().ifPresent(category -> entry.put("category", category.name()));
    entry.put("args", violation.clientArgs());
    return entry;
  }
}
