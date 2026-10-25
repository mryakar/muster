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

import com.jayway.jsonpath.JsonPath
import me.yakar.muster.CheckMode
import spock.lang.Specification

class ConfigurationMetadataSpec extends Specification {

  def metadata = JsonPath.parse(getClass().getResource('/META-INF/spring-configuration-metadata.json').text)

  def 'describes the properties of Muster for IDEs'() {
    expect:
    metadata.read('$.properties[*].name') == [
      'muster.mode',
      'muster.transaction.reorder-advisor',
      'muster.validation.reorder-advisor',
      'muster.problem-details.mixed-status',
      'muster.rules'
    ]
    metadata.read('$..sourceType').every { it == MusterAutoConfiguration.name }
  }

  def 'offers every mode, in the form a property takes'() {
    given:
    def modes = CheckMode.values().collect {
      it.name().toLowerCase().replace('_', '-')
    }

    expect:
    metadata.read('$.hints[?(@.name == "muster.mode")].values[*].value') == modes
    metadata.read('$.properties[?(@.name == "muster.mode")].defaultValue') == ['fail-fast']
  }

  def 'gives the default mixed status'() {
    expect:
    metadata.read('$.properties[?(@.name == "muster.problem-details.mixed-status")].defaultValue') == [422]
  }
}
