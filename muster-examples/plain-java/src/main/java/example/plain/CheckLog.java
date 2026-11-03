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
package example.plain;

import java.util.function.Consumer;
import me.yakar.muster.spi.CheckListener;

// tag::listener[]
/** Logs each rule of each check: the check, the rule, its outcome and how long it took. */
final class CheckLog implements CheckListener {

  private final Consumer<String> log;

  CheckLog(Consumer<String> log) {
    this.log = log;
  }

  @Override
  public Observer checkStarted(Check check) {
    return new Observer() { // one per check, so it needs no state to tell checks apart
      @Override
      public void ruleEnded(RuleEnd end) {
        log.accept(
            check.name()
                + ": "
                + end.ruleId()
                + " "
                + end.outcome()
                + " in "
                + end.took().toNanos()
                + " ns");
      }
    };
  }
}
// end::listener[]
