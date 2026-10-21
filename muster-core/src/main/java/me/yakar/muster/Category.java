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
package me.yakar.muster;

/**
 * The kind of business problem a {@link Violation} reports.
 *
 * <p>Muster itself does not know HTTP. Integrations map categories to their own error model; the
 * Spring Boot starter maps them to response status codes. A violation without a category is treated
 * as unprocessable.
 */
public enum Category {

  /** Something the request refers to does not exist. */
  NOT_FOUND,

  /** The request conflicts with existing data, for example a name that is already taken. */
  CONFLICT,

  /** The data the request acts on is not in a state that allows it. */
  INVALID_STATE
}
