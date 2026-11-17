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

import spock.lang.Specification

class TargetNamesSpec extends Specification {

  def names = TargetNames.NONE.with('name', 'vehicleName').with('address', 'shippingAddress')
  .with('address.street', 'street').with('lines', 'orderLines')

  def 'the target "#target" becomes "#renamed"'() {
    expect:
    names.rename(target) == renamed

    where:
    target || renamed
    'name' || 'vehicleName'
    'name.first' || 'vehicleName.first'
    'name[0]' || 'vehicleName[0]'
    'address' || 'shippingAddress'
    'address.city' || 'shippingAddress.city'
    'address.street' || 'street'
    'address.street.line' || 'street.line'
    'address.streets' || 'shippingAddress.streets'
    'lines[2].sku' || 'orderLines[2].sku'
    'lines[2]' || 'orderLines[2]'
    'names' || 'names'
    'nam' || 'nam'
    'firstName' || 'firstName'
    'plate' || 'plate'
    '[0].name' || '[0].name'
  }

  def 'the longest name wins, whatever the order the names were given in'() {
    given:
    def longestLast = TargetNames.NONE.with('a', 'x').with('a.b', 'y')
    def longestFirst = TargetNames.NONE.with('a.b', 'y').with('a', 'x')

    expect:
    longestLast.rename('a.b.c') == 'y.c'
    longestFirst.rename('a.b.c') == 'y.c'
    longestLast.rename('a.c') == 'x.c'
    longestFirst.rename('a.c') == 'x.c'
  }

  def 'of two names of the same length only the one that matches renames'() {
    given:
    def names = TargetNames.NONE.with('ab', 'x').with('ac', 'y')

    expect:
    names.rename('ac.d') == 'y.d'
    names.rename('ab.d') == 'x.d'
  }

  def 'names are not chained'() {
    given:
    def names = TargetNames.NONE.with('a', 'b').with('b', 'c')

    expect:
    names.rename('a') == 'b'
    names.rename('b') == 'c'
  }

  def 'without names a target stays as it is'() {
    expect:
    TargetNames.NONE.isEmpty()
    TargetNames.NONE.rename('name') == 'name'
    TargetNames.NONE.toString() == '{}'
  }

  def 'a name renames to itself, and two names rename to the same target'() {
    given:
    def names = TargetNames.NONE.with('name', 'name').with('title', 'label').with('caption', 'label')

    expect:
    names.rename('name') == 'name'
    names.rename('title') == 'label'
    names.rename('caption') == 'label'
  }

  def 'adding a name leaves the names it was added to as they are'() {
    given:
    def one = TargetNames.NONE.with('name', 'vehicleName')

    when:
    def two = one.with('plate', 'vehiclePlate')

    then:
    !one.isEmpty()
    one.toString() == '{name=vehicleName}'
    two.toString() == '{name=vehicleName, plate=vehiclePlate}'
    one.rename('plate') == 'plate'
    two.rename('plate') == 'vehiclePlate'
    TargetNames.NONE.isEmpty()
  }

  def 'the names keep the order they were given in'() {
    expect:
    names.toString() == '{name=vehicleName, address=shippingAddress, address.street=street, lines=orderLines}'
  }

  def 'a target that is renamed already is not renamed again'() {
    when:
    names.with('name', 'title')

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "target 'name' is renamed to 'vehicleName' already"
  }

  def 'a null #what is rejected'() {
    when:
    TargetNames.NONE.with(from, to)

    then:
    def e = thrown(NullPointerException)
    e.message == what

    where:
    from   | to || what
    null   | 'name' || 'from'
    'name' | null || 'to'
  }

  def 'a blank #what is rejected'() {
    when:
    TargetNames.NONE.with(from, to)

    then:
    def e = thrown(IllegalArgumentException)
    e.message == "$what must not be blank"

    where:
    from   | to || what
    ''     | 'name' || 'from'
    ' '    | 'name' || 'from'
    'name' | '' || 'to'
    'name' | '\t' || 'to'
  }
}
