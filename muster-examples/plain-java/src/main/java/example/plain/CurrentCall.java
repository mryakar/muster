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

import java.security.Principal;
import java.util.Locale;

// tag::current-call[]
/**
 * Who makes the current call and in which locale, which the edge of the application, such as the
 * handler of a request, sets for the thread that serves it.
 */
final class CurrentCall {

  private static final ThreadLocal<Call> CALL = new ThreadLocal<>();

  private CurrentCall() {}

  /** Runs work as a call of the given caller, in the given locale. */
  static void run(Principal caller, Locale locale, Runnable work) {
    var previous = CALL.get();
    CALL.set(new Call(caller, locale));
    try {
      work.run();
    } finally {
      if (previous == null) {
        CALL.remove(); // leaves nothing behind on a thread of a pool
      } else {
        CALL.set(previous);
      }
    }
  }

  /** Returns the caller, or {@code null} outside a call: Muster takes that as no caller. */
  static Principal caller() {
    var call = CALL.get();
    return call == null ? null : call.caller();
  }

  static Locale locale() {
    var call = CALL.get();
    return call == null ? Locale.getDefault() : call.locale();
  }

  private record Call(Principal caller, Locale locale) {}
}
// end::current-call[]
