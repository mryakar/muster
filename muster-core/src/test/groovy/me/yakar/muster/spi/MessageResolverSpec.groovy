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
package me.yakar.muster.spi

import static me.yakar.muster.Concurrency.concurrently

import java.util.logging.Level
import java.util.logging.LogRecord
import me.yakar.muster.LogCapture
import me.yakar.muster.rule.RuleContext
import spock.lang.Specification

class MessageResolverSpec extends Specification {

  static final Map<String, String> TEMPLATES = [
    'vehicle.name-taken|tr': '{name} adı kullanılıyor',
    'vehicle.name-taken|en': 'The name {name} is taken',
  ]

  def templates = { String code, Locale locale ->
    Optional.ofNullable(TEMPLATES["$code|${locale.language}"])
  }
  def resolver = MessageResolver.ofTemplates(templates)

  LogCapture capture = new LogCapture(Level.ALL)
  List<LogRecord> logged = capture.records

  def cleanup() {
    capture.close()
  }

  def 'fills the template for the locale with the client arguments'() {
    expect:
    resolver.resolve('vehicle.name-taken', [name: 'Atlas'], locale) == message

    where:
    locale || message
    Locale.forLanguageTag('tr') || 'Atlas adı kullanılıyor'
    Locale.forLanguageTag('en-GB') || 'The name Atlas is taken'
  }

  def 'without a template the message is the code, logged at DEBUG'() {
    when:
    def message = resolver.resolve('vehicle.name-taken', [name: 'Atlas'], Locale.FRENCH)

    then:
    message == 'vehicle.name-taken'
    logged*.message == ["No message for 'vehicle.name-taken' in locale 'fr'; using the code"]
    logged*.level == [Level.FINE]
  }

  def 'a message for a found template is not logged'() {
    when:
    resolver.resolve('vehicle.name-taken', [name: 'Atlas'], Locale.ENGLISH)

    then:
    logged.isEmpty()
  }

  def 'resolves a violation from its code and client arguments only'() {
    given:
    def violation = RuleContext.forRule('vehicle.name-unique')
        .violation('vehicle.name-taken')
        .clientArg('name', 'Atlas')
        .internalArg('ownerId', 42L)
        .build()
    def seen = []
    MessageResolver spy = { String code, Map<String, ?> args, Locale locale ->
      seen << [code, args, locale]
      'message'
    }

    when:
    def message = spy.resolve(violation, Locale.ENGLISH)

    then:
    message == 'message'
    seen == [['vehicle.name-taken', [name: 'Atlas'], Locale.ENGLISH]]
  }

  def 'a template that refers to an internal argument leaves it unfilled'() {
    given:
    def violation = RuleContext.forRule('vehicle.name-unique')
        .violation('vehicle.name-taken')
        .clientArg('name', 'Atlas')
        .internalArg('ownerId', 42L)
        .build()
    def resolver = MessageResolver.ofTemplates { code, locale ->
      Optional.of('{name} belongs to {ownerId}')
    }

    expect:
    resolver.resolve(violation, Locale.ENGLISH) == 'Atlas belongs to {ownerId}'
  }

  def 'the lookup gets the code and the locale as they are'() {
    given:
    def seen = []
    def resolver = MessageResolver.ofTemplates { code, locale ->
      seen << [code, locale]
      Optional.empty()
    }

    when:
    resolver.resolve('VEHICLE_NAME_TAKEN', [:], Locale.forLanguageTag('tr-TR'))

    then:
    seen == [['VEHICLE_NAME_TAKEN', Locale.forLanguageTag('tr-TR')]]
  }

  def 'ofTemplates needs a lookup'() {
    when:
    MessageResolver.ofTemplates(null)

    then:
    def e = thrown(NullPointerException)
    e.message == 'templates'
  }

  def 'resolve needs a #name'() {
    when:
    resolver.resolve(code, args, locale)

    then:
    def e = thrown(NullPointerException)
    e.message == name

    where:
    name         | code                 | args | locale
    'code'       | null                 | [:]  | Locale.ENGLISH
    'clientArgs' | 'vehicle.name-taken' | null | Locale.ENGLISH
    'locale'     | 'vehicle.name-taken' | [:]  | null
  }

  def 'a lookup that returns null is a mistake that is named'() {
    given:
    def resolver = MessageResolver.ofTemplates { code, locale -> null }

    when:
    resolver.resolve('vehicle.name-taken', [:], Locale.ENGLISH)

    then:
    def e = thrown(NullPointerException)
    e.message == "the template lookup returned null for 'vehicle.name-taken'; return Optional.empty()"
  }

  def 'resolves messages from resource bundles'() {
    given:
    def messages = MessageResolver.ofResourceBundles { Locale locale ->
      ResourceBundle.getBundle('me.yakar.muster.messages', locale)
    }
    def violation = RuleContext.forRule('vehicle.name-unique')
        .violation('vehicle.name-taken')
        .clientArg('name', 'Atlas')
        .build()

    expect:
    messages.resolve(violation, Locale.forLanguageTag('tr-TR')) == 'Atlas adı kullanılıyor'
    messages.resolve(violation, Locale.FRENCH) == 'The name Atlas is taken'
    messages.resolve('vehicle.owner-only', [name: 'Atlas'], Locale.GERMAN) == "Atlas's owner may change it"
    messages.resolve('vehicle.plate-format', [plate: 'X'], Locale.GERMAN) == 'vehicle.plate-format'
  }

  def 'ofResourceBundles needs a base bundle'() {
    when:
    MessageResolver.ofResourceBundles { Locale locale ->
      ResourceBundle.getBundle('me.yakar.muster.nobase', locale)
    }

    then:
    thrown(IllegalArgumentException)
  }

  def 'resolves the same messages from many threads at once, while the bundles load'() {
    given: 'a default locale with a bundle, so that other locales race through the fallback too'
    def defaultLocale = Locale.getDefault()
    Locale.setDefault(Locale.forLanguageTag('tr-TR'))
    ResourceBundle.clearCache()
    def messages = MessageResolver.ofResourceBundles { Locale locale ->
      ResourceBundle.getBundle('me.yakar.muster.messages', locale)
    }
    def expected = [
      'tr'   : 'Atlas adı kullanılıyor',
      'tr-TR': 'Atlas adı kullanılıyor',
      'de-AT': 'Der Name Atlas ist vergeben',
      'fr'   : 'The name Atlas is taken',
      'en'   : 'The name Atlas is taken',
      'und'  : 'The name Atlas is taken',
    ]

    when:
    def mismatches = concurrently {
      def wrong = []
      200.times {
        expected.each { tag, message ->
          def resolved = messages.resolve('vehicle.name-taken', [name: 'Atlas'], Locale.forLanguageTag(tag))
          if (resolved != message) {
            wrong << "$tag: $resolved"
          }
        }
      }
      wrong
    }.flatten()

    then:
    mismatches.isEmpty()

    cleanup:
    Locale.setDefault(defaultLocale)
    ResourceBundle.clearCache()
  }
}
