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

import java.lang.reflect.Executable
import me.yakar.muster.spring.internal.Settings
import me.yakar.muster.spring.internal.advice.AdviceConfiguration
import me.yakar.muster.spring.internal.engine.EngineConfiguration
import me.yakar.muster.spring.internal.problem.ProblemDetailsConfiguration
import spock.lang.Specification

/**
 * The starter's optional dependencies, Spring's transactions, Spring Security, Spring Web and Bean
 * Validation, may be absent from an application. Spring introspects a bean's class, so the classes it creates without
 * one of them must not name its types in a field, a constructor or a method; the tests run with all
 * of them on the class path, so only the signatures can show it.
 */
class OptionalDependenciesSpec extends Specification {

  static final String TRANSACTION = 'org.springframework.transaction'
  static final String SECURITY = 'org.springframework.security'
  static final String WEB = 'org.springframework.web'
  static final String VALIDATION = 'jakarta.validation'

  /** The classes that the starter creates, or Spring reads, without any optional dependency. */
  static final List<Class<?>> WITHOUT_ANY = [
    MusterAutoConfiguration,
    Rules,
    Settings,
    EngineConfiguration,
    classOf('internal.engine.RuleBeans'),
    classOf('internal.engine.RuleScanner'),
    classOf('internal.engine.MessageSourceTemplates'),
    classOf('internal.engine.OwnMusterCheck'),
    AdviceConfiguration,
    classOf('internal.advice.RulesInterceptor'),
    classOf('internal.advice.MethodAdvice'),
    classOf('internal.advice.RulesAdvisor'),
    classOf('internal.advice.AdvisedMethods'),
    classOf('internal.advice.ProxyCreatorRegistrar'),
    classOf('internal.advice.ValidationOrdering'),
    classOf('internal.advice.TransactionGuard$Resolved')
  ]

  def '#type.name names no type of #dependency, which the application may not have'() {
    expect:
    names(type).findAll { it.startsWith(dependency) }.empty

    where:
    [type, dependency] << [WITHOUT_ANY, [TRANSACTION, SECURITY, WEB, VALIDATION]].combinations() +
    [[classOf('internal.engine.SecurityCaller')], [TRANSACTION, WEB, VALIDATION]].combinations() +
    [[ProblemDetailsConfiguration], [TRANSACTION, SECURITY, VALIDATION]].combinations() +
    [
      [classOf('internal.advice.TransactionOrdering'), classOf('internal.advice.TransactionGuard')],
      [SECURITY, WEB, VALIDATION]
    ].combinations()
  }

  private static Class<?> classOf(String name) {
    Class.forName("me.yakar.muster.spring.$name")
  }

  /** The type names in the signatures of a class: its fields, constructors and methods. */
  private static Set<String> names(Class<?> type) {
    def names = type.declaredFields*.genericType*.typeName as Set<String>
    def executables = (type.declaredConstructors as List<Executable>) + (type.declaredMethods as List<Executable>)
    executables.each {
      names.addAll(it.genericParameterTypes*.typeName + it.genericExceptionTypes*.typeName)
    }
    names.addAll(type.declaredMethods*.genericReturnType*.typeName)
    names
  }
}
