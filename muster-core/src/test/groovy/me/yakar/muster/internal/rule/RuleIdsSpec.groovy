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
package me.yakar.muster.internal.rule

import java.util.regex.Pattern
import spock.lang.Specification

class RuleIdsSpec extends Specification {

  /** The format, written as the regular expression it was first defined by. */
  static final Pattern FORMAT = Pattern.compile('[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*')

  def '"#id" is a valid rule id'() {
    expect:
    RuleIds.isValid(id)

    where:
    id << [
      'a',
      'z',
      'a0',
      'z9',
      'task.ready',
      'vehicle.name-unique',
      'plan.v2.capacity-sufficient',
      'a.1',
      'a-0z'
    ]
  }

  def '"#id" is not a valid rule id'() {
    expect:
    !RuleIds.isValid(id)

    where:
    id << [
      '',
      '0a',
      '9a',
      '`a',
      '{a',
      'Task',
      '.a',
      '-a',
      'a.',
      'a-',
      'a..b',
      'a.-b',
      'a`',
      'a{',
      'a/',
      'a:',
      'a_b',
      'a b',
      'café'
    ]
  }

  def 'every short string is valid exactly when it matches the format'() {
    given:
    def alphabet = [
      'a',
      'z',
      '0',
      '9',
      '.',
      '-',
      '_',
      'A',
      '`',
      '{',
      '/',
      ':'
    ]
    def strings = [''] + (1..3).collectMany { length ->
      ([alphabet] * length).combinations()*.join()
    }

    expect:
    strings.size() == 1 + 12 + 144 + 1728
    strings.every { RuleIds.isValid(it) == FORMAT.matcher(it).matches() }
  }
}
