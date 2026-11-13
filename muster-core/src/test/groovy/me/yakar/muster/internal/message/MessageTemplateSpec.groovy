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

import java.time.LocalDate
import java.time.LocalTime
import java.util.logging.Level
import java.util.logging.LogRecord
import me.yakar.muster.LogCapture
import spock.lang.Specification

class MessageTemplateSpec extends Specification {

  static final Locale TURKISH = Locale.forLanguageTag('tr')

  LogCapture capture = new LogCapture(Level.ALL)
  List<LogRecord> logged = capture.records

  def cleanup() {
    capture.close()
  }

  def 'fills "#template" as "#text"'() {
    expect:
    MessageTemplate.fill(template, [name: 'Atlas', count: 3, empty: ''], Locale.ENGLISH) == text

    where:
    template || text
    '' || ''
    'Name taken' || 'Name taken'
    '{name}' || 'Atlas'
    'Name {name} is taken' || 'Name Atlas is taken'
    '{name} has {count} trucks' || 'Atlas has 3 trucks'
    '{name}{name}' || 'AtlasAtlas'
    'empty: [{empty}]' || 'empty: []'
    'Unknown {other} stays' || 'Unknown {other} stays'
    'Empty {} stays' || 'Empty {} stays'
    'Not a name { name } stays' || 'Not a name { name } stays'
    'Unclosed {name' || 'Unclosed {name'
    'Unclosed at the end {' || 'Unclosed at the end {'
    'Close only name}' || 'Close only name}'
    'Escaped {{name}}' || 'Escaped {name}'
    'Escaped {{ and }}' || 'Escaped { and }'
    '{{{name}}}' || '{Atlas}'
    '{{{{' || '{{'
    '{{{' || '{{'
    '}}}' || '}}'
    'Nested {a{name}}' || 'Nested {aAtlas}'
    'Nested {a{{name}' || 'Nested {a{name}'
    'Unknown then known {other}}{name}' || 'Unknown then known {other}}Atlas'
    "L'utilisateur {name}" || "L'utilisateur Atlas"
  }

  def 'writes a value with String.valueOf'() {
    expect:
    MessageTemplate.fill('{value}', [value: value], TURKISH) == text

    where:
    value || text
    42L || '42'
    new BigDecimal('1.50') || '1.50'
    ['a', 'b'] || '[a, b]'
    Optional.of('x') || 'Optional[x]'
  }

  def 'an empty placeholder stays as it is, even for an argument with an empty name'() {
    expect:
    MessageTemplate.fill('Empty {} stays', ['': 'X'], Locale.ENGLISH) == 'Empty {} stays'
  }

  def 'fills an argument whose name lies outside the basic plane'() {
    expect:
    MessageTemplate.fill('Truck {name🚚}', ['name🚚': 'Atlas'], Locale.ENGLISH) == 'Truck Atlas'
  }

  def 'fills "#template" in Turkish as "#text"'() {
    given:
    def args = [
      limit: 1500,
      price: new BigDecimal('12.50'),
      day  : LocalDate.of(2026, 11, 12),
      name : 'Atlas',
    ]

    expect:
    MessageTemplate.fill(template, args, TURKISH) == text

    where:
    template || text
    'En çok {limit,number} araç' || 'En çok 1.500 araç'
    '{price,number} TL' || '12,50 TL'
    '{day,date,short} tarihine kadar' || '12.11.2026 tarihine kadar'
    '{name}: {limit,number,integer}, {day,date}' || 'Atlas: 1.500, 12 Kas 2026'
    '{{limit,number}}' || '{limit,number}'
    '{{{limit,number}}}' || '{1.500}'
    'Unknown format {limit,money} stays' || 'Unknown format {limit,money} stays'
    'Unknown style {limit,number,currency} stays' || 'Unknown style {limit,number,currency} stays'
    'Not a format {limit, number} stays' || 'Not a format {limit, number} stays'
    'Unknown name {other,number} stays' || 'Unknown name {other,number} stays'
    'No name {,number} stays' || 'No name {,number} stays'
    'Only a comma {,} stays' || 'Only a comma {,} stays'
    'No format {limit,} stays' || 'No format {limit,} stays'
  }

  def 'an argument whose whole name is the placeholder wins over a typed placeholder'() {
    expect:
    MessageTemplate.fill('{a,number}', args, TURKISH) == text

    where:
    args || text
    ['a,number': 'X'] || 'X'
    ['a,number': 'X', a: 1500] || 'X'
    [a: 1500] || '1.500'
    ['a,number,percent': 'Y'] || '{a,number}'
  }

  def 'a value that its format cannot format is written as it is, and logged at DEBUG'() {
    when:
    def text = MessageTemplate.fill('{name,number} at {at,time,long}', [name: 'Atlas', at: LocalTime.NOON], TURKISH)

    then:
    text == 'Atlas at 12:00'
    logged*.message == [
      "Cannot format java.lang.String as {name,number} in locale 'tr'; writing it as it is",
      "Cannot format java.time.LocalTime as {at,time,long} in locale 'tr'; writing it as it is",
    ]
    logged*.level == [Level.FINE, Level.FINE]
  }

  def 'a value that is formatted is not logged'() {
    when:
    MessageTemplate.fill('{limit,number}', [limit: 1500], TURKISH)

    then:
    logged.isEmpty()
  }
}
