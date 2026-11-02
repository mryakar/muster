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

import me.yakar.muster.spring.fixture.integration.transaction.ProbeCall
import me.yakar.muster.spring.fixture.integration.transaction.ProbedService
import me.yakar.muster.spring.fixture.integration.transaction.TransactionApplication
import me.yakar.muster.spring.fixture.integration.transaction.TransactionProbe
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.core.Ordered
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared
import spock.lang.Specification

/**
 * Rules run inside the transaction of their method in an application with a database: the starter
 * moves the transaction advisor of Spring Boot's auto-configuration one step ahead of its own.
 */
class TransactionOrderSpec extends Specification {

  @Shared
  PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse('postgres:18'))

  def setupSpec() {
    postgres.start()
  }

  def cleanupSpec() {
    postgres.stop()
  }

  def setup() {
    TransactionProbe.IN_TRANSACTION.clear()
  }

  def 'rules run inside a JDBC transaction of the transaction auto-configuration, which moved one step ahead'() {
    given:
    def context = new SpringApplicationBuilder(TransactionApplication)
        .web(WebApplicationType.NONE)
        .properties(
        "spring.datasource.url=${postgres.jdbcUrl}".toString(),
        "spring.datasource.username=${postgres.username}".toString(),
        "spring.datasource.password=${postgres.password}".toString())
        .run()

    when:
    context.getBean(ProbedService).call(new ProbeCall('probe'))

    then:
    TransactionProbe.IN_TRANSACTION == [true]
    context.getBean(BeanFactoryTransactionAttributeSourceAdvisor).order == Ordered.LOWEST_PRECEDENCE - 1

    cleanup:
    context?.close()
  }
}
