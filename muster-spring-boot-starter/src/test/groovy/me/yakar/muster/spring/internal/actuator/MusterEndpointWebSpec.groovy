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
package me.yakar.muster.spring.internal.actuator

import me.yakar.muster.spring.fixture.actuator.Car
import me.yakar.muster.spring.fixture.actuator.GarageService
import me.yakar.muster.spring.fixture.actuator.WashService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

/**
 * The endpoint over the web, through Spring Boot's own auto-configuration of the actuator, written
 * with the actuator's JSON mapper rather than the application's.
 */
@SpringBootTest(properties = [
  'management.endpoints.web.exposure.include=muster',
  'muster.rules.garage.closed.enabled=false',
  // The settings of the application do not reach the listing: an empty list stays in it.
  'spring.jackson.default-property-inclusion=non_empty'
])
class MusterEndpointWebSpec extends Specification {

  @SpringBootConfiguration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  @AutoConfigurationPackage(basePackageClasses = Car)
  static class Application {
    @Bean
    GarageService garageService() {
      new GarageService()
    }

    @Bean
    WashService washService() {
      new WashService()
    }
  }

  @Autowired
  WebApplicationContext context

  def 'GET /actuator/muster answers the listing as JSON'() {
    given:
    context.getBean(GarageService).park(new Car('TR-1'))

    when:
    def answer = MockMvcBuilders.webAppContextSetup(context).build()
        .perform(MockMvcRequestBuilders.get('/actuator/muster'))
        .andReturn()
        .response

    then:
    answer.status == 200
    answer.contentType.startsWith('application/vnd.spring-boot.actuator')
    def body = JsonMapper.shared().readValue(answer.contentAsString, Map)
    body.mode == 'FAIL_FAST'
    body.disabled == ['garage.closed']
    body.methods*.method == [
      'GarageService.inspect(Car)',
      'GarageService.park(Plated)',
      'GarageService.parkAll(List)',
      'GarageService.register(Car)',
      'GarageService.remove(Long)',
      'WashService.wash(Car)'
    ]
    body.methods*.target[5] == WashService.name
    body.methods[1] == [
      method: 'GarageService.park(Plated)',
      target: GarageService.name,
      input: 'me.yakar.muster.spring.fixture.actuator.Plated',
      collection: false,
      mode: 'FAIL_FAST',
      rules: [],
      excluded: [],
      chains: [(Car.name): ['garage.plate-format', 'garage.plate-free']]
    ]
    body.methods[4].mode == 'COLLECT_ALL'
    body.methods[4].rules == ['garage.id-known']
    body.types == [:]
    body.collections == [:]
  }
}
