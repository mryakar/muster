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
package me.yakar.muster.spring

import org.springframework.boot.builder.SpringApplicationBuilder
import spock.lang.Specification

/** Muster logs through the JDK, and Spring Boot passes those lines on to its own log. */
class MusterLogSpec extends Specification {

  def 'a rule switched off by configuration is logged in the log of the application'() {
    given:
    def original = System.out
    def captured = new ByteArrayOutputStream()
    System.out = new PrintStream(new TeeOutputStream(original, captured), true)

    when:
    def context = new SpringApplicationBuilder(IntegrationTestApplication)
        .properties('muster.rules.vehicle.name-unique.enabled=false')
        .run()
    context.close()

    then:
    // Spring Boot's own line format, with its level and the logger name; the JDK would write
    // "WARNING:" to standard error instead.
    captured.toString().readLines().any {
      it =~ / WARN .* me\.yakar\.muster +: Rule 'vehicle\.name-unique' is disabled$/
    }

    cleanup:
    System.out = original
  }

  /** Writes to two streams. */
  static class TeeOutputStream extends OutputStream {
    final OutputStream first
    final OutputStream second

    TeeOutputStream(OutputStream first, OutputStream second) {
      this.first = first
      this.second = second
    }

    void write(int b) {
      first.write(b)
      second.write(b)
    }
  }
}
