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

import org.springframework.asm.ClassWriter
import org.springframework.asm.ConstantDynamic
import org.springframework.asm.Handle
import org.springframework.asm.Label
import org.springframework.asm.MethodVisitor
import org.springframework.asm.Type
import spock.lang.Specification

/** The operand stack of a method, followed far enough to tell the receiver of each call. */
class ReceiverTrackerSpec extends Specification {

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

  // --- helpers ---

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
}
