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
 * How far a check goes once a rule is violated. Every {@link Muster} has a mode, {@link #FAIL_FAST}
 * unless set; a {@link RuleChain} can override it.
 */
public enum CheckMode {

  /**
   * Stop at the first rule that is violated and report all of its violations. Rules that are
   * skipped, because they do not apply or a rule they require did not pass, do not stop the check.
   */
  FAIL_FAST,

  /** Run every rule that applies and report the violations of all of them. */
  COLLECT_ALL
}
