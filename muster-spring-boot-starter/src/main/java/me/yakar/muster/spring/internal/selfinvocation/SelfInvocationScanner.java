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

import static java.lang.System.Logger.Level.DEBUG;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.SpringAsmInfo;
import org.springframework.asm.Type;
import org.springframework.util.ClassUtils;
import org.springframework.util.FileCopyUtils;

/**
 * Scans the bytecode of an advised class for calls of its advised methods on {@code this}, which
 * pass no proxy and check no rules. It reads the class, its superclasses and its interfaces, with
 * the ASM that Spring ships, and follows the receiver of every call: a value loaded from {@code
 * this}, also through a cast or a copy on the operand stack. The bodies of lambdas are methods of
 * the class, so they are scanned too, and a method reference to an advised method on {@code this}
 * counts as a call. A call through reflection, through a nested class or from code compiled to
 * dynamic calls, as Groovy's, is not seen.
 */
public final class SelfInvocationScanner {

  private static final System.Logger LOGGER = System.getLogger("me.yakar.muster");

  private SelfInvocationScanner() {}

  /**
   * Finds the calls of the advised methods of a class on {@code this}, from the class, its
   * superclasses and its interfaces; a class whose bytecode cannot be read, such as one of the JDK
   * or one without a class file, is skipped. A target that is a proxy based on interfaces has no
   * code to scan.
   *
   * @param targetClass the advised class
   * @param advised its advised methods, which the calls are matched to by name and descriptor, each
   *     to the label the advice names it by
   * @return the calls found, each once, in the order of the classes and their methods
   */
  public static List<SelfInvocation> find(Class<?> targetClass, Map<Method, String> advised) {
    var found = new LinkedHashSet<SelfInvocation>();
    if (!Proxy.isProxyClass(targetClass)) {
      var callees = new HashMap<String, String>();
      advised.forEach(
          (method, label) ->
              callees.put(method.getName() + Type.getMethodDescriptor(method), label));
      for (var type : hierarchy(targetClass)) {
        var reader = read(type);
        if (reader != null) {
          scan(reader, callees, found, targetClass.getPackageName());
        }
      }
    }
    return List.copyOf(found);
  }

  /**
   * Scans one class for calls of the callees on {@code this}.
   *
   * @param callees the advised methods, by name and descriptor, to the label that names them
   * @param found where the calls go
   * @param targetPackage the package of the advised class, which a method of another package that
   *     is neither public nor protected is not seen from
   */
  static void scan(
      ClassReader reader,
      Map<String, String> callees,
      Set<SelfInvocation> found,
      String targetPackage) {
    reader.accept(
        new ClassScan(callees, found, targetPackage),
        ClassReader.SKIP_DEBUG | ClassReader.EXPAND_FRAMES);
  }

  /**
   * The class, its superclasses but {@code Object}, and their interfaces, with the interfaces of
   * those.
   */
  private static Set<Class<?>> hierarchy(Class<?> targetClass) {
    var types = new LinkedHashSet<Class<?>>();
    for (var type = targetClass;
        type != null && type != Object.class;
        type = type.getSuperclass()) {
      types.add(type);
    }
    for (var type : List.copyOf(types)) {
      addInterfaces(type, types);
    }
    return types;
  }

  private static void addInterfaces(Class<?> type, Set<Class<?>> types) {
    for (var implemented : type.getInterfaces()) {
      if (types.add(implemented)) {
        addInterfaces(implemented, types);
      }
    }
  }

  /**
   * Reads the class file of a class, or returns {@code null} for a class of the JDK, one without a
   * class file, such as in a native image, one older than Java 7, whose code has no stack map
   * frames to follow, or one that the ASM cannot read.
   */
  private static @Nullable ClassReader read(Class<?> type) {
    if (type.getClassLoader() == null) {
      return null;
    }
    try {
      var in = type.getResourceAsStream(ClassUtils.getClassFileName(type));
      if (in == null) {
        LOGGER.log(
            DEBUG,
            "Muster cannot find the class file of " + type.getName() + ", so it is not scanned");
        return null;
      }
      var reader = new ClassReader(FileCopyUtils.copyToByteArray(in));
      if (reader.readShort(6) < Opcodes.V1_7) {
        LOGGER.log(
            DEBUG,
            "Muster does not scan "
                + type.getName()
                + ", which is compiled for a Java older than 7");
        return null;
      }
      return reader;
    } catch (IOException | RuntimeException e) {
      LOGGER.log(
          DEBUG,
          "Muster cannot read the class file of " + type.getName() + ", so it is not scanned",
          e);
      return null;
    }
  }

  /** A method of a scanned class, by name and descriptor. */
  record MethodId(String name, String descriptor) {

    /** The name and descriptor together, which a call names. */
    String key() {
      return name + descriptor;
    }
  }

  /** Scans the methods of one class and labels the calls found once the class is read. */
  static final class ClassScan extends ClassVisitor {

    private final Map<String, String> callees;
    private final Set<SelfInvocation> found;
    private final String targetPackage;
    private final List<Call> calls = new ArrayList<>();

    /**
     * The methods of the scanned class that no subclass overrides, by name and descriptor: private
     * ones, and those of another package than the target that are neither public nor protected. A
     * call of one from the class runs it, not the advised method of the same name and descriptor.
     */
    private final Set<String> own = new HashSet<>();

    /**
     * The method that creates each lambda, by the name of the method that holds the lambda's body.
     */
    private final Map<String, MethodId> lambdaOwners = new HashMap<>();

    private String internalName = "";
    private String simpleName = "";
    private boolean inTargetPackage;

    ClassScan(Map<String, String> callees, Set<SelfInvocation> found, String targetPackage) {
      super(SpringAsmInfo.ASM_VERSION);
      this.callees = callees;
      this.found = found;
      this.targetPackage = targetPackage;
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        @Nullable String signature,
        @Nullable String superName,
        String @Nullable [] interfaces) {
      internalName = name;
      var slash = name.lastIndexOf('/');
      simpleName = MethodLabels.simpleName(name.substring(slash + 1));
      inTargetPackage =
          name.substring(0, Math.max(slash, 0)).replace('/', '.').equals(targetPackage);
    }

    /**
     * Follows the receivers in every method a compiler did not make up, and in the bodies of
     * lambdas, which javac and Kotlin name with {@code lambda$}: a bridge calls the method it
     * bridges to on {@code this} by design, and so does a forwarder such as Groovy's {@code
     * super$2$find}. In a static method, and in the body of a lambda that does not capture {@code
     * this}, the first local variable is a parameter.
     */
    @Override
    public @Nullable MethodVisitor visitMethod(
        int access,
        String name,
        String descriptor,
        @Nullable String signature,
        String @Nullable [] exceptions) {
      var visible = access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
      if (visible == Opcodes.ACC_PRIVATE || (visible == 0 && !inTargetPackage)) {
        own.add(name + descriptor);
      }
      var madeUp = (access & (Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC)) != 0;
      if (madeUp && !name.contains("lambda$")) {
        return null;
      }
      return new ReceiverTracker(
          this,
          internalName,
          new MethodId(name, descriptor),
          (access & Opcodes.ACC_STATIC) == 0,
          callees);
    }

    /**
     * Notes a call of a callee on {@code this}.
     *
     * @param owner the internal name of the class the call names
     * @param key the name and descriptor of the method it names
     * @param callee the label of the advised method of that name and descriptor
     */
    void call(MethodId caller, String owner, String key, String callee) {
      calls.add(new Call(caller, owner, key, callee));
    }

    /** Notes the method that creates a lambda, so that its body is named after it. */
    void lambda(String lambdaMethod, MethodId creator) {
      lambdaOwners.put(lambdaMethod, creator);
    }

    /** Labels the calls found, but those of a method of the class that no subclass overrides. */
    @Override
    public void visitEnd() {
      for (var call : calls) {
        if (!(call.owner().equals(internalName) && own.contains(call.key()))) {
          found.add(new SelfInvocation(label(call.caller()), call.callee()));
        }
      }
    }

    /**
     * Names a method; the body of a lambda after the method that creates it, through the lambdas
     * nested in between.
     */
    private String label(MethodId method) {
      var lambda = false;
      var seen = new HashSet<String>();
      for (var creator = lambdaOwners.get(method.name());
          creator != null && seen.add(method.name());
          creator = lambdaOwners.get(method.name())) {
        method = creator;
        lambda = true;
      }
      return MethodLabels.of(simpleName, method.name(), method.descriptor(), lambda);
    }

    private record Call(MethodId caller, String owner, String key, String callee) {}
  }
}
