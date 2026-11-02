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
package me.yakar.muster.spring.internal.selfinvocation

import static me.yakar.muster.spring.internal.selfinvocation.GeneratedClasses.*
import static org.springframework.asm.Opcodes.*

import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.logging.Level
import me.yakar.muster.rule.RuleTarget
import me.yakar.muster.spring.LogCapture
import me.yakar.muster.spring.fixture.selfinvocation.Barn
import me.yakar.muster.spring.fixture.selfinvocation.BigShed
import me.yakar.muster.spring.fixture.selfinvocation.BigYard
import me.yakar.muster.spring.fixture.selfinvocation.Careful
import me.yakar.muster.spring.fixture.selfinvocation.Cellar
import me.yakar.muster.spring.fixture.selfinvocation.Garage
import me.yakar.muster.spring.fixture.selfinvocation.OpenLot
import me.yakar.muster.spring.fixture.selfinvocation.Saving
import me.yakar.muster.spring.fixture.selfinvocation.Stacks
import me.yakar.muster.spring.fixture.selfinvocation.Vehicle
import me.yakar.muster.spring.fixture.selfinvocation.VehicleDepot
import me.yakar.muster.spring.fixture.selfinvocation.WineCellar
import me.yakar.muster.spring.fixture.selfinvocation.other.Lot
import org.springframework.aop.support.AopUtils
import org.springframework.asm.ClassWriter
import org.springframework.asm.MethodVisitor
import spock.lang.Specification

/** The scan of an advised class, its superclasses and its interfaces, for calls through this. */
class SelfInvocationScannerSpec extends Specification {

  def 'finds the calls of the advised method on this, lambdas and method references included'() {
    expect:
    findings(Garage) as Set == [
      'Garage.register(String) calls Garage.save(Vehicle) through this',
      'Garage.registerExplicitly(String) calls Garage.save(Vehicle) through this',
      'Garage.registerEither(String, boolean) calls Garage.save(Vehicle) through this',
      'Garage.saver() calls Garage.save(Vehicle) through this',
      'a lambda in Garage.registerLater(String) calls Garage.save(Vehicle) through this',
      'a lambda in Garage.registerNested(String) calls Garage.save(Vehicle) through this'
    ] as Set
  }

  def 'does not take a call through the proxy, on another instance, from a static method or of another method for one'() {
    expect:
    findings(Careful) == []
  }

  def 'finds the call of a superclass through the erased signature, but not the bridge method, nor the override calling the base with its input'() {
    expect:
    findings(VehicleDepot, 'save') == ['Depot.registerFromBase(String) calls VehicleDepot.save(Vehicle) through this']
  }

  def 'does not take a call of a method of the base that the advised method does not override: #base.simpleName'() {
    expect:
    findings(type) == []

    where:
    type       | base
    WineCellar | Cellar
    OpenLot    | Lot
  }

  def 'finds the call of a package-private method of the base that the advised method overrides'() {
    expect:
    findings(BigYard) == ['Yard.place(String) calls BigYard.save(Vehicle) through this']
  }

  def 'names a nested class by its own name'() {
    expect:
    findings(Garage.Annex) == ['Annex.register(String) calls Annex.save(Vehicle) through this']
  }

  def 'finds the calls of the default methods of an interface, and of the interfaces above it'() {
    expect:
    findings(Barn) == [
      'Saving.registerDefault(String) calls Barn.save(Vehicle) through this',
      'Registering.registerFromTop(String) calls Barn.save(Vehicle) through this'
    ]
    findings(Saving) == [
      'Saving.registerDefault(String) calls Saving.save(Vehicle) through this',
      'Registering.registerFromTop(String) calls Saving.save(Vehicle) through this'
    ]
  }

  def 'does not take a call of the method itself with its own input, which the proxy checked'() {
    expect:
    findings(BigShed, 'save', 'saveTwin', 'saveTrimmed', 'saveAgain') == [
      'BigShed.saveTwin(Vehicle) calls BigShed.save(Vehicle) through this',
      'BigShed.saveTrimmed(Vehicle) calls BigShed.saveTrimmed(Vehicle) through this'
    ]
  }

  def 'follows the stack through every move javac makes'() {
    expect:
    findings(Stacks, 'save', 'guarded')*.replaceFirst(/ calls Stacks\.save\(Vehicle\) through this$/, '') == [
      'Stacks.compound(int)',
      'Stacks.wideCompound(int)',
      'Stacks.wideField()',
      'Stacks.assignment()',
      'Stacks.discarding()',
      'Stacks.arithmetic(long, double, float, int, short, byte, char)',
      'Stacks.switching(int, String)',
      'Stacks.guarded(Vehicle)',
      'Stacks.finallySaving(String)',
      'Stacks.arrays(Object)',
      'Stacks.looping(int)'
    ]
  }

  def 'has nothing to scan in a proxy based on interfaces, or in a class of the JDK'() {
    expect:
    SelfInvocationScanner.find(Proxy.getProxyClass(Saving.classLoader, Saving), [:]) == []
    // ArrayList and AbstractList both implement List, which the hierarchy holds once.
    SelfInvocationScanner.find(ArrayList, [:]) == []
  }

  def 'skips a class whose class file is #skipped, and says so'() {
    given:
    def logs = new LogCapture()
    def served = served(classFile)

    when:
    def found = SelfInvocationScanner.find(served, [(served.getMethod('save', served.classLoader.loadClass('gen.V'))): 'Gen.save(V)'])

    then:
    found == []
    logs.records*.level == [Level.FINE]
    logs.messages.first().contains(message)

    cleanup:
    logs.close()

    where:
    skipped             | classFile || message
    'missing'           | {
      null
    } || 'cannot find the class file of gen.Gen'
    'older than Java 7' | {
      new ByteArrayInputStream(generated(V1_6))
    } || 'does not scan gen.Gen, which is compiled for a Java older than 7'
    'not a class file'  | {
      new ByteArrayInputStream([1, 2, 3] as byte[])
    } || 'cannot read the class file of gen.Gen'
    'unreadable'        | {
      new InputStream() {
            int read() {
              throw new IOException('torn')
            }
          }
    } || 'cannot read the class file of gen.Gen'
  }

  def 'scans a class compiled for Java 7, the first with stack map frames'() {
    given:
    def served = served({
      new ByteArrayInputStream(generated(V1_7) { ClassWriter writer ->
        method(writer, 'm', '()V') { MethodVisitor mv ->
          callSave(mv)
        }
      })
    })

    expect:
    SelfInvocationScanner.find(served, [(served.getMethod('save', served.classLoader.loadClass('gen.V'))): 'Gen.save(V)'])*.toString() ==
    ['Gen.m() calls Gen.save(V) through this']
  }

  def 'skips a method a compiler made up, unless it is the body of a lambda: #name'() {
    expect:
    scan(generated(V17) { ClassWriter writer ->
      method(writer, name, "($V)V", { MethodVisitor mv ->
        callSave(mv)
      }, ACC_PUBLIC | ACC_SYNTHETIC)
    }) == found

    where:
    name || found
    'super$2$save' || []
    'access$000' || []
    'lambda$m$0' || ['Gen.lambda$m$0(V) calls Gen.save(V) through this']
    'm$lambda$0' || ['Gen.m$lambda$0(V) calls Gen.save(V) through this']
  }

  def 'does not take a call of a private method of the class, whatever its name, but one of another owner'() {
    expect:
    scan(generated(V17) { ClassWriter writer ->
      method(writer, 'save', "($V)V", {}, ACC_PRIVATE)
      method(writer, 'm', '()V') { MethodVisitor mv -> callSave(mv) }
      method(writer, 'n', '()V') { MethodVisitor mv ->
        mv.visitVarInsn(ALOAD, 0)
        mv.visitVarInsn(ALOAD, 1)
        mv.visitMethodInsn(INVOKEVIRTUAL, 'gen/Base', 'save', "($V)V", false)
      }
    }) == ['Gen.n() calls Gen.save(V) through this']
  }

  def 'names a constructor after the class'() {
    expect:
    scan('<init>', '(I)V') { MethodVisitor mv ->
      callSave(mv)
    } == ['Gen(int) calls Gen.save(V) through this']
  }

  def 'names the body of a lambda after the method that creates it, even when the lambdas create each other'() {
    given:
    def bytes = generated(V17) { ClassWriter writer ->
      method(writer, 'm', '()V') { MethodVisitor mv -> lambda(mv, 'lambda$m$0') }
      method(writer, 'lambda$m$0', '()V') { MethodVisitor mv -> lambda(mv, 'lambda$m$1') }
      method(writer, 'lambda$m$1', '()V') { MethodVisitor mv ->
        lambda(mv, 'lambda$m$0'); callSave(mv)
      }
    }

    expect:
    scan(bytes) == ['a lambda in Gen.lambda$m$1() calls Gen.save(V) through this']
  }

  // --- helpers ---

  /** The findings of a class, with the given methods advised, {@code save} if none is named. */
  private static List<String> findings(Class<?> type, String... advised) {
    def names = advised ?: ['save'] as String[]
    def methods = type.methods.findAll {
      it.name in names && it.parameterCount == 1 && RuleTarget.isAssignableFrom(it.parameterTypes[0])
    }
    def labels = new LinkedHashMap<Method, String>()
    methods.each {
      def specific = AopUtils.getMostSpecificMethod(it, type)
      labels.put(it, "${type.simpleName}.${specific.name}(${specific.parameterTypes*.simpleName.join(', ')})".toString())
    }
    SelfInvocationScanner.find(type, labels)*.toString()
  }

  /** A class defined from generated bytes, whose class file the loader serves from the closure. */
  private static Class<?> served(Closure<InputStream> classFile) {
    def gen = generated(V17)
    def v = new ClassWriter(0).with {
      visit(V17, ACC_PUBLIC, 'gen/V', null, 'java/lang/Object', null)
      visitEnd()
      toByteArray()
    }
    def loader = new ClassLoader(SelfInvocationScannerSpec.classLoader) {
          protected Class<?> findClass(String name) {
            def bytes = name == 'gen.Gen' ? gen: name == 'gen.V' ? v : null
            if (bytes == null) {
              throw new ClassNotFoundException(name)
            }
            defineClass(name, bytes, 0, bytes.length)
          }

          InputStream getResourceAsStream(String name) {
            classFile()
          }
        }
    loader.loadClass('gen.Gen')
  }
}
