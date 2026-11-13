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

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import spock.lang.Specification

class ValueFormatSpec extends Specification {

  static final Locale TURKISH = Locale.forLanguageTag('tr')
  static final LocalDate DAY = LocalDate.of(2026, 11, 12)
  static final LocalTime TIME = LocalTime.of(14, 30)
  static final LocalDateTime DAY_TIME = LocalDateTime.of(DAY, TIME)
  static final ZonedDateTime ZONED = ZonedDateTime.of(DAY_TIME, ZoneId.of('Europe/Istanbul'))
  static final OffsetDateTime OFFSET = OffsetDateTime.of(DAY_TIME, ZoneOffset.ofHours(3))

  static String format(String format, Object value, Locale locale = Locale.ENGLISH) {
    ValueFormat.parse(format).format(value, locale)
  }

  def '"#format" is no format'() {
    expect:
    ValueFormat.parse(format) == null

    where:
    format << [
      '',
      'Number',
      'money',
      ' number',
      'number ',
      'number,',
      'number,currency',
      'number,Integer',
      'number,integer,x',
      'number,#.##',
      'date,',
      'date,SHORT',
      'date,short,x',
      'date,dd.MM.yyyy',
      'time,',
      'time,tiny',
      'datetime,',
      'datetime,huge',
      'date time',
    ]
  }

  def 'formats #value as "#format" in #locale: #text'() {
    expect:
    format(format, value, Locale.forLanguageTag(locale)) == text

    where:
    format           | value                                  | locale || text
    'number'         | 1234L                                  | 'en' || '1,234'
    'number'         | 1234L                                  | 'tr' || '1.234'
    'number'         | 1234567.891d                           | 'en' || '1,234,567.891'
    'number'         | 1234567.891d                           | 'tr' || '1.234.567,891'
    'number'         | 0.12345d                               | 'en' || '0.123'
    'number'         | 7                                      | 'en' || '7'
    'number'         | new BigInteger('12345678901234567890') | 'en' || '12,345,678,901,234,567,890'
    'number'         | new BigDecimal('12.50')                | 'en' || '12.50'
    'number'         | new BigDecimal('12.50')                | 'tr' || '12,50'
    'number'         | new BigDecimal('0.12345')              | 'en' || '0.12345'
    'number'         | new BigDecimal('1234')                 | 'en' || '1,234'
    'number'         | new BigDecimal('1E+3')                 | 'en' || '1,000'
    'number'         | 1234L                                  | 'und' || '1,234'
    'number,integer' | 12.5d                                  | 'en' || '12'
    'number,integer' | 13.5d                                  | 'en' || '14'
    'number,integer' | new BigDecimal('12.50')                | 'en' || '12'
    'number,integer' | 1234L                                  | 'tr' || '1.234'
    'number,percent' | 0.15d                                  | 'en' || '15%'
    'number,percent' | 0.15d                                  | 'tr' || '%15'
    'number,percent' | new BigDecimal('0.155')                | 'en' || '16%'
    'date'           | DAY                                    | 'en' || 'Nov 12, 2026'
    'date'           | DAY                                    | 'tr' || '12 Kas 2026'
    'date,short'     | DAY                                    | 'en' || '11/12/26'
    'date,short'     | DAY                                    | 'en-GB'|| '12/11/2026'
    'date,short'     | DAY                                    | 'tr' || '12.11.2026'
    'date,short'     | DAY                                    | 'und' || '2026-11-12'
    'date,medium'    | DAY                                    | 'en' || 'Nov 12, 2026'
    'date,long'      | DAY                                    | 'en' || 'November 12, 2026'
    'date,full'      | DAY                                    | 'en' || 'Thursday, November 12, 2026'
    'date,full'      | DAY                                    | 'tr' || '12 Kasım 2026 Perşembe'
    'date'           | DAY_TIME                               | 'tr' || '12 Kas 2026'
    'date,long'      | ZONED                                  | 'tr' || '12 Kasım 2026'
    'date,short'     | OFFSET                                 | 'tr' || '12.11.2026'
    'time'           | TIME                                   | 'tr' || '14:30:00'
    'time,short'     | TIME                                   | 'tr' || '14:30'
    'time,medium'    | TIME                                   | 'en-GB'|| '14:30:00'
    'time,short'     | OffsetTime.of(TIME, ZoneOffset.UTC)    | 'tr' || '14:30'
    'time,short'     | DAY_TIME                               | 'tr' || '14:30'
    'time'           | ZONED                                  | 'tr' || '14:30:00'
    'datetime'       | DAY_TIME                               | 'tr' || '12 Kas 2026 14:30:00'
    'datetime,short' | DAY_TIME                               | 'tr' || '12.11.2026 14:30'
    'datetime,short' | DAY_TIME                               | 'en-GB'|| '12/11/2026, 14:30'
    'datetime,medium'| OFFSET                                 | 'tr' || '12 Kas 2026 14:30:00'
  }

  def 'a time in the #format style needs a zone ID, and writes its name'() {
    expect: 'the name of the zone depends on the JDK'
    format(format, ZONED, TURKISH).startsWith(text)

    and: 'an offset is no zone ID, so there is nothing to write'
    format(format, OFFSET, TURKISH) == null
    format(format, value, TURKISH) == null

    where:
    format          | value || text
    'time,long'     | TIME || '14:30:00 '
    'time,full'     | TIME || '14:30:00 '
    'datetime,long' | DAY_TIME || '12 Kasım 2026 14:30:00 '
    'datetime,full' | DAY_TIME || '12 Kasım 2026 Perşembe 14:30:00 '
  }

  def 'cannot format #value as "#format"'() {
    expect:
    format(format, value) == null

    where:
    format           | value
    'number'         | '1234'
    'number,integer' | DAY
    'number,percent' | true
    'date'           | 20261112L
    'date'           | '2026-11-12'
    'date'           | new Date()
    'date'           | Instant.parse('2026-11-12T11:30:00Z')
    'date'           | TIME
    'date'           | YearMonth.of(2026, 11)
    'time'           | DAY
    'time'           | Instant.parse('2026-11-12T11:30:00Z')
    'datetime'       | DAY
    'datetime'       | TIME
    'datetime'       | Instant.parse('2026-11-12T11:30:00Z')
  }

  def 'a BigDecimal with up to 1,000 digits before or after its point is formatted as "#format"'() {
    given:
    def large = new BigDecimal(BigInteger.ONE, -999)
    def small = new BigDecimal(BigInteger.ONE, 1000)

    expect:
    ValueFormat.MAX_DIGITS == 1000
    format(format, large).replace(',', '') ==~ /1${'0' * 999}(00)?%?/
    format(format, small) != null

    where:
    format << ['number', 'number,integer', 'number,percent']
  }

  def 'a BigDecimal with more than 1,000 digits before or after its point is not formatted as "#format": #value'() {
    expect:
    format(format, value) == null

    where:
    [format, value] << [
      ['number', 'number,integer', 'number,percent'],
      [
        new BigDecimal(BigInteger.ONE, -1000),
        new BigDecimal(BigInteger.ONE, 1001),
        new BigDecimal('1E+10000000'),
        new BigDecimal('1E-10000000'),
      ]
    ].combinations()
  }

  def 'the small BigDecimal keeps all of its 1,000 fraction digits'() {
    expect:
    format('number', new BigDecimal(BigInteger.ONE, 1000)) == '0.' + '0' * 999 + '1'
  }
}
