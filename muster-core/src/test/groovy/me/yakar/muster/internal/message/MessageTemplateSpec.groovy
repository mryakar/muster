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

class MessageTemplateSpec extends Specification {

  def 'fills "#template" as "#text"'() {
    expect:
    MessageTemplate.fill(template, [name: 'Atlas', count: 3, empty: '']) == text

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
    MessageTemplate.fill('{value}', [value: value]) == text

    where:
    value || text
    42L || '42'
    new BigDecimal('1.50') || '1.50'
    ['a', 'b'] || '[a, b]'
    Optional.of('x') || 'Optional[x]'
  }

  def 'an empty placeholder stays as it is, even for an argument with an empty name'() {
    expect:
    MessageTemplate.fill('Empty {} stays', ['': 'X']) == 'Empty {} stays'
  }

  def 'fills an argument whose name lies outside the basic plane'() {
    expect:
    MessageTemplate.fill('Truck {name🚚}', ['name🚚': 'Atlas']) == 'Truck Atlas'
  }
}
