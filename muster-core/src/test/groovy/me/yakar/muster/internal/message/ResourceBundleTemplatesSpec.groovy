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
package me.yakar.muster.internal.message

import spock.lang.Specification

class ResourceBundleTemplatesSpec extends Specification {

  static final Locale TURKISH = Locale.forLanguageTag('tr')
  static final Locale GERMAN = Locale.GERMAN

  Locale defaultLocale

  def setup() {
    defaultLocale = Locale.getDefault()
    // Bundles are cached by the locale asked for, so a bundle cached under another default locale
    // would hide the fallback.
    ResourceBundle.clearCache()
  }

  def cleanup() {
    Locale.setDefault(defaultLocale)
    ResourceBundle.clearCache()
  }

  static ResourceBundleTemplates messages() {
    new ResourceBundleTemplates({ Locale locale ->
      ResourceBundle.getBundle('me.yakar.muster.messages', locale)
    }, Locale.ENGLISH)
  }

  def 'reads the template of #locale from the bundle of #bundle'() {
    given:
    Locale.setDefault(Locale.ENGLISH)

    expect:
    messages().find('vehicle.name-taken', Locale.forLanguageTag(locale)).map {
      it.text()
    } == Optional.of(template)

    where:
    locale       | bundle || template
    'tr'         | 'tr' || '{name} adı kullanılıyor'
    'tr-TR'      | 'tr' || '{name} adı kullanılıyor'
    'tr-Latn-TR' | 'tr' || '{name} adı kullanılıyor'
    'de-AT'      | 'de' || 'Der Name {name} ist vergeben'
    'fr'         | 'the base'|| 'The name {name} is taken'
    'und'        | 'the base'|| 'The name {name} is taken'
  }

  def 'never falls back to the bundle of the default locale of the JVM, as getBundle does'() {
    given:
    Locale.setDefault(TURKISH)

    expect: 'getBundle answers a French client in Turkish'
    ResourceBundle.getBundle('me.yakar.muster.messages', Locale.FRENCH).locale == TURKISH

    and: 'the templates come from the base bundle'
    messages().find('vehicle.name-taken', Locale.FRENCH).map {
      it.text()
    } == Optional.of('The name {name} is taken')
    messages().find('vehicle.name-taken', Locale.forLanguageTag('fr-CA')).map {
      it.text()
    } == Optional.of('The name {name} is taken')

    and: 'the default locale itself still has its bundle'
    messages().find('vehicle.name-taken', TURKISH).map {
      it.text()
    } == Optional.of('{name} adı kullanılıyor')
  }

  def 'never falls back to the default locale #jvmLocale, which has a region, for #locale'() {
    given:
    Locale.setDefault(Locale.forLanguageTag(jvmLocale))
    def templates = new ResourceBundleTemplates({ Locale requested ->
      ResourceBundle.getBundle("me.yakar.muster.$family", requested)
    }, Locale.ROOT)

    expect: 'getBundle answers from the bundle of the default locale'
    ResourceBundle.getBundle("me.yakar.muster.$family", Locale.forLanguageTag(locale)).locale != Locale.ROOT

    and: 'the template comes from the base bundle'
    templates.find('vehicle.name-taken', Locale.forLanguageTag(locale)).map {
      it.text()
    } == Optional.of('The name {name} is taken')

    where:
    jvmLocale | family     | locale
    'tr-TR'   | 'messages' | 'fr'
    'de-DE'   | 'regional' | 'de-AT'
    'de-DE'   | 'regional' | 'de'
    'de-DE'   | 'regional' | 'fr'
  }

  def 'the bundle of a region is still found for that region'() {
    given:
    Locale.setDefault(Locale.ENGLISH)

    expect:
    new ResourceBundleTemplates({ Locale requested ->
      ResourceBundle.getBundle('me.yakar.muster.regional', requested)
    }, Locale.ROOT)
    .find('vehicle.name-taken', Locale.forLanguageTag('de-DE')).map {
      it.text()
    } == Optional.of('Der Name {name} ist in Deutschland vergeben')
  }

  def 'a key that only the base bundle has is read from it'() {
    given:
    Locale.setDefault(GERMAN)

    expect:
    messages().find('vehicle.owner-only', TURKISH).map {
      it.text()
    } == Optional.of("{name}'s owner may change it")
    messages().find('vehicle.owner-only', Locale.FRENCH).map {
      it.text()
    } == Optional.of("{name}'s owner may change it")
  }

  def 'a code that no bundle has has no template'() {
    expect:
    messages().find('vehicle.plate-format', locale).map { it.text() } == Optional.empty()

    where:
    locale << [TURKISH, Locale.FRENCH, Locale.ROOT]
  }

  def 'a family without a base bundle is rejected: default locale #jvmLocale'() {
    given:
    Locale.setDefault(Locale.forLanguageTag(jvmLocale))

    when:
    new ResourceBundleTemplates({ Locale locale ->
      ResourceBundle.getBundle('me.yakar.muster.nobase', locale)
    }, Locale.ROOT)

    then:
    def e = thrown(IllegalArgumentException)
    e.message.startsWith('the resource bundles have no base bundle (')
    e.message.endsWith('); add one without a locale, such as messages.properties, for the locales that have no bundle of their own')
    e.message.contains(detail)
    (e.cause instanceof MissingResourceException) == missing

    where:
    jvmLocale || missing | detail
    'en' || false   | "for Locale.ROOT the function returned the bundle for 'en'"
    // The rest of the message is the JDK's.
    'de' || true    | 'me.yakar.muster.nobase'
  }

  def 'needs a function that returns a bundle'() {
    when:
    new ResourceBundleTemplates(null, Locale.ROOT)

    then:
    def e = thrown(NullPointerException)
    e.message == 'bundles'

    when:
    new ResourceBundleTemplates({ Locale locale -> null }, Locale.ROOT)

    then:
    e = thrown(NullPointerException)
    e.message == "the function returned no bundle for ''"
  }

  def 'a bundle that getBundle did not load is rejected, since it does not know its locale'() {
    given:
    def handMade = new PropertyResourceBundle(new StringReader('vehicle.name-taken=Taken'))

    when:
    new ResourceBundleTemplates({ Locale locale -> handMade }, Locale.ROOT)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "the function returned a bundle for '' that ResourceBundle.getBundle did not load; return the bundles getBundle loads, or use MessageResolver.ofTemplates for templates from another source"

    when:
    def base = ResourceBundle.getBundle('me.yakar.muster.messages', Locale.ROOT)
    new ResourceBundleTemplates({ Locale locale ->
      locale == Locale.ROOT ? base : handMade
    }, Locale.ROOT)
    .find('vehicle.name-taken', TURKISH)

    then:
    e = thrown(IllegalArgumentException)
    e.message.startsWith("the function returned a bundle for 'tr' that ResourceBundle.getBundle did not load")
  }

  def 'a bundle that the function no longer returns is named'() {
    given:
    def base = ResourceBundle.getBundle('me.yakar.muster.messages', Locale.ROOT)
    def templates = new ResourceBundleTemplates({ Locale locale ->
      locale == Locale.ROOT ? base : null
    }, Locale.ROOT)

    when:
    templates.find('vehicle.name-taken', TURKISH)

    then:
    def e = thrown(NullPointerException)
    e.message == "the function returned no bundle for 'tr'"
  }

  def 'the values of #locale are formatted in #formatLocale, as the template comes from #bundle'() {
    given:
    Locale.setDefault(Locale.ENGLISH)

    expect:
    messages().find(code, Locale.forLanguageTag(locale)).map {
      it.locale()
    } == Optional.of(Locale.forLanguageTag(formatLocale))

    where:
    code                  | locale       | bundle || formatLocale
    'vehicle.name-taken'  | 'tr'         | 'tr' || 'tr'
    'vehicle.name-taken'  | 'tr-TR'      | 'tr, in the region of the call' || 'tr-TR'
    'vehicle.name-taken'  | 'de-AT'      | 'de, in the region of the call' || 'de-AT'
    'vehicle.turkish-only'| 'tr-TR'      | 'tr, which the base lacks' || 'tr-TR'
    'vehicle.name-taken'  | 'fr'         | 'the base' || 'en'
    'vehicle.name-taken'  | 'und'        | 'the base' || 'en'
    'vehicle.owner-only'  | 'tr-TR'      | 'the base, inherited' || 'en'
    'vehicle.same'        | 'de'         | 'de, the same as the base' || 'en'
    'vehicle.name-taken'  | 'en'         | 'the base, in English' || 'en'
    'vehicle.name-taken'  | 'en-GB'      | 'the base, in the region of the call' || 'en-GB'
    'vehicle.owner-only'  | 'en-US'      | 'the base, in the region of the call' || 'en-US'
  }

  def 'the values of the base bundle are formatted in the locale it is given'() {
    given:
    def templates = new ResourceBundleTemplates({ Locale locale ->
      ResourceBundle.getBundle('me.yakar.muster.regional', locale)
    }, baseLocale)

    expect:
    templates.find('vehicle.name-taken', Locale.FRENCH).map {
      it.locale()
    } == Optional.of(baseLocale)
    templates.find('vehicle.name-taken', Locale.forLanguageTag('de-DE')).map {
      it.locale()
    } == Optional.of(Locale.forLanguageTag('de-DE'))

    where:
    baseLocale << [Locale.ROOT, Locale.ENGLISH, Locale.forLanguageTag('tr')]
  }

  def 'the values of the base bundle are formatted in its locale even when the default locale has a bundle'() {
    given:
    Locale.setDefault(TURKISH)

    expect:
    messages().find('vehicle.name-taken', Locale.FRENCH).map {
      it.locale()
    } == Optional.of(Locale.ENGLISH)
  }

  def 'needs the locale of the base bundle'() {
    when:
    new ResourceBundleTemplates({ Locale locale ->
      ResourceBundle.getBundle('me.yakar.muster.messages', locale)
    }, null)

    then:
    def e = thrown(NullPointerException)
    e.message == 'baseLocale'
  }

  def 'a base bundle in #baseLocale formats a call in #locale in #formatLocale'() {
    given:
    def templates = new ResourceBundleTemplates({ Locale requested ->
      ResourceBundle.getBundle('me.yakar.muster.regional', requested)
    }, Locale.forLanguageTag(baseLocale))

    expect:
    templates.find('vehicle.name-taken', Locale.forLanguageTag(locale)).map {
      it.locale()
    } == Optional.of(Locale.forLanguageTag(formatLocale))

    where: 'the root locale belongs to no language, so a call is never in its language'
    baseLocale | locale || formatLocale
    'und'      | 'en' || 'und'
    'und'      | 'en-GB' || 'und'
    'en'       | 'en-GB' || 'en-GB'
    'en-US'    | 'en-GB' || 'en-US'
    'en-US'    | 'en-US' || 'en-US'
    'en'       | 'fr' || 'en'
    'en'       | 'und' || 'en'
  }
}
