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

import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared
import spock.lang.Specification

/**
 * Rules run inside the transaction of their method: the starter moves the transaction advisor one
 * step ahead of its own, however the application is configured, unless the application gives the
 * transaction advice an order of its own.
 */
class TransactionOrderSpec extends Specification {

  static final int MOVED = Ordered.LOWEST_PRECEDENCE - 1

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

  def 'rules run inside the transaction: #sources.simpleName'() {
    expect:
    probe(sources as Class[]) == [inside: [true], transactionOrder: MOVED]

    where:
    sources << [
      [],
      [EarlyService],
      [EarlyTransactionAdvisor],
      [ExplicitLowestOrder]
    ]
  }

  def 'an order the application gives the transaction advice is left alone'() {
    expect:
    probe(UserTransactionOrder) == [inside: [true], transactionOrder: 0]
  }

  def 'with the reordering switched off, the application does not start'() {
    when:
    probe([
      'muster.transaction.reorder-advisor=false'
    ], [] as Class[])

    then:
    def e = thrown(Exception)
    rootCause(e).message ==
        "the transaction advice has the order ${Ordered.LOWEST_PRECEDENCE}, which does not come before Muster's, ${Ordered.LOWEST_PRECEDENCE}, so rules could run outside the transaction; leave the order of @EnableTransactionManagement at its default for Muster to move it, or give it a lower one"
  }

  def 'with the reordering switched off, an order the application gives still works'() {
    expect:
    probe([
      'muster.transaction.reorder-advisor=false'
    ], [UserTransactionOrder] as Class[]) == [inside: [true], transactionOrder: 0]
  }

  private Map<String, Object> probe(Class<?>... sources) {
    probe([], sources)
  }

  /**
   * Calls the probed service once and returns whether its rule saw an active transaction, and the
   * order the transaction advisor ended with.
   */
  private Map<String, Object> probe(List<String> properties, Class<?>... sources) {
    def context = new SpringApplicationBuilder(([Application] + sources.toList()) as Class<?>[])
    .web(WebApplicationType.NONE)
    .properties((properties + [
      "spring.datasource.url=${postgres.jdbcUrl}".toString(),
      "spring.datasource.username=${postgres.username}".toString(),
      "spring.datasource.password=${postgres.password}".toString()
    ]) as String[])
    .run()
    try {
      context.getBean(ProbedService).call(new ProbeCall('probe'))
      [inside: TransactionProbe.IN_TRANSACTION, transactionOrder: context.getBean(BeanFactoryTransactionAttributeSourceAdvisor).order]
    } finally {
      context.close()
    }
  }

  static Throwable rootCause(Throwable failure) {
    def cause = failure
    while (cause.cause != null) {
      cause = cause.cause
    }
    cause
  }

  static class ProbedService {
    @Transactional
    @Rules(TransactionProbe)
    void call(ProbeCall call) {}
  }

  @EnableAutoConfiguration
  static class Application {
    @Bean
    ProbedService probedService() {
      new ProbedService()
    }
  }

  /** A post-processor of the application that needs the service, so the service is created early. */
  static class EarlyService {
    @Bean
    static BeanPostProcessor needsService(ProbedService service) {
      new BeanPostProcessor() {}
    }
  }

  /** A priority-ordered post-processor that needs the transaction advisor, so it is created early. */
  static class EarlyTransactionAdvisor {
    @Bean
    static NeedsTransactionAdvisor needsTransactionAdvisor(BeanFactoryTransactionAttributeSourceAdvisor advisor) {
      new NeedsTransactionAdvisor()
    }
  }

  static class NeedsTransactionAdvisor implements BeanPostProcessor, PriorityOrdered {
    @Override
    int getOrder() {
      Ordered.HIGHEST_PRECEDENCE
    }
  }

  @EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE)
  static class ExplicitLowestOrder {}

  @EnableTransactionManagement(order = 0)
  static class UserTransactionOrder {}
}
