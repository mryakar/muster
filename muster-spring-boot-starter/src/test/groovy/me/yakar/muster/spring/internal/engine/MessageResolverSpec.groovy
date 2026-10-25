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
package me.yakar.muster.spring.internal.engine

import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import me.yakar.muster.spi.MessageResolver
import me.yakar.muster.spring.MusterAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.StaticMessageSource
import spock.lang.Specification

class MessageResolverSpec extends Specification {

  static final Locale TURKISH = Locale.forLanguageTag('tr-TR')

  static final Map<String, Object> NAME = [name: 'Atlas']

  @Configuration(proxyBeanMethods = false)
  static class OwnResolver {
    @Bean
    MessageResolver messages() {
      { code, args, locale -> "own: $code".toString() } as MessageResolver
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OwnFormattingMessageSource {
    @Bean
    StaticMessageSource messageSource() {
      def source = new StaticMessageSource()
      source.alwaysUseMessageFormat = true
      source
    }
  }

  def runner = new ApplicationContextRunner()
  .withConfiguration(AutoConfigurations.of(MessageSourceAutoConfiguration, MusterAutoConfiguration))
  .withPropertyValues(
  'spring.messages.basename=me.yakar.muster.spring.messages',
  'spring.messages.fallback-to-system-locale=false')

  def 'the message is the template of the code in the message source, filled with the client arguments'() {
    expect:
    runner.run {
      def messages = it.getBean(MessageResolver)
      assert messages.resolve('vehicle.name-taken', NAME, Locale.ENGLISH) == "The name Atlas isn't free"
      assert messages.resolve('vehicle.name-taken', NAME, TURKISH) == 'Atlas adı kullanımda'
      assert messages.resolve('vehicle.plate-format', [plate: '34 X'], TURKISH) == 'The plate 34 X is not valid'
    }
  }

  def 'without a template the message is the code'() {
    expect:
    runner.run {
      assert it.getBean(MessageResolver).resolve('vehicle.unknown', NAME, Locale.ENGLISH) == 'vehicle.unknown'
    }
  }

  def 'without a message source of the application every message is the code'() {
    expect:
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
        .run {
          assert it.getBean(MessageResolver).resolve('vehicle.name-taken', NAME, Locale.ENGLISH) == 'vehicle.name-taken'
        }
  }

  def 'a message source that uses the code as the default message still gives the code'() {
    expect:
    runner.withPropertyValues('spring.messages.use-code-as-default-message=true').run {
      assert it.getBean(MessageResolver).resolve('vehicle.unknown', NAME, Locale.ENGLISH) == 'vehicle.unknown'
    }
  }

  def "Spring Boot's message source falls back to the default locale of the JVM unless told not to"() {
    given:
    def jvmLocale = Locale.default
    Locale.default = TURKISH

    expect:
    runner.withPropertyValues('spring.messages.fallback-to-system-locale=true').run {
      assert it.getBean(MessageResolver).resolve('vehicle.name-taken', NAME, Locale.FRENCH) == 'Atlas adı kullanımda'
    }
    runner.run {
      assert it.getBean(MessageResolver).resolve('vehicle.name-taken', NAME, Locale.FRENCH) == "The name Atlas isn't free"
    }

    cleanup:
    Locale.default = jvmLocale
  }

  def 'a MessageResolver bean of the application replaces the message source'() {
    expect:
    runner.withUserConfiguration(OwnResolver).run {
      assert it.getBeansOfType(MessageResolver).keySet() == ['messages'] as Set
      assert it.getBean(MessageResolver).resolve('vehicle.name-taken', NAME, Locale.ENGLISH) == 'own: vehicle.name-taken'
    }
  }

  def 'a message source that formats every message stops the application: #configuration'() {
    expect:
    configuration(runner).run {
      def failure = getMostSpecificCause(it.startupFailure)
      assert failure instanceof IllegalStateException
      assert failure.message == 'the message source of the application formats every message with' +
      ' MessageFormat, as spring.messages.always-use-message-format=true makes it, and MessageFormat' +
      " rejects the {name} placeholders of Muster's messages; turn that off, or define a bean of" +
      ' type MessageResolver to resolve the messages of violations'
    }

    where:
    configuration << [
      { ApplicationContextRunner it ->
        it.withPropertyValues('spring.messages.always-use-message-format=true')
      },
      { ApplicationContextRunner it ->
        it.withUserConfiguration(OwnFormattingMessageSource)
      }
    ]
  }

  def 'a MessageResolver bean of the application lets a message source format every message'() {
    expect:
    runner.withPropertyValues('spring.messages.always-use-message-format=true')
        .withUserConfiguration(OwnResolver)
        .run { assert it.startupFailure == null }
  }

  def 'threads resolving at once each get the message of their own locale'() {
    given:
    def threads = 16
    def start = new CountDownLatch(1)
    def pool = Executors.newFixedThreadPool(threads)
    MessageResolver messages = null
    def tasks = (0..<threads).collect { index -> {

        ->
        def turkish = index % 2 == 0
        def name = "vehicle-$index".toString()
        def expected = turkish ? "$name adı kullanımda".toString() : "The name $name isn't free".toString()
        start.await()
        def wrong = []
        200.times {
          def message = messages.resolve('vehicle.name-taken', [name: name], turkish ? TURKISH : Locale.ENGLISH)
          if (message != expected) {
            wrong << message
          }
        }
        wrong
      } as Callable<List>
    }

    when:
    List mismatches = null
    // The message source of the application goes with its context.
    runner.run {
      messages = it.getBean(MessageResolver)
      def futures = tasks.collect { pool.submit(it) }
      start.countDown()
      mismatches = futures.collectMany { it.get(1, TimeUnit.MINUTES) }
    }

    then:
    mismatches.isEmpty()

    cleanup:
    pool.shutdownNow()
  }
}
