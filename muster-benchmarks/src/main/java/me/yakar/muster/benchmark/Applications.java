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
package me.yakar.muster.benchmark;

import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

/** Starts the Spring applications that the benchmarks of the starter measure. */
final class Applications {

  private Applications() {}

  /**
   * Starts an application from the sources with the first rules as beans, as Spring Boot does, but
   * with no web server, banner or startup log.
   */
  static ConfigurableApplicationContext start(int rules, Class<?>... sources) {
    return new SpringApplicationBuilder(sources)
        .web(WebApplicationType.NONE)
        .bannerMode(Banner.Mode.OFF)
        .logStartupInfo(false)
        // With no rules, Muster warns that none binds; here that is intended.
        .properties("logging.level.root=warn", "logging.level.me.yakar.muster=error")
        .initializers(
            applicationContext ->
                Rules.first(rules)
                    .forEach(
                        rule ->
                            ((GenericApplicationContext) applicationContext)
                                .registerBean(ruleClass(rule), () -> rule)))
        .run();
  }

  @SuppressWarnings("unchecked")
  private static Class<Rules.IndexedRule> ruleClass(Rules.IndexedRule rule) {
    return (Class<Rules.IndexedRule>) rule.getClass();
  }
}
