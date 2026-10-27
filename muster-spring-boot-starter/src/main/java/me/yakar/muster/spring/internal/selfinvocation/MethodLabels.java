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

import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.asm.Type;

/**
 * Names methods for the findings, as the advice names them: {@code
 * VehicleService.save(SaveVehicle)}.
 */
final class MethodLabels {

  private MethodLabels() {}

  /**
   * Names a method of a scanned class from its descriptor: a constructor as {@code
   * VehicleService(String)}, the body of a lambda as {@code a lambda in
   * VehicleService.register(String)}.
   */
  static String of(String simpleClassName, String name, String descriptor, boolean lambda) {
    var parameters =
        Arrays.stream(Type.getArgumentTypes(descriptor))
            .map(type -> simpleName(type.getClassName()))
            .collect(Collectors.joining(", ", "(", ")"));
    var label =
        name.equals("<init>")
            ? simpleClassName + parameters
            : simpleClassName + "." + name + parameters;
    return lambda ? "a lambda in " + label : label;
  }

  /**
   * Returns the simple name of a class name, also of a nested class: {@code Outer$Inner} is {@code
   * Inner}.
   */
  static String simpleName(String className) {
    var simple = className.substring(className.lastIndexOf('.') + 1);
    return simple.substring(simple.lastIndexOf('$') + 1);
  }
}
