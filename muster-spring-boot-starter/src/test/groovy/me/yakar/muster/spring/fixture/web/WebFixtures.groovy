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
package me.yakar.muster.spring.fixture.web

import me.yakar.muster.BusinessRuleViolationException
import me.yakar.muster.rule.Violation
import me.yakar.muster.spring.MusterAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

/** The violations that the controllers throw; a test sets them. */
class Violations {
  volatile List<Violation> current = []

  BusinessRuleViolationException exception() {
    new BusinessRuleViolationException(current)
  }
}

@RestController
class VehicleController {

  private final Violations violations

  VehicleController(Violations violations) {
    this.violations = violations
  }

  @PostMapping('/vehicles')
  void save() {
    throw violations.exception()
  }
}

@RestController
class LocalHandlerController {

  private final Violations violations

  LocalHandlerController(Violations violations) {
    this.violations = violations
  }

  @PostMapping('/local')
  void save() {
    throw violations.exception()
  }

  @ExceptionHandler(BusinessRuleViolationException)
  ResponseEntity<String> local(BusinessRuleViolationException e) {
    ResponseEntity.status(418).body('local')
  }
}

@RestControllerAdvice
class NamingAdvice {
  @ExceptionHandler(BusinessRuleViolationException)
  ResponseEntity<String> own(BusinessRuleViolationException e) {
    ResponseEntity.status(400).body('own')
  }
}

@RestControllerAdvice
class ParameterAdvice {
  @ExceptionHandler
  ResponseEntity<String> own(BusinessRuleViolationException e) {
    ResponseEntity.status(400).body('own')
  }
}

@RestControllerAdvice
class ListingAdvice {
  @ExceptionHandler([
    IllegalStateException,
    BusinessRuleViolationException
  ])
  ResponseEntity<String> own(Exception e) {
    ResponseEntity.status(400).body('own')
  }
}

@RestControllerAdvice
class CatchAllAdvice {
  @ExceptionHandler(Exception)
  ResponseEntity<String> all(Exception e) {
    ResponseEntity.status(500).body('error')
  }
}

/** Handles other exceptions only. */
@RestControllerAdvice
class UnrelatedAdvice {
  @ExceptionHandler(IllegalArgumentException)
  ResponseEntity<String> other(IllegalArgumentException e) {
    ResponseEntity.status(400).body('other')
  }
}

/** An auto-configuration of an error handling library, which comes after Muster's. */
@AutoConfiguration(after = MusterAutoConfiguration)
class LaterErrorsAutoConfiguration {
  @Bean
  NamingAdvice laterNamingAdvice() {
    new NamingAdvice()
  }
}
