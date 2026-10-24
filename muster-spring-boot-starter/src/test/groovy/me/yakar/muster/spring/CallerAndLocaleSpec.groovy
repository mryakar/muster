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

import java.security.Principal
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import me.yakar.muster.BusinessRuleViolationException
import me.yakar.muster.Muster
import me.yakar.muster.spring.fixture.call.Call
import me.yakar.muster.spring.fixture.call.ReportsCall
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.RememberMeAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextHolderStrategy
import org.springframework.security.core.context.SecurityContextImpl
import spock.lang.Specification

class CallerAndLocaleSpec extends Specification {

  static final Locale TURKISH = Locale.forLanguageTag('tr-TR')

  @Configuration(proxyBeanMethods = false)
  @AutoConfigurationPackage(basePackageClasses = ReportsCall)
  static class Application {}

  @Configuration(proxyBeanMethods = false)
  static class OwnCaller {
    @Bean
    Supplier<Principal> caller() {
      { -> { -> 'service-account' } as Principal } as Supplier<Principal>
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OwnLocale {
    @Bean
    Supplier<Locale> locale() {
      { -> Locale.JAPAN } as Supplier<Locale>
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class UnrelatedSupplier {
    @Bean
    Supplier<String> greeting() {
      { -> 'hello' } as Supplier<String>
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class RawSupplier {
    @Bean
    @SuppressWarnings('rawtypes')
    Supplier raw() {
      { -> 'a string' } as Supplier
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class WildcardSupplier {
    @Bean
    Supplier<?> wildcard() {
      { -> 'a string' } as Supplier<String>
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OwnSecurityContextStrategy {
    @Bean
    SecurityContextHolderStrategy securityContextHolderStrategy() {
      def context = new SecurityContextImpl(user('from-strategy'))
      [
        getContext       : { -> context },
        getDeferredContext: { -> { -> context } as Supplier<SecurityContext> },
        clearContext     : { -> },
        setContext       : { SecurityContext ignored -> },
        createEmptyContext: { -> new SecurityContextImpl() }
      ] as SecurityContextHolderStrategy
    }
  }

  def runner = new ApplicationContextRunner()
  .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
  .withUserConfiguration(Application)

  def cleanup() {
    SecurityContextHolder.clearContext()
    LocaleContextHolder.resetLocaleContext()
  }

  static Map<String, Object> reported(Muster muster) {
    try {
      muster.check(new Call('anything'))
      throw new AssertionError('the call passed')
    } catch (BusinessRuleViolationException e) {
      e.violations().first().clientArgs()
    }
  }

  static UsernamePasswordAuthenticationToken user(String name) {
    UsernamePasswordAuthenticationToken.authenticated(name, 'secret', AuthorityUtils.NO_AUTHORITIES)
  }

  def 'the caller is the authentication of Spring Security'() {
    given:
    SecurityContextHolder.context.authentication = user('ece')

    expect:
    runner.run { assert reported(it.getBean(Muster)).caller == 'ece' }
  }

  def 'a call that is not authenticated, or anonymously, has no caller: #authentication'() {
    given:
    SecurityContextHolder.context.authentication = authentication

    expect:
    runner.run { assert reported(it.getBean(Muster)).caller == 'none' }

    where:
    authentication << [
      null,
      new AnonymousAuthenticationToken('key', 'anonymousUser', AuthorityUtils.createAuthorityList('ROLE_ANONYMOUS')),
      UsernamePasswordAuthenticationToken.unauthenticated('bob', 'password')
    ]
  }

  def 'a caller remembered from an earlier session is a caller'() {
    given:
    SecurityContextHolder.context.authentication = new RememberMeAuthenticationToken('key', 'ece', AuthorityUtils.createAuthorityList('ROLE_USER'))

    expect:
    runner.run { assert reported(it.getBean(Muster)).caller == 'ece' }
  }

  def 'without Spring Security no call has a caller'() {
    given:
    SecurityContextHolder.context.authentication = user('ece')

    expect:
    runner.withClassLoader(new FilteredClassLoader(SecurityContextHolder)).run { context ->
      assert !context.containsBean('musterSecurityCaller')
      assert reported(context.getBean(Muster)).caller == 'none'
    }
  }

  def 'a Supplier<Principal> bean of the application replaces Spring Security'() {
    given:
    SecurityContextHolder.context.authentication = user('ece')

    expect:
    runner.withUserConfiguration(OwnCaller).run {
      assert reported(it.getBean(Muster)).caller == 'service-account'
    }
  }

  def 'the locale is the one LocaleContextHolder holds'() {
    given:
    LocaleContextHolder.locale = TURKISH

    expect:
    runner.run { assert reported(it.getBean(Muster)).locale == 'tr-TR' }
  }

  def 'outside a request the locale is the default locale of the JVM'() {
    expect:
    runner.run {
      assert reported(it.getBean(Muster)).locale == Locale.default.toLanguageTag()
    }
  }

  def 'a Supplier<Locale> bean of the application replaces LocaleContextHolder'() {
    given:
    LocaleContextHolder.locale = TURKISH

    expect:
    runner.withUserConfiguration(OwnLocale).run {
      assert reported(it.getBean(Muster)).locale == 'ja-JP'
    }
  }

  def 'a supplier of something else is not taken for the caller or the locale: #configuration.simpleName'() {
    given:
    SecurityContextHolder.context.authentication = user('ece')
    LocaleContextHolder.locale = TURKISH

    expect:
    runner.withUserConfiguration(configuration).run {
      assert reported(it.getBean(Muster)) == [caller: 'ece', locale: 'tr-TR']
    }

    where:
    configuration << [
      UnrelatedSupplier,
      RawSupplier,
      WildcardSupplier
    ]
  }

  def 'the caller comes through the SecurityContextHolderStrategy bean of the application'() {
    given:
    SecurityContextHolder.context.authentication = user('ece')

    expect:
    runner.withUserConfiguration(OwnSecurityContextStrategy).run {
      assert reported(it.getBean(Muster)).caller == 'from-strategy'
    }
  }

  def 'threads checking at once each see their own caller and locale'() {
    given:
    def threads = 16
    def start = new CountDownLatch(1)
    def pool = Executors.newFixedThreadPool(threads)
    Muster muster = null
    runner.run { muster = it.getBean(Muster) }
    def tasks = (0..<threads).collect { index -> {

        ->
        def locale = Locale.forLanguageTag(index % 2 == 0 ? 'tr-TR' : 'en-GB')
        SecurityContextHolder.context.authentication = user("user-$index")
        LocaleContextHolder.locale = locale
        start.await()
        def wrong = []
        200.times {
          def args = reported(muster)
          if (args != [caller: "user-$index".toString(), locale: locale.toLanguageTag()]) {
            wrong << args
          }
        }
        SecurityContextHolder.clearContext()
        LocaleContextHolder.resetLocaleContext()
        wrong
      } as Callable<List>
    }

    when:
    def futures = tasks.collect { pool.submit(it) }
    start.countDown()
    def mismatches = futures.collectMany { it.get(1, TimeUnit.MINUTES) }

    then:
    mismatches.isEmpty()

    cleanup:
    pool.shutdownNow()
  }
}
