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
import me.yakar.muster.spring.internal.advice.AdviceConfiguration
import me.yakar.muster.spring.internal.engine.EngineConfiguration
import spock.lang.Specification

/**
 * The starter's optional dependencies, Spring's transactions and Spring Security, may be absent from
 * an application. Spring introspects a bean's class, so the classes it creates without them must
 * not name their types in a field, a constructor or a method; the tests run with both on the class
 * path, so only the signatures can show it.
 */
class OptionalDependenciesSpec extends Specification {

  def '#type names no type of #dependency, which the application may not have'() {
    expect:
    names(type).findAll { it.startsWith(dependency) }.empty

    where:
    type                                                          | dependency
    AdviceConfiguration                                           | 'org.springframework.transaction'
    classOf('internal.advice.RulesInterceptor')                   | 'org.springframework.transaction'
    classOf('internal.advice.MethodAdvice')                       | 'org.springframework.transaction'
    classOf('internal.advice.RulesAdvisor')                       | 'org.springframework.transaction'
    classOf('internal.advice.AdvisedMethods')                     | 'org.springframework.transaction'
    classOf('internal.advice.ProxyCreatorRegistrar')              | 'org.springframework.transaction'
    EngineConfiguration                                           | 'org.springframework.security'
    classOf('internal.engine.RuleBeans')                        | 'org.springframework.security'
    classOf('internal.engine.RuleScanner')                        | 'org.springframework.security'
    classOf('internal.engine.SecurityCaller')                     | 'org.springframework.transaction'
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
