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
package me.yakar.muster.spring.internal.problem

import me.yakar.muster.Muster
import me.yakar.muster.rule.Category
import me.yakar.muster.spring.fixture.web.VehicleController
import me.yakar.muster.spring.fixture.web.Violations
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

/** A slice of the web layer, which loads the web auto-configurations only. */
@WebMvcTest(properties = 'spring.messages.basename=me.yakar.muster.spring.messages')
@Import([VehicleController, Violations])
class ProblemDetailsSliceSpec extends Specification {

  @SpringBootConfiguration(proxyBeanMethods = false)
  static class Application {}

  @Autowired
  ApplicationContext context

  @Autowired
  MockMvc mvc

  @Autowired
  Violations violations

  def 'a @WebMvcTest answers violations with problem details'() {
    given:
    violations.current = [ProblemDetailsSpec.nameTaken()]

    when:
    def answer = ProblemDetailsSpec.answer(mvc)

    then:
    answer.status == 409
    answer.type == 'application/problem+json'
    answer.body.violations == [
      [code: 'vehicle.name-taken', message: "The name Atlas isn't free", target: 'name', category: Category.CONFLICT.name(), args: [name: 'Atlas']]
    ]
  }

  def 'the slice gets the handler and the message resolver, without the rules'() {
    expect:
    context.containsBean('musterViolationExceptionHandler')
    context.containsBean('musterMessageResolver')
    context.getBeanNamesForType(Muster).length == 0
    !context.containsBean('musterRuleScanner')
    !context.containsBean('musterRulesAdvisor')
    !context.containsBean('musterValidationOrdering')
  }
}
