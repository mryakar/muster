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

import static org.springframework.asm.Opcodes.*

import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.logging.Level
import me.yakar.muster.rule.RuleTarget
import me.yakar.muster.spring.LogCapture
import me.yakar.muster.spring.fixture.selfinvocation.Barn
import me.yakar.muster.spring.fixture.selfinvocation.BigShed
import me.yakar.muster.spring.fixture.selfinvocation.BigYard
import me.yakar.muster.spring.fixture.selfinvocation.Cellar
import me.yakar.muster.spring.fixture.selfinvocation.Careful
import me.yakar.muster.spring.fixture.selfinvocation.Garage
import me.yakar.muster.spring.fixture.selfinvocation.OpenLot
import me.yakar.muster.spring.fixture.selfinvocation.WineCellar
import me.yakar.muster.spring.fixture.selfinvocation.other.Lot
import me.yakar.muster.spring.fixture.selfinvocation.Saving
import me.yakar.muster.spring.fixture.selfinvocation.Stacks
import me.yakar.muster.spring.fixture.selfinvocation.Vehicle
import me.yakar.muster.spring.fixture.selfinvocation.VehicleDepot
import org.springframework.aop.support.AopUtils
import org.springframework.asm.ClassReader
import org.springframework.asm.ClassWriter
import org.springframework.asm.ConstantDynamic
import org.springframework.asm.Handle
import org.springframework.asm.Label
import org.springframework.asm.MethodVisitor
import org.springframework.asm.Type
import spock.lang.Specification

class SelfInvocationScannerSpec extends Specification {

  static final String GEN = 'gen/Gen'
  static final String V = 'Lgen/V;'
  static final int PUBLIC_STATIC = ACC_PUBLIC | ACC_STATIC
  static final Handle METAFACTORY = new Handle(H_INVOKESTATIC, 'java/lang/invoke/LambdaMetafactory', 'metafactory',
  '(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;', false)

  // --- compiled fixtures ---

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

  // --- generated bytecode ---

  def 'copies #insn as the JVM does: this ends up at the depths #depths'() {
    expect:
    depthsOfThis(pushes, { MethodVisitor mv -> mv.visitInsn(insn) }) == depths as Set

    where:
    pushes               | insn || depths
    ['T', 'a', 'a']      | POP2 || [0]
    ['T', 'L']           | POP2 || [0]
    ['T', 'a']           | DUP || [2]
    ['T', 'T', 'a']      | DUP_X1 || [1, 3]
    ['T', 'T', 'a', 'b'] | DUP_X2 || [2, 4]
    ['x', 'T', 'L', 'a'] | DUP_X2 || [3]
    ['T', 'L']           | DUP2 || [2]
    ['T', 'a', 'b']      | DUP2 || [4]
    ['T', 'T', 'L']      | DUP2_X1 || [1, 3]
    ['T', 'T', 'a', 'b'] | DUP2_X1 || [2, 5]
    ['x', 'T', 'L', 'M'] | DUP2_X2 || [3]
    ['T', 'T', 'b', 'L'] | DUP2_X2 || [2, 4]
    ['T', 'L', 'a', 'b'] | DUP2_X2 || [5]
    ['T', 'a', 'T', 'b'] | DUP2_X2 || [1, 3, 5]
    ['T', 'a']           | SWAP || [0]
    ['T']                | SWAP || [0]
    ['T']                | DUP_X2 || [0, 1]
    ['T']                | DUP2_X2 || [0, 1]
    []                   | POP2 || []
  }

  def 'sizes the values a frame adds, and keeps the values it keeps'() {
    expect:
    depthsOfThis(pushes, code) == depths as Set

    where:
    pushes          | code || depths
    ['T', 'a', 'b'] | frame(GEN, INTEGER) || [1]
    ['T']           | seq(frame(GEN, LONG, INTEGER), insn(POP), insn(POP2)) || [0]
    ['T']           | seq(frame(GEN, DOUBLE), insn(POP2)) || [0]
    ['T', 'a']      | seq(jump(GOTO), frame(GEN)) || [0]
    ['T', 'a']      | seq(insn(RETURN), frame(GEN, INTEGER)) || []
    ['T', 'a']      | seq(insn(ATHROW), frame(GEN, INTEGER)) || []
    ['T', 'a']      | frame(GEN, INTEGER, INTEGER, INTEGER) || [3]
  }

  def 'pushes a value of the size of #what'() {
    expect:
    depthsOfThis(['T'], seq(code, insn(pop))) == [0] as Set

    where:
    what                      | code                                                                              | pop
    'a long constant'         | ldc(1L)                                                                           | POP2
    'a double constant'       | ldc(1.5d)                                                                         | POP2
    'a byte constant'         | { MethodVisitor mv -> mv.visitIntInsn(BIPUSH, 5) } | POP
    'a string constant'       | ldc('s')                                                                          | POP
    'a wide dynamic constant' | ldc(new ConstantDynamic('c', 'J', METAFACTORY))                                   | POP2
    'a dynamic constant'      | ldc(new ConstantDynamic('c', 'I', METAFACTORY))                                   | POP
    'a static long field'     | { MethodVisitor mv ->
      mv.visitFieldInsn(GETSTATIC, GEN, 'f', 'J')
    }               | POP2
    'a long a static method returns' | { MethodVisitor mv ->
      mv.visitMethodInsn(INVOKESTATIC, GEN, 's', '()J', false)
    } | POP2
    'a wide input'            | { MethodVisitor mv ->
      mv.visitVarInsn(LLOAD, 1)
    }                                 | POP2
    'a double input'          | { MethodVisitor mv ->
      mv.visitVarInsn(DLOAD, 1)
    }                                 | POP2
    'a long local'            | { MethodVisitor mv ->
      mv.visitVarInsn(LLOAD, 3)
    }                                 | POP2
    'a return address'        | jump(JSR)                                                                         | POP
    'an array of arrays'      | seq(insn(ICONST_1), insn(ICONST_2), { MethodVisitor mv ->
      mv.visitMultiANewArrayInsn('[[I', 2)
    }) | POP
  }

  def 'pops what #what takes'() {
    expect:
    depthsOfThis(['T'] + pushes, code) == [0] as Set

    where:
    what                | pushes          | code
    'a table switch'    | ['a']           | { MethodVisitor mv ->
      def l = new Label(); mv.visitTableSwitchInsn(0, 0, l, l); mv.visitLabel(l)
    }
    'a lookup switch'   | ['a']           | { MethodVisitor mv ->
      def l = new Label(); mv.visitLookupSwitchInsn(l, [] as int[], [] as Label[]); mv.visitLabel(l)
    }
    'a void static call' | ['a', 'b']     | { MethodVisitor mv ->
      mv.visitMethodInsn(INVOKESTATIC, GEN, 's', '(II)V', false)
    }
    'a call of another method' | ['x', 'a'] | { MethodVisitor mv ->
      mv.visitMethodInsn(INVOKEVIRTUAL, GEN, 'o', '(I)V', false)
    }
    'a dynamic call'    | ['a', 'b']      | { MethodVisitor mv ->
      mv.visitInvokeDynamicInsn('concat', '(II)V', METAFACTORY, 'constant')
    }
    'a store'           | ['a']           | { MethodVisitor mv -> mv.visitVarInsn(ISTORE, 2) }
    'a put'             | ['x', 'a']      | { MethodVisitor mv ->
      mv.visitFieldInsn(PUTFIELD, GEN, 'f', 'I')
    }
  }

  def 'takes the first local for this until the method stores to it'() {
    expect:
    depthsOfThis(['T'], { MethodVisitor mv ->
      mv.visitVarInsn(ALOAD, 2)
      mv.visitVarInsn(ASTORE, 0)
      mv.visitVarInsn(ALOAD, 0)
    }) == [1] as Set
  }

  def 'takes a method reference to the advised method on this for a call, through #handle'() {
    expect:
    scan('m', '()V') { MethodVisitor mv ->
      mv.visitVarInsn(ALOAD, 0)
      mv.visitInvokeDynamicInsn('accept', "(L$GEN;)Ljava/util/function/Consumer;", METAFACTORY,
          Type.getType('(Ljava/lang/Object;)V'), new Handle(tag, GEN, 'save', "($V)V", tag == H_INVOKEINTERFACE), Type.getType("($V)V"))
    } == ['Gen.m() calls Gen.save(V) through this']

    where:
    handle              | tag
    'a virtual call'    | H_INVOKEVIRTUAL
    'an interface call' | H_INVOKEINTERFACE
    'a special call'    | H_INVOKESPECIAL
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

  def 'does not take a method reference #what'() {
    expect:
    scan('m', '()V', code) == []

    where:
    what                      | code
    'on another instance'     | { MethodVisitor mv ->
      mv.visitVarInsn(ALOAD, 2); mv.visitInvokeDynamicInsn('accept', "(L$GEN;)Ljava/util/function/Consumer;", METAFACTORY, new Handle(H_INVOKEVIRTUAL, GEN, 'save', "($V)V", false))
    }
    'that captures nothing'   | { MethodVisitor mv ->
      mv.visitVarInsn(ALOAD, 0); mv.visitInvokeDynamicInsn('accept', '()Ljava/util/function/Consumer;', METAFACTORY, new Handle(H_INVOKEVIRTUAL, GEN, 'save', "($V)V", false))
    }
    'to a field'              | { MethodVisitor mv ->
      mv.visitVarInsn(ALOAD, 0); mv.visitInvokeDynamicInsn('get', "(L$GEN;)Ljava/util/function/Supplier;", METAFACTORY, new Handle(H_GETFIELD, GEN, 'save', "($V)V", false))
    }
    'to a lambda of another class' | { MethodVisitor mv ->
      mv.visitVarInsn(ALOAD, 0); mv.visitInvokeDynamicInsn('run', "(L$GEN;)Ljava/lang/Runnable;", METAFACTORY, new Handle(H_INVOKESTATIC, 'gen/Other', 'lambda$m$0', "(L$GEN;)V", false))
    }
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

  def 'takes the call of the method itself for one #when'() {
    expect:
    scan(name, descriptor, code, access) == (found ? ["Gen.$label calls Gen.$label through this"] : [])

    where:
    when                          | name   | descriptor | access                   | code || found
    'with its input cast'         | 'save' | "($V)V"    | ACC_PUBLIC               | { MethodVisitor mv ->
      mv.visitVarInsn(ALOAD, 0); mv.visitVarInsn(ALOAD, 1); mv.visitTypeInsn(CHECKCAST, 'gen/V'); mv.visitMethodInsn(INVOKEVIRTUAL, GEN, 'save', "($V)V", false)
    } || false
    'with its input stored over'  | 'save' | "($V)V"    | ACC_PUBLIC               | { MethodVisitor mv ->
      mv.visitVarInsn(ALOAD, 2); mv.visitVarInsn(ASTORE, 1); mv.visitVarInsn(ALOAD, 0); mv.visitVarInsn(ALOAD, 1); mv.visitMethodInsn(INVOKEVIRTUAL, GEN, 'save', "($V)V", false)
    } || true
    'with its input incremented'  | 'take' | '(I)V'     | ACC_PUBLIC               | { MethodVisitor mv ->
      mv.visitIincInsn(1, 1); mv.visitVarInsn(ALOAD, 0); mv.visitVarInsn(ILOAD, 1); mv.visitMethodInsn(INVOKEVIRTUAL, GEN, 'take', '(I)V', false)
    } || true
    'with its input'              | 'take' | '(I)V'     | ACC_PUBLIC               | { MethodVisitor mv ->
      mv.visitIincInsn(2, 1); mv.visitVarInsn(ALOAD, 0); mv.visitVarInsn(ILOAD, 1); mv.visitMethodInsn(INVOKEVIRTUAL, GEN, 'take', '(I)V', false)
    } || false
    'with its wide input'         | 'take' | '(J)V'     | ACC_PUBLIC               | { MethodVisitor mv ->
      mv.visitVarInsn(ALOAD, 0); mv.visitVarInsn(LLOAD, 1); mv.visitMethodInsn(INVOKEVIRTUAL, GEN, 'take', '(J)V', false)
    } || false
    'in a static method'          | 'take' | '(I)V'     | PUBLIC_STATIC             | { MethodVisitor mv ->
      mv.visitVarInsn(ALOAD, 0); mv.visitVarInsn(ILOAD, 1); mv.visitMethodInsn(INVOKEVIRTUAL, GEN, 'take', '(I)V', false)
    } || false

    label = "$name(${descriptor == '(I)V' ? 'int' : descriptor == '(J)V' ? 'long' : 'V'})"
  }

  def 'describes a finding'() {
    expect:
    new SelfInvocation('A.b(C)', 'A.d(E)').toString() == 'A.b(C) calls A.d(E) through this'
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

  /** The findings of a generated class whose method runs the code, with save(V) and take(int|long) advised. */
  private static List<String> scan(String name, String descriptor, Closure<?> code, int access = ACC_PUBLIC) {
    scan(generated(V17) { ClassWriter writer -> method(writer, name, descriptor, code, access) })
  }

  private static List<String> scan(byte[] bytes) {
    def callees = ['take(I)V': 'Gen.take(int)', 'take(J)V': 'Gen.take(long)']
    callees.put("save($V)V".toString(), 'Gen.save(V)')
    (0..6).each { callees.put("p$it${probe(it)}".toString(), "p$it".toString()) }
    def found = new LinkedHashSet<SelfInvocation>()
    SelfInvocationScanner.scan(new ClassReader(bytes), callees, found, 'gen')
    found*.toString()
  }

  /**
   * The depths from the top of the stack at which {@code this} sits after the pushes and the code:
   * a method per depth probes it with a call that takes that many arguments.
   */
  private static Set<Integer> depthsOfThis(List<String> pushes, Closure<?> code) {
    def bytes = generated(V17) { ClassWriter writer ->
      (0..6).each { depth ->
        method(writer, "m$depth", '(J)V') { MethodVisitor mv ->
          pushes.each { push(mv, it) }
          code(mv)
          mv.visitMethodInsn(INVOKEVIRTUAL, GEN, "p$depth", probe(depth), false)
        }
      }
    }
    scan(bytes).collect { (it =~ /calls p(\d)/)[0][1] as Integer } as Set
  }

  private static String probe(int arguments) {
    "(${'I' * arguments})V"
  }

  private static void push(MethodVisitor mv, String value) {
    switch (value) {
      case 'T' -> mv.visitVarInsn(ALOAD, 0)
      case 'a' -> mv.visitInsn(ICONST_0)
      case 'b' -> mv.visitInsn(ICONST_1)
      case 'x' -> mv.visitInsn(ICONST_2)
      case 'L' -> mv.visitInsn(LCONST_0)
      case 'M' -> mv.visitInsn(LCONST_1)
    }
  }

  /** Runs the steps on the same method, in order. */
  private static Closure<?> seq(Closure<?>... steps) {
    { MethodVisitor mv -> steps.each { it(mv) } }
  }

  /** A jump to the next instruction. */
  private static Closure<?> jump(int opcode) {
    { MethodVisitor mv ->
      def label = new Label(); mv.visitJumpInsn(opcode, label); mv.visitLabel(label)
    }
  }

  private static Closure<?> insn(int opcode) {
    { MethodVisitor mv -> mv.visitInsn(opcode) }
  }

  private static Closure<?> ldc(Object value) {
    { MethodVisitor mv -> mv.visitLdcInsn(value) }
  }

  /** A frame whose stack holds the given types; the locals are this and a long. */
  private static Closure<?> frame(Object... stack) {
    { MethodVisitor mv -> mv.visitFrame(F_NEW, 2, [GEN, LONG] as Object[], stack.length, stack) }
  }

  private static void lambda(MethodVisitor mv, String body) {
    mv.visitVarInsn(ALOAD, 0)
    mv.visitInvokeDynamicInsn('run', "(L$GEN;)Ljava/lang/Runnable;", METAFACTORY, Type.getType('()V'), new Handle(H_INVOKESPECIAL, GEN, body, '()V', false), Type.getType('()V'))
    mv.visitInsn(POP)
  }

  private static void callSave(MethodVisitor mv) {
    mv.visitVarInsn(ALOAD, 0)
    mv.visitVarInsn(ALOAD, 1)
    mv.visitMethodInsn(INVOKEVIRTUAL, GEN, 'save', "($V)V", false)
  }

  /** A class gen.Gen with a save(V) method and the methods the closure adds. */
  private static byte[] generated(int version, Closure<?> methods = {}) {
    def writer = new ClassWriter(0)
    writer.visit(version, ACC_PUBLIC, GEN, null, 'java/lang/Object', null)
    method(writer, '<init>', '()V') { MethodVisitor mv ->
      mv.visitVarInsn(ALOAD, 0)
      mv.visitMethodInsn(INVOKESPECIAL, 'java/lang/Object', '<init>', '()V', false)
    }
    method(writer, 'save', "($V)V") {}
    methods(writer)
    writer.visitEnd()
    writer.toByteArray()
  }

  private static void method(ClassWriter writer, String name, String descriptor, Closure<?> code, int access = ACC_PUBLIC) {
    def mv = writer.visitMethod(access, name, descriptor, null, null)
    mv.visitCode()
    code(mv)
    mv.visitInsn(RETURN)
    mv.visitMaxs(16, 8)
    mv.visitEnd()
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
