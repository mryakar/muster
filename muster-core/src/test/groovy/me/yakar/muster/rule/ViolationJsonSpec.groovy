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
package me.yakar.muster.rule

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility
import com.fasterxml.jackson.annotation.PropertyAccessor
import me.yakar.muster.BusinessRuleViolationException
import spock.lang.Specification
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.json.JsonMapper

/**
 * A violation's internal arguments must not reach JSON, however an application configures its
 * mapper. Applications are told not to serialize violations directly; these tests make sure that
 * doing so anyway leaks nothing.
 */
class ViolationJsonSpec extends Specification {

  static final String SECRET = 'internal-7731'

  static final List<String> CONFIGURATIONS = [
    'default',
    'field visibility ANY',
    'all visibility ANY',
    'transient marker propagated'
  ]

  def violation = RuleContext.forRule('request.owner-only').violation('request.not-owner')
  .clientArg('requestNo', 'TR-1047')
  .internalArg('ownerId', SECRET)
  .build()

  def 'internal arguments of a #what do not reach JSON with the #configuration mapper'() {
    given:
    def json = mapper(configuration).writeValueAsString(value(what))

    expect:
    !json.contains(SECRET)
    !json.contains('ownerId')
    !json.contains('internalArgs')

    where:
    [configuration, what] << [
      CONFIGURATIONS,
      ['violation', 'failed result']
    ].combinations()
  }

  def 'a default mapper does not reveal internal arguments of an exception'() {
    // With field visibility ANY, Jackson cannot write any exception: the JDK does not open
    // Throwable's fields to it. It fails instead of revealing anything.
    given:
    def json = mapper('default').writeValueAsString(new BusinessRuleViolationException([violation]))

    expect:
    !json.contains(SECRET)
    !json.contains('ownerId')
  }

  def 'a default mapper finds nothing to write in a violation'() {
    expect:
    mapper('default').writeValueAsString(violation) == '{}'
    mapper('default').writeValueAsString(RuleResult.failed(violation)) == '{"violations":[{}]}'
  }

  private Object value(String what) {
    what == 'violation' ? violation : RuleResult.failed(violation)
  }

  private static JsonMapper mapper(String configuration) {
    def builder = JsonMapper.builder()
    switch (configuration) {
      case 'field visibility ANY':
        builder.changeDefaultVisibility {
          it.withVisibility(PropertyAccessor.FIELD, Visibility.ANY)
        }
        break
      case 'all visibility ANY':
        builder.changeDefaultVisibility {
          it.withVisibility(PropertyAccessor.ALL, Visibility.ANY)
        }
        break
      case 'transient marker propagated':
        builder.changeDefaultVisibility {
          it.withVisibility(PropertyAccessor.FIELD, Visibility.ANY)
        }
        builder.enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER)
        break
    }
    builder.build()
  }
}
