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

import org.springframework.asm.ClassReader
import org.springframework.asm.ClassWriter
import org.springframework.asm.Handle
import org.springframework.asm.MethodVisitor
import org.springframework.asm.Type

/**
 * Builds and scans a class {@code gen.Gen} written with the ASM, for the cases that javac does not
 * write.
 */
final class GeneratedClasses {

  static final String GEN = 'gen/Gen'
  static final String V = 'Lgen/V;'
  static final int PUBLIC_STATIC = ACC_PUBLIC | ACC_STATIC
  static final Handle METAFACTORY = new Handle(H_INVOKESTATIC, 'java/lang/invoke/LambdaMetafactory', 'metafactory',
  '(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;', false)

  private GeneratedClasses() {}

  /** The findings of a generated class whose method runs the code, with save(V) and take(int|long) advised. */
  static List<String> scan(String name, String descriptor, Closure<?> code, int access = ACC_PUBLIC) {
    scan(generated(V17) { ClassWriter writer -> method(writer, name, descriptor, code, access) })
  }

  static List<String> scan(byte[] bytes) {
    def callees = ['take(I)V': 'Gen.take(int)', 'take(J)V': 'Gen.take(long)']
    callees.put("save($V)V".toString(), 'Gen.save(V)')
    (0..6).each { callees.put("p$it${probe(it)}".toString(), "p$it".toString()) }
    def found = new LinkedHashSet<SelfInvocation>()
    SelfInvocationScanner.scan(new ClassReader(bytes), callees, found, 'gen')
    found*.toString()
  }

  static String probe(int arguments) {
    "(${'I' * arguments})V"
  }

  static void lambda(MethodVisitor mv, String body) {
    mv.visitVarInsn(ALOAD, 0)
    mv.visitInvokeDynamicInsn('run', "(L$GEN;)Ljava/lang/Runnable;", METAFACTORY, Type.getType('()V'), new Handle(H_INVOKESPECIAL, GEN, body, '()V', false), Type.getType('()V'))
    mv.visitInsn(POP)
  }

  static void callSave(MethodVisitor mv) {
    mv.visitVarInsn(ALOAD, 0)
    mv.visitVarInsn(ALOAD, 1)
    mv.visitMethodInsn(INVOKEVIRTUAL, GEN, 'save', "($V)V", false)
  }

  /** A class gen.Gen with a save(V) method and the methods the closure adds. */
  static byte[] generated(int version, Closure<?> methods = {}) {
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

  static void method(ClassWriter writer, String name, String descriptor, Closure<?> code, int access = ACC_PUBLIC) {
    def mv = writer.visitMethod(access, name, descriptor, null, null)
    mv.visitCode()
    code(mv)
    mv.visitInsn(RETURN)
    mv.visitMaxs(16, 8)
    mv.visitEnd()
  }
}
