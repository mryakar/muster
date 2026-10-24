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

import me.yakar.muster.BusinessRuleViolationException
import me.yakar.muster.Muster
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.simple.JdbcClient
import spock.lang.Specification

@SpringBootTest(classes = IntegrationTestApplication)
class MusterAutoConfigurationIntegrationSpec extends Specification {

  @Autowired
  Muster muster

  @Autowired
  JdbcClient jdbc

  def setup() {
    jdbc.sql('create table if not exists vehicle (name text primary key)').update()
    jdbc.sql("insert into vehicle values ('Atlas') on conflict do nothing").update()
  }

  def 'an application gets Muster with its rules, which query its database'() {
    when:
    muster.check(new SaveVehicle('Boreas'))

    then:
    noExceptionThrown()

    when:
    muster.check(new SaveVehicle('Atlas'))

    then:
    def e = thrown(BusinessRuleViolationException)
    e.violations()*.code() == ['vehicle.name-unique']
    e.violations()*.clientArgs() == [[name: 'Atlas']]
  }
}
