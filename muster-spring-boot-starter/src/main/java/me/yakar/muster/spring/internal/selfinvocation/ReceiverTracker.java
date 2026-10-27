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
package me.yakar.muster.spring.internal.selfinvocation;

import static org.springframework.asm.Opcodes.AALOAD;
import static org.springframework.asm.Opcodes.AASTORE;
import static org.springframework.asm.Opcodes.ACONST_NULL;
import static org.springframework.asm.Opcodes.ALOAD;
import static org.springframework.asm.Opcodes.ANEWARRAY;
import static org.springframework.asm.Opcodes.ARETURN;
import static org.springframework.asm.Opcodes.ARRAYLENGTH;
import static org.springframework.asm.Opcodes.ASTORE;
import static org.springframework.asm.Opcodes.ATHROW;
import static org.springframework.asm.Opcodes.BALOAD;
import static org.springframework.asm.Opcodes.BASTORE;
import static org.springframework.asm.Opcodes.BIPUSH;
import static org.springframework.asm.Opcodes.CALOAD;
import static org.springframework.asm.Opcodes.CASTORE;
import static org.springframework.asm.Opcodes.D2F;
import static org.springframework.asm.Opcodes.D2I;
import static org.springframework.asm.Opcodes.D2L;
import static org.springframework.asm.Opcodes.DADD;
import static org.springframework.asm.Opcodes.DALOAD;
import static org.springframework.asm.Opcodes.DASTORE;
import static org.springframework.asm.Opcodes.DCMPG;
import static org.springframework.asm.Opcodes.DCMPL;
import static org.springframework.asm.Opcodes.DCONST_0;
import static org.springframework.asm.Opcodes.DCONST_1;
import static org.springframework.asm.Opcodes.DDIV;
import static org.springframework.asm.Opcodes.DLOAD;
import static org.springframework.asm.Opcodes.DMUL;
import static org.springframework.asm.Opcodes.DNEG;
import static org.springframework.asm.Opcodes.DREM;
import static org.springframework.asm.Opcodes.DRETURN;
import static org.springframework.asm.Opcodes.DSTORE;
import static org.springframework.asm.Opcodes.DSUB;
import static org.springframework.asm.Opcodes.DUP;
import static org.springframework.asm.Opcodes.DUP2;
import static org.springframework.asm.Opcodes.DUP2_X1;
import static org.springframework.asm.Opcodes.DUP2_X2;
import static org.springframework.asm.Opcodes.DUP_X1;
import static org.springframework.asm.Opcodes.DUP_X2;
import static org.springframework.asm.Opcodes.F2D;
import static org.springframework.asm.Opcodes.F2I;
import static org.springframework.asm.Opcodes.F2L;
import static org.springframework.asm.Opcodes.FADD;
import static org.springframework.asm.Opcodes.FALOAD;
import static org.springframework.asm.Opcodes.FASTORE;
import static org.springframework.asm.Opcodes.FCMPG;
import static org.springframework.asm.Opcodes.FCMPL;
import static org.springframework.asm.Opcodes.FCONST_0;
import static org.springframework.asm.Opcodes.FCONST_1;
import static org.springframework.asm.Opcodes.FCONST_2;
import static org.springframework.asm.Opcodes.FDIV;
import static org.springframework.asm.Opcodes.FLOAD;
import static org.springframework.asm.Opcodes.FMUL;
import static org.springframework.asm.Opcodes.FNEG;
import static org.springframework.asm.Opcodes.FREM;
import static org.springframework.asm.Opcodes.FRETURN;
import static org.springframework.asm.Opcodes.FSTORE;
import static org.springframework.asm.Opcodes.FSUB;
import static org.springframework.asm.Opcodes.GETFIELD;
import static org.springframework.asm.Opcodes.GETSTATIC;
import static org.springframework.asm.Opcodes.H_INVOKEINTERFACE;
import static org.springframework.asm.Opcodes.H_INVOKESPECIAL;
import static org.springframework.asm.Opcodes.H_INVOKEVIRTUAL;
import static org.springframework.asm.Opcodes.I2B;
import static org.springframework.asm.Opcodes.I2C;
import static org.springframework.asm.Opcodes.I2D;
import static org.springframework.asm.Opcodes.I2F;
import static org.springframework.asm.Opcodes.I2L;
import static org.springframework.asm.Opcodes.I2S;
import static org.springframework.asm.Opcodes.IADD;
import static org.springframework.asm.Opcodes.IALOAD;
import static org.springframework.asm.Opcodes.IAND;
import static org.springframework.asm.Opcodes.IASTORE;
import static org.springframework.asm.Opcodes.ICONST_0;
import static org.springframework.asm.Opcodes.ICONST_1;
import static org.springframework.asm.Opcodes.ICONST_2;
import static org.springframework.asm.Opcodes.ICONST_3;
import static org.springframework.asm.Opcodes.ICONST_4;
import static org.springframework.asm.Opcodes.ICONST_5;
import static org.springframework.asm.Opcodes.ICONST_M1;
import static org.springframework.asm.Opcodes.IDIV;
import static org.springframework.asm.Opcodes.IFEQ;
import static org.springframework.asm.Opcodes.IFGE;
import static org.springframework.asm.Opcodes.IFGT;
import static org.springframework.asm.Opcodes.IFLE;
import static org.springframework.asm.Opcodes.IFLT;
import static org.springframework.asm.Opcodes.IFNE;
import static org.springframework.asm.Opcodes.IFNONNULL;
import static org.springframework.asm.Opcodes.IFNULL;
import static org.springframework.asm.Opcodes.IF_ACMPEQ;
import static org.springframework.asm.Opcodes.IF_ACMPNE;
import static org.springframework.asm.Opcodes.IF_ICMPEQ;
import static org.springframework.asm.Opcodes.IF_ICMPGE;
import static org.springframework.asm.Opcodes.IF_ICMPGT;
import static org.springframework.asm.Opcodes.IF_ICMPLE;
import static org.springframework.asm.Opcodes.IF_ICMPLT;
import static org.springframework.asm.Opcodes.IF_ICMPNE;
import static org.springframework.asm.Opcodes.ILOAD;
import static org.springframework.asm.Opcodes.IMUL;
import static org.springframework.asm.Opcodes.INEG;
import static org.springframework.asm.Opcodes.INSTANCEOF;
import static org.springframework.asm.Opcodes.INVOKESTATIC;
import static org.springframework.asm.Opcodes.IOR;
import static org.springframework.asm.Opcodes.IREM;
import static org.springframework.asm.Opcodes.IRETURN;
import static org.springframework.asm.Opcodes.ISHL;
import static org.springframework.asm.Opcodes.ISHR;
import static org.springframework.asm.Opcodes.ISTORE;
import static org.springframework.asm.Opcodes.ISUB;
import static org.springframework.asm.Opcodes.IUSHR;
import static org.springframework.asm.Opcodes.IXOR;
import static org.springframework.asm.Opcodes.JSR;
import static org.springframework.asm.Opcodes.L2D;
import static org.springframework.asm.Opcodes.L2F;
import static org.springframework.asm.Opcodes.L2I;
import static org.springframework.asm.Opcodes.LADD;
import static org.springframework.asm.Opcodes.LALOAD;
import static org.springframework.asm.Opcodes.LAND;
import static org.springframework.asm.Opcodes.LASTORE;
import static org.springframework.asm.Opcodes.LCMP;
import static org.springframework.asm.Opcodes.LCONST_0;
import static org.springframework.asm.Opcodes.LCONST_1;
import static org.springframework.asm.Opcodes.LDIV;
import static org.springframework.asm.Opcodes.LLOAD;
import static org.springframework.asm.Opcodes.LMUL;
import static org.springframework.asm.Opcodes.LNEG;
import static org.springframework.asm.Opcodes.LOR;
import static org.springframework.asm.Opcodes.LREM;
import static org.springframework.asm.Opcodes.LRETURN;
import static org.springframework.asm.Opcodes.LSHL;
import static org.springframework.asm.Opcodes.LSHR;
import static org.springframework.asm.Opcodes.LSTORE;
import static org.springframework.asm.Opcodes.LSUB;
import static org.springframework.asm.Opcodes.LUSHR;
import static org.springframework.asm.Opcodes.LXOR;
import static org.springframework.asm.Opcodes.MONITORENTER;
import static org.springframework.asm.Opcodes.MONITOREXIT;
import static org.springframework.asm.Opcodes.NEW;
import static org.springframework.asm.Opcodes.NEWARRAY;
import static org.springframework.asm.Opcodes.POP;
import static org.springframework.asm.Opcodes.POP2;
import static org.springframework.asm.Opcodes.PUTFIELD;
import static org.springframework.asm.Opcodes.PUTSTATIC;
import static org.springframework.asm.Opcodes.RETURN;
import static org.springframework.asm.Opcodes.SALOAD;
import static org.springframework.asm.Opcodes.SASTORE;
import static org.springframework.asm.Opcodes.SIPUSH;
import static org.springframework.asm.Opcodes.SWAP;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import me.yakar.muster.spring.internal.selfinvocation.SelfInvocationScanner.ClassScan;
import me.yakar.muster.spring.internal.selfinvocation.SelfInvocationScanner.MethodId;
import org.jspecify.annotations.Nullable;
import org.springframework.asm.ConstantDynamic;
import org.springframework.asm.Handle;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.SpringAsmInfo;
import org.springframework.asm.Type;

/**
 * Follows the operand stack of one method far enough to tell whether the receiver of a call is
 * {@code this}, and whether its argument is the input of the method, unchanged: each value on the
 * stack is {@code this}, loaded from the first local variable of an instance method, the input,
 * loaded from the second, or another value, of one or two slots. A copy keeps what it copies, a
 * cast keeps its value, and every other instruction pushes other values. An advised method that
 * calls itself with its own input, as an override does with {@code super}, checks nothing twice,
 * since the proxy checked the input already; so that call is not one.
 *
 * <p>The code after a return or a throw starts with a stack map frame, which gives the size of the
 * stack there; so does the code after an unconditional jump, whose frame cuts the stack to the
 * values that every path to it pushed, as javac pushes the receiver and the arguments before a
 * conditional expression. The values a frame keeps from the code before it keep what they were, and
 * the values it adds are other values. So a value is taken for {@code this} only if it is {@code
 * this} on the path followed, and a call whose receiver is {@code this} on one path and another
 * object on the other may be missed, as may one whose receiver javac spills to a local variable,
 * around a switch expression with a {@code try} in it.
 */
final class ReceiverTracker extends MethodVisitor {

  /** A value on the operand stack. */
  enum Value {
    THIS,
    INPUT,
    WIDE_INPUT,
    ONE,
    TWO;

    /** Returns whether the value takes two slots, as a {@code long} or a {@code double} does. */
    boolean wide() {
      return this == WIDE_INPUT || this == TWO;
    }

    /** Returns whether the value is the input of the method, unchanged. */
    boolean input() {
      return this == INPUT || this == WIDE_INPUT;
    }
  }

  /** How many values each instruction without an operand of its own pops, by opcode. */
  private static final int[] POPS = new int[IFNONNULL + 1];

  /** The value each such instruction pushes, if any, by opcode. */
  private static final @Nullable Value[] PUSHES = new Value[IFNONNULL + 1];

  static {
    effect(0, Value.ONE, ACONST_NULL, ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4);
    effect(0, Value.ONE, ICONST_5, FCONST_0, FCONST_1, FCONST_2, BIPUSH, SIPUSH, ILOAD, FLOAD);
    effect(0, Value.ONE, ALOAD, NEW, JSR);
    effect(0, Value.TWO, LCONST_0, LCONST_1, DCONST_0, DCONST_1, LLOAD, DLOAD);
    effect(1, null, POP, ISTORE, LSTORE, FSTORE, DSTORE, ASTORE, GETFIELD, PUTSTATIC);
    effect(1, null, MONITORENTER);
    effect(1, null, MONITOREXIT, IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL, IFNONNULL);
    effect(1, Value.ONE, INEG, FNEG, I2F, L2I, L2F, F2I, D2I, D2F, I2B, I2C, I2S, ARRAYLENGTH);
    effect(1, Value.ONE, NEWARRAY, ANEWARRAY, INSTANCEOF);
    effect(1, Value.TWO, LNEG, DNEG, I2L, I2D, L2D, F2L, F2D, D2L);
    effect(2, null, PUTFIELD, IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE);
    effect(2, null, IF_ACMPEQ, IF_ACMPNE);
    effect(2, Value.ONE, IALOAD, FALOAD, AALOAD, BALOAD, CALOAD, SALOAD, IADD, FADD, ISUB, FSUB);
    effect(2, Value.ONE, IMUL, FMUL, IDIV, FDIV, IREM, FREM, ISHL, ISHR, IUSHR, IAND, IOR, IXOR);
    effect(2, Value.ONE, LCMP, FCMPL, FCMPG, DCMPL, DCMPG);
    effect(2, Value.TWO, LALOAD, DALOAD, LADD, DADD, LSUB, DSUB, LMUL, DMUL, LDIV, DDIV, LREM);
    effect(2, Value.TWO, DREM, LSHL, LSHR, LUSHR, LAND, LOR, LXOR);
    effect(3, null, IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE);
  }

  private final ClassScan scan;
  private final String owner;
  private final MethodId method;
  private final Map<String, String> callees;
  private final List<Value> stack = new ArrayList<>();

  /** Whether the first local variable still holds {@code this}. */
  private boolean thisInFirstLocal;

  /** Whether the second local variable still holds the input, the first parameter. */
  private boolean inputInSecondLocal = true;

  /**
   * @param scan where the calls go
   * @param owner the internal name of the class, whose lambdas are its own methods
   * @param method the method followed
   * @param instance whether the method is an instance method, whose first local is {@code this}
   * @param callees the advised methods, by name and descriptor, to the label that names them
   */
  ReceiverTracker(
      ClassScan scan,
      String owner,
      MethodId method,
      boolean instance,
      Map<String, String> callees) {
    super(SpringAsmInfo.ASM_VERSION);
    this.scan = scan;
    this.owner = owner;
    this.method = method;
    this.callees = callees;
    thisInFirstLocal = instance;
  }

  private static void effect(int pops, @Nullable Value push, int... opcodes) {
    for (var opcode : opcodes) {
      POPS[opcode] = pops;
      PUSHES[opcode] = push;
    }
  }

  @Override
  public void visitInsn(int opcode) {
    switch (opcode) {
      case POP2 -> pop(top().wide() ? 1 : 2);
      case DUP -> stack.add(top());
      case DUP_X1 -> insert(2, top());
      case DUP_X2 -> insert(value(1).wide() ? 2 : 3, top());
      case DUP2 -> {
        if (top().wide()) {
          stack.add(top());
        } else {
          insert(0, value(1), value(0));
        }
      }
      case DUP2_X1 -> {
        if (top().wide()) {
          insert(2, top());
        } else {
          insert(3, value(1), value(0));
        }
      }
      case DUP2_X2 -> {
        if (top().wide()) {
          insert(value(1).wide() ? 2 : 3, top());
        } else {
          insert(value(2).wide() ? 3 : 4, value(1), value(0));
        }
      }
      case SWAP -> {
        if (stack.size() > 1) {
          Collections.swap(stack, stack.size() - 1, stack.size() - 2);
        }
      }
      case IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN, ATHROW -> stack.clear();
      default -> apply(opcode);
    }
  }

  @Override
  public void visitIntInsn(int opcode, int operand) {
    apply(opcode);
  }

  @Override
  public void visitVarInsn(int opcode, int varIndex) {
    if (opcode == ALOAD && varIndex == 0 && thisInFirstLocal) {
      stack.add(Value.THIS);
    } else if (opcode <= ALOAD && varIndex == 1 && inputInSecondLocal) {
      stack.add(opcode == LLOAD || opcode == DLOAD ? Value.WIDE_INPUT : Value.INPUT);
    } else {
      if (POPS[opcode] == 1) {
        thisInFirstLocal &= varIndex != 0;
        inputInSecondLocal &= varIndex != 1;
      }
      apply(opcode);
    }
  }

  @Override
  public void visitIincInsn(int varIndex, int increment) {
    inputInSecondLocal &= varIndex != 1;
  }

  @Override
  public void visitTypeInsn(int opcode, String type) {
    apply(opcode);
  }

  @Override
  public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
    apply(opcode);
    if (opcode == GETSTATIC || opcode == GETFIELD) {
      push(Type.getType(descriptor));
    }
  }

  @Override
  public void visitMethodInsn(
      int opcode, String owner, String name, String descriptor, boolean isInterface) {
    var arguments = Type.getArgumentTypes(descriptor).length;
    if (opcode == INVOKESTATIC) {
      pop(arguments);
    } else {
      callIf(value(arguments), arguments == 1 ? top() : Value.ONE, owner, name + descriptor);
      pop(arguments + 1);
    }
    push(Type.getReturnType(descriptor));
  }

  /**
   * A method reference to an advised method on {@code this} calls it when the function runs; the
   * lambda bodies the method creates are named after it.
   */
  @Override
  public void visitInvokeDynamicInsn(
      String name,
      String descriptor,
      Handle bootstrapMethodHandle,
      Object... bootstrapMethodArguments) {
    var arguments = Type.getArgumentTypes(descriptor).length;
    for (var argument : bootstrapMethodArguments) {
      if (argument instanceof Handle handle) {
        if (handle.getOwner().equals(owner) && handle.getName().startsWith("lambda$")) {
          scan.lambda(handle.getName(), method);
        } else if (invokes(handle.getTag()) && arguments > 0) {
          callIf(
              value(arguments - 1),
              Value.ONE,
              handle.getOwner(),
              handle.getName() + handle.getDesc());
        }
      }
    }
    pop(arguments);
    push(Type.getReturnType(descriptor));
  }

  private static boolean invokes(int tag) {
    return tag == H_INVOKEVIRTUAL || tag == H_INVOKEINTERFACE || tag == H_INVOKESPECIAL;
  }

  @Override
  public void visitJumpInsn(int opcode, Label label) {
    apply(opcode);
  }

  @Override
  public void visitLdcInsn(Object value) {
    if (value instanceof ConstantDynamic constant) {
      push(Type.getType(constant.getDescriptor()));
    } else {
      stack.add(value instanceof Long || value instanceof Double ? Value.TWO : Value.ONE);
    }
  }

  @Override
  public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
    pop(1);
  }

  @Override
  public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
    pop(1);
  }

  @Override
  public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
    pop(numDimensions);
    stack.add(Value.ONE);
  }

  /** Sizes the stack as the frame says, keeping the values below and adding other values above. */
  @Override
  public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
    pop(this.stack.size() - numStack);
    for (var index = this.stack.size(); index < numStack; index++) {
      var value = stack[index];
      this.stack.add(
          Opcodes.LONG.equals(value) || Opcodes.DOUBLE.equals(value) ? Value.TWO : Value.ONE);
    }
  }

  /**
   * Notes a call of a callee if its receiver is {@code this}, unless the method is that callee,
   * also through the erased signature of a generic base, and calls it with its own input.
   */
  private void callIf(Value receiver, Value argument, String owner, String key) {
    var callee = callees.get(key);
    if (receiver == Value.THIS
        && callee != null
        && !(argument.input() && callee.equals(callees.get(method.key())))) {
      scan.call(method, owner, key, callee);
    }
  }

  /** Applies the effect of an instruction the tables know. */
  private void apply(int opcode) {
    pop(POPS[opcode]);
    var push = PUSHES[opcode];
    if (push != null) {
      stack.add(push);
    }
  }

  /** Pushes a value of the type, if it is not {@code void}. */
  private void push(Type type) {
    if (type.getSort() != Type.VOID) {
      stack.add(type.getSize() == 2 ? Value.TWO : Value.ONE);
    }
  }

  /** Pops values, as many as there are. */
  private void pop(int count) {
    for (var popped = 0; popped < count && !stack.isEmpty(); popped++) {
      stack.removeLast();
    }
  }

  private Value top() {
    return value(0);
  }

  /** The value at a depth from the top, or another value if the stack is not that deep. */
  private Value value(int depth) {
    var index = stack.size() - 1 - depth;
    return index < 0 ? Value.ONE : stack.get(index);
  }

  /** Inserts values at a depth from the top, or at the bottom if the stack is not that deep. */
  private void insert(int depth, Value... values) {
    stack.addAll(Math.max(0, stack.size() - depth), List.of(values));
  }
}
