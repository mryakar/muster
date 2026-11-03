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
package example.spring;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// tag::advice[]
/** Answers a command that fails method validation, before any rule runs, with 400. */
@RestControllerAdvice
public class ConstraintViolations {

  @ExceptionHandler
  ProblemDetail invalid(ConstraintViolationException e) {
    var fields =
        e.getConstraintViolations().stream()
            .map(violation -> field(violation.getPropertyPath()) + " " + violation.getMessage())
            .sorted()
            .collect(Collectors.joining(", "));
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, fields);
  }

  /** Returns the field of the command, without the method and parameter it is reached through. */
  private static String field(Path path) {
    String last = null;
    for (var node : path) {
      last = node.getName();
    }
    return last;
  }
}
// end::advice[]
