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

/**
 * A call of an advised method on {@code this}, which passes no proxy and checks no rules.
 *
 * @param caller the method that makes the call, such as {@code VehicleService.register(String)}, or
 *     {@code a lambda in VehicleService.register(String)} for the body of a lambda
 * @param callee the advised method it calls, such as {@code VehicleService.save(SaveVehicle)}
 */
public record SelfInvocation(String caller, String callee) {

  @Override
  public String toString() {
    return caller + " calls " + callee + " through this";
  }
}
