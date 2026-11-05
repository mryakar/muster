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

import java.util.function.Supplier
import org.springframework.aot.test.generate.TestGenerationContext
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader
import org.springframework.context.aot.ApplicationContextAotGenerator
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.test.tools.TestCompiler

/**
 * Starts an application as from a native image: processes it ahead of time, compiles what the
 * processing generates and starts from that. A test that calls it carries {@code
 * CompileWithForkedClassLoader}, which loads the generated code next to the package-private classes
 * it calls.
 */
class AheadOfTime {

  /**
   * Processes the configuration ahead of time, with the given properties, compiles what it
   * generates and starts from it, with the properties to start with: by default the same.
   */
  static void startedAheadOfTime(Class<?> configuration, Map<String, Object> properties = [:],
      Map<String, Object> propertiesAtStart = properties, Closure check) {
    startedAheadOfTime({
      new GenericApplicationContext()
    }, configuration, properties, propertiesAtStart, check)
  }

  /** As above, in contexts of the kind that {@code contexts} makes, such as a web application's. */
  static void startedAheadOfTime(Supplier<GenericApplicationContext> contexts, Class<?> configuration,
      Map<String, Object> properties, Map<String, Object> propertiesAtStart, Closure check) {
    def generation = new TestGenerationContext()
    def processed = withProperties(contexts.get(), properties)
    def initializer
    try {
      new AnnotatedBeanDefinitionReader(processed).register(configuration)
      initializer = new ApplicationContextAotGenerator().processAheadOfTime(processed, generation)
    } finally {
      processed.close()
    }
    generation.writeGeneratedContent()
    TestCompiler.forSystem().with(generation).compile { compiled ->
      // As Spring Boot starts an application from generated code: with ahead-of-time mode on.
      def aotEnabled = System.setProperty('spring.aot.enabled', 'true')
      def context = withProperties(contexts.get(), propertiesAtStart)
      try {
        compiled.getInstance(ApplicationContextInitializer, initializer.toString()).initialize(context)
        context.refresh()
        check(context)
      } finally {
        context.close()
        if (aotEnabled == null) {
          System.clearProperty('spring.aot.enabled')
        } else {
          System.setProperty('spring.aot.enabled', aotEnabled)
        }
      }
    }
  }

  private static GenericApplicationContext withProperties(GenericApplicationContext context, Map<String, Object> properties) {
    context.environment.propertySources.addFirst(new MapPropertySource('test', properties))
    context
  }
}
