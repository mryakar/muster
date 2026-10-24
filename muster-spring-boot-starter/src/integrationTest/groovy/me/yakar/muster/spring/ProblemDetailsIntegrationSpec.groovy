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

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.simple.JdbcClient
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

@SpringBootTest(classes = IntegrationTestApplication, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProblemDetailsIntegrationSpec extends Specification {

  @LocalServerPort
  int port

  @Autowired
  JdbcClient jdbc

  def setup() {
    jdbc.sql('create table if not exists vehicle (name text primary key)').update()
    jdbc.sql("insert into vehicle values ('Atlas') on conflict do nothing").update()
  }

  HttpResponse<String> save(String name, String language) {
    def request = HttpRequest.newBuilder(URI.create("http://localhost:$port/vehicles"))
        .header('Content-Type', 'application/json')
        .header('Accept-Language', language)
        .POST(HttpRequest.BodyPublishers.ofString(/{"name":"$name"}/))
        .build()
    HttpClient.newHttpClient().withCloseable {
      it.send(request, HttpResponse.BodyHandlers.ofString())
    }
  }

  def 'a rule that a request violates answers it with problem details, in the language of the client'() {
    when:
    def response = save('Atlas', 'tr-TR')

    then:
    response.statusCode() == 422
    response.headers().firstValue('Content-Type').get() == 'application/problem+json'
    JsonMapper.shared().readValue(response.body(), Map) == [
      title     : 'Unprocessable Content',
      status    : 422,
      instance  : '/vehicles',
      violations: [
        [code: 'vehicle.name-unique', message: 'Atlas adı alınmış', args: [name: 'Atlas']]
      ]
    ]
  }

  def 'a request that violates no rule passes'() {
    expect:
    save('Boreas', 'en').statusCode() == 200
  }
}
