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

import static me.yakar.muster.spring.Concurrency.concurrently
import static org.springframework.core.NestedExceptionUtils.getMostSpecificCause

import tools.jackson.databind.json.JsonMapper
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Supplier
import java.util.logging.Level
import me.yakar.muster.rule.Category
import me.yakar.muster.rule.RuleContext
import me.yakar.muster.rule.Violation
import me.yakar.muster.spi.MessageResolver
import me.yakar.muster.spring.MusterAutoConfiguration
import me.yakar.muster.spring.fixture.web.CatchAllAdvice
import me.yakar.muster.spring.fixture.web.LaterErrorsAutoConfiguration
import me.yakar.muster.spring.fixture.web.ListingAdvice
import me.yakar.muster.spring.fixture.web.LocalHandlerController
import me.yakar.muster.spring.fixture.web.NamingAdvice
import me.yakar.muster.spring.fixture.web.OrderedCatchAllAdvice
import me.yakar.muster.spring.fixture.web.ParameterAdvice
import me.yakar.muster.spring.fixture.web.UnrelatedAdvice
import me.yakar.muster.spring.fixture.web.VehicleController
import me.yakar.muster.spring.fixture.web.Violations
import me.yakar.muster.spring.fixture.web.WrapperAdvice
import org.aopalliance.intercept.MethodInterceptor
import org.springframework.aop.support.DefaultPointcutAdvisor
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletAutoConfiguration
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.servlet.DispatcherServlet
import me.yakar.muster.spring.LogCapture
import spock.lang.Specification

class ProblemDetailsSpec extends Specification {

  @Configuration(proxyBeanMethods = false)
  @Import([VehicleController, LocalHandlerController])
  static class Web {
    @Bean
    Violations violations() {
      new Violations()
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OwnLocale {
    @Bean
    Supplier<Locale> locale() {
      { -> Locale.forLanguageTag('tr-TR') } as Supplier<Locale>
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OwnResolver {
    @Bean
    MessageResolver messages() {
      { code, args, locale -> "own: $code".toString() } as MessageResolver
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class AdviceFromBeanMethod {
    @Bean
    NamingAdvice namingAdvice() {
      new NamingAdvice()
    }
  }

  /** Proxies every controller advice, as an aspect that logs exception handlers would. */
  @Configuration(proxyBeanMethods = false)
  static class ProxiedAdvice {
    static final List<String> calls = new CopyOnWriteArrayList<>()

    @Bean
    static DefaultAdvisorAutoProxyCreator proxyCreator() {
      new DefaultAdvisorAutoProxyCreator()
    }

    @Bean
    static DefaultPointcutAdvisor adviceAdvisor() {
      new DefaultPointcutAdvisor(
          new AnnotationMatchingPointcut(RestControllerAdvice), { invocation ->
            calls << invocation.method.name; invocation.proceed()
          } as MethodInterceptor)
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ComponentScan(basePackageClasses = MusterAutoConfiguration, useDefaultFilters = false,
  includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ViolationExceptionHandler))
  static class ScansMuster {}

  def runner = new WebApplicationContextRunner()
  .withConfiguration(AutoConfigurations.of(
      DispatcherServletAutoConfiguration,
      WebMvcAutoConfiguration,
      HttpMessageConvertersAutoConfiguration,
      JacksonAutoConfiguration,
      MessageSourceAutoConfiguration,
      MusterAutoConfiguration))
  .withUserConfiguration(Web)
  .withPropertyValues(
  'spring.messages.basename=me.yakar.muster.spring.messages',
  'spring.messages.fallback-to-system-locale=false')

  static Violation.Builder violation(String code) {
    RuleContext.forRule('vehicle.name-unique').violation(code)
  }

  static Violation nameTaken() {
    violation('vehicle.name-taken').clientArg('name', 'Atlas').target('name').category(Category.CONFLICT).build()
  }

  static Violation withCategory(Category category) {
    def builder = violation('vehicle.name-taken').clientArg('name', 'Atlas')
    category == null ? builder.build() : builder.category(category).build()
  }

  /** Throws the violations from a controller and returns the response: its status, type and body. */
  static Map answer(WebApplicationContext context, List<Violation> violations, Map<String, String> headers = [:], String path = '/vehicles') {
    context.getBean(Violations).current = violations
    answer(MockMvcBuilders.webAppContextSetup(context).build(), headers, path)
  }

  static Map answer(MockMvc mvc, Map<String, String> headers = [:], String path = '/vehicles') {
    def request = MockMvcRequestBuilders.post(path)
    headers.each { name, value -> request.header(name, value) }
    def response = mvc.perform(request).andReturn().response
    def text = response.getContentAsString(StandardCharsets.UTF_8)
    [
      status: response.status,
      type  : response.contentType,
      body  : text.startsWith('{') ? JsonMapper.shared().readValue(text, Map) : text
    ]
  }

  def 'a violation becomes problem details with the status of its category'() {
    expect:
    runner.run {
      def answer = answer(it, [nameTaken()])
      assert answer.status == 409
      assert answer.type == 'application/problem+json'
      // No type means about:blank (RFC 9457), as in Spring's own problem details.
      assert answer.body == [
        title     : 'Conflict',
        status    : 409,
        instance  : '/vehicles',
        violations: [
          [
            code    : 'vehicle.name-taken',
            message : "The name Atlas isn't free",
            target  : 'name',
            category: 'CONFLICT',
            args    : [name: 'Atlas']
          ]
        ]
      ]
      assert answer.body.violations.first().keySet() as List == [
        'code',
        'message',
        'target',
        'category',
        'args'
      ]
    }
  }

  def 'each category has its status: #category'() {
    expect:
    runner.run {
      def answer = answer(it, [
        withCategory(category),
        withCategory(category)
      ])
      assert answer.status == status
      assert answer.body.title == title
      assert answer.body.status == status
    }

    where:
    category               | status | title
    Category.FORBIDDEN     | 403    | 'Forbidden'
    Category.NOT_FOUND     | 404    | 'Not Found'
    Category.CONFLICT      | 409    | 'Conflict'
    Category.INVALID_STATE | 422    | 'Unprocessable Content'
    null                   | 422    | 'Unprocessable Content'
  }

  def 'violations of different categories are answered with 422: #categories'() {
    expect:
    runner.run {
      def answer = answer(it, categories.collect { withCategory(it) })
      assert answer.status == 422
      assert answer.body.violations*.category == categories*.name()
    }

    where:
    categories << [
      [
        Category.CONFLICT,
        Category.NOT_FOUND
      ],
      [Category.CONFLICT, null],
      [null, Category.FORBIDDEN],
      [
        Category.CONFLICT,
        Category.CONFLICT,
        Category.INVALID_STATE
      ]
    ]
  }

  def 'muster.problem-details.mixed-status sets the status of violations of different categories'() {
    expect:
    runner.withPropertyValues('muster.problem-details.mixed-status=400').run {
      assert answer(it, [
        withCategory(Category.CONFLICT),
        withCategory(null)
      ]).status == 400
      assert answer(it, [
        withCategory(Category.CONFLICT),
        withCategory(Category.CONFLICT)
      ]).status == 409
    }
  }

  def 'a mixed status that is no client error stops the application: #value'() {
    expect:
    runner.withPropertyValues("muster.problem-details.mixed-status=$value").run {
      def failure = getMostSpecificCause(it.startupFailure)
      assert failure instanceof IllegalStateException
      assert failure.message == "muster.problem-details.mixed-status is $value; set it to a status code from 400 to 499, since a violation is an error of the client"
    }

    where:
    value << [399, 500, 200]
  }

  def 'a mixed status of 400 or 499 is a client error'() {
    expect:
    runner.withPropertyValues("muster.problem-details.mixed-status=$value").run {
      assert answer(it, [
        withCategory(Category.CONFLICT),
        withCategory(null)
      ]).status == value
    }

    where:
    value << [400, 499]
  }

  def 'a mixed status set to nothing or to no number stops the application: "#value"'() {
    expect:
    runner.withPropertyValues("muster.problem-details.mixed-status=$value").run {
      assert it.startupFailure != null
      assert getMostSpecificCause(it.startupFailure).message.contains(message)
    }

    where:
    value  | message
    ''     | 'muster.problem-details.mixed-status is set to nothing; set it to a status code from 400 to 499'
    'many' | 'many'
  }

  def 'a violation without target or category has neither, and no client arguments are an empty object'() {
    expect:
    runner.run {
      def entry = answer(it, [
        violation('vehicle.plate-format').build()
      ]).body.violations.first()
      assert entry == [code: 'vehicle.plate-format', message: 'The plate {plate} is not valid', args: [:]]
    }
  }

  def 'client arguments keep their order and their JSON types; internal arguments, the rule id and the severity stay inside'() {
    given:
    def violation = violation('vehicle.name-taken')
        .clientArg('name', 'Atlas')
        .clientArg('count', 3)
        .clientArg('active', true)
        .internalArg('conflictingId', 'secret-42')
        .build()

    expect:
    runner.run {
      def answer = answer(it, [violation])
      def args = answer.body.violations.first().args
      assert args == [name: 'Atlas', count: 3, active: true]
      assert args.keySet() as List == ['name', 'count', 'active']
      assert !answer.toString().contains('secret-42')
      assert !answer.toString().contains('conflictingId')
      assert !answer.toString().contains('vehicle.name-unique')
      assert !answer.toString().toLowerCase().contains('severity')
    }
  }

  def 'the entries keep their fields whatever the JSON settings leave out: #inclusion'() {
    expect:
    runner.withPropertyValues(
        "spring.jackson.default-property-inclusion=$inclusion",
        'spring.jackson.mapper.sort-properties-alphabetically=true').run {
          def entries = answer(it, [
            violation('vehicle.plate-format').build(),
            violation('vehicle.name-taken').clientArg('name', '').clientArg('count', 0).build()
          ]).body.violations
          assert entries[0] == [code: 'vehicle.plate-format', message: 'The plate {plate} is not valid', args: [:]]
          assert entries[1].args == [name: '', count: 0]
          assert entries*.keySet()*.toList() == [
            ['code', 'message', 'args'],
            ['code', 'message', 'args']
          ]
        }

    where:
    inclusion << [
      'always',
      'non_null',
      'non_absent',
      'non_default',
      'non_empty'
    ]
  }

  def 'violations come in the order the exception lists them'() {
    expect:
    runner.run {
      def answer = answer(it, [
        violation('vehicle.plate-format').build(),
        nameTaken()
      ])
      assert answer.body.violations*.code == [
        'vehicle.plate-format',
        'vehicle.name-taken'
      ]
    }
  }

  def 'messages are in the locale of the request'() {
    expect:
    runner.run {
      assert answer(it, [nameTaken()], ['Accept-Language': 'tr-TR']).body.violations.first().message == 'Atlas adı kullanımda'
      assert answer(it, [nameTaken()], ['Accept-Language': 'fr']).body.violations.first().message == "The name Atlas isn't free"
    }
  }

  def 'messages are in the locale that a Supplier<Locale> bean of the application gives, as rules are'() {
    expect:
    runner.withUserConfiguration(OwnLocale).run {
      assert answer(it, [nameTaken()], ['Accept-Language': 'en']).body.violations.first().message == 'Atlas adı kullanımda'
    }
  }

  def 'a MessageResolver bean of the application resolves the messages'() {
    expect:
    runner.withUserConfiguration(OwnResolver).run {
      assert answer(it, [nameTaken()]).body.violations.first().message == 'own: vehicle.name-taken'
    }
  }

  def 'a controller advice of the application that names the exception replaces the handler: #advice.simpleName'() {
    expect:
    runner.withUserConfiguration(advice).run {
      assert !it.containsBean('musterViolationExceptionHandler')
      assert answer(it, [nameTaken()]) == [status: 400, type: 'text/plain;charset=UTF-8', body: 'own']
    }

    where:
    advice << [
      NamingAdvice,
      ParameterAdvice,
      ListingAdvice,
      AdviceFromBeanMethod
    ]
  }

  def 'a controller advice that names the exception replaces the handler also from an auto-configuration after Muster\'s'() {
    expect:
    runner.withConfiguration(AutoConfigurations.of(LaterErrorsAutoConfiguration)).run {
      assert !it.containsBean('musterViolationExceptionHandler')
      assert answer(it, [nameTaken()]).body == 'own'
    }
  }

  def 'a controller advice that names the exception replaces the handler also from a parent context'() {
    given:
    def parent = new AnnotationConfigApplicationContext(NamingAdvice)

    expect:
    runner.withParent(parent).run {
      assert !it.containsBean('musterViolationExceptionHandler')
      assert answer(it, [nameTaken()]).body == 'own'
    }

    cleanup:
    parent.close()
  }

  def 'a parent context without such an advice leaves the handler in place'() {
    given:
    def parent = new AnnotationConfigApplicationContext(CatchAllAdvice)

    expect:
    runner.withParent(parent).run {
      assert answer(it, [nameTaken()]).status == 409
    }

    cleanup:
    parent.close()
  }

  def 'a controller advice ordered before the handler that handles every exception answers violations'() {
    expect:
    runner.withUserConfiguration(OrderedCatchAllAdvice).run {
      assert it.containsBean('musterViolationExceptionHandler')
      assert answer(it, [nameTaken()]).body == 'error'
    }
  }

  def 'an exception that a violation causes is answered with the problem details of the violation'() {
    expect:
    runner.withUserConfiguration(WrapperAdvice).run {
      def answer = answer(it, [nameTaken()], [:], '/wrapped')
      assert answer.status == 409
      assert answer.body.violations*.code == ['vehicle.name-taken']
    }
  }

  def 'an advisor of the application can proxy the handler'() {
    given:
    ProxiedAdvice.calls.clear()

    expect:
    runner.withUserConfiguration(ProxiedAdvice).run {
      assert it.startupFailure == null
      assert AopUtils.isAopProxy(it.getBean('musterViolationExceptionHandler'))
      assert answer(it, [nameTaken()]).status == 409
      assert ProxiedAdvice.calls == ['handle']
    }
  }

  def 'a controller advice of the application that handles every exception does not answer violations'() {
    expect:
    runner.withUserConfiguration(CatchAllAdvice).run {
      assert it.containsBean('musterViolationExceptionHandler')
      assert answer(it, [nameTaken()]).status == 409
    }
  }

  def 'a controller advice of the application for other exceptions leaves the handler in place'() {
    expect:
    runner.withUserConfiguration(UnrelatedAdvice).run {
      assert answer(it, [nameTaken()]).status == 409
    }
  }

  def "a controller's own handler of the exception comes first"() {
    expect:
    runner.run {
      assert answer(it, [nameTaken()], [:], '/local') == [status: 418, type: 'text/plain;charset=UTF-8', body: 'local']
    }
  }

  def 'a bean whose type is not known yet does not stop the search for a handler'() {
    given:
    def typeless = new GenericBeanDefinition()
    typeless.lazyInit = true

    expect:
    runner.withInitializer {
      it.registerBeanDefinition('typeless', typeless)
    }.run {
      assert it.containsBean('musterViolationExceptionHandler')
    }
  }

  def 'an application that scans the packages of Muster for components does not register the handler again'() {
    expect:
    runner.withUserConfiguration(ScansMuster).run {
      assert it.startupFailure == null
      assert it.getBeanNamesForType(ViolationExceptionHandler) as List == [
        'musterViolationExceptionHandler'
      ]
    }
  }

  def 'only a Spring MVC application has the handler'() {
    expect:
    configured.run { context ->
      assert context.startupFailure == null
      assert !context.containsBean('musterViolationExceptionHandler')
    }

    where:
    configured << [
      new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(MusterAutoConfiguration)),
      new ReactiveWebApplicationContextRunner().withConfiguration(AutoConfigurations.of(MusterAutoConfiguration)),
      new WebApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(MusterAutoConfiguration))
      .withClassLoader(new FilteredClassLoader(DispatcherServlet))
    ]
  }

  def 'answered violations are logged at DEBUG'() {
    given:
    def logs = new LogCapture()
    def violation = nameTaken()

    when:
    runner.run { answer(it, [violation]) }

    then:
    logs.records.any {
      it.level == Level.FINE && it.message == "Answering 409 to the business rule violations [$violation]".toString()
    }

    cleanup:
    logs.close()
  }

  def 'requests answered at once each get the messages of their own locale'() {
    expect:
    runner.run { context ->
      context.getBean(Violations).current = [nameTaken()]
      def mvc = MockMvcBuilders.webAppContextSetup(context).build()
      def wrong = concurrently { index ->
        def turkish = index % 2 == 0
        def expected = turkish ? 'Atlas adı kullanımda' : "The name Atlas isn't free"
        (1..50).collect {
          answer(mvc, ['Accept-Language': turkish ? 'tr-TR' : 'en']).body.violations.first().message
        }.findAll { it != expected }
      }.flatten()
      assert wrong.isEmpty()
    }
  }
}
