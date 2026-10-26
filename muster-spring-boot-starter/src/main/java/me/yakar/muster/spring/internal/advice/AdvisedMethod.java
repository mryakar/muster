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
package me.yakar.muster.spring.internal.advice;

import java.util.List;
import java.util.Map;
import me.yakar.muster.CheckMode;
import me.yakar.muster.RuleChain;

/**
 * An advised method, as the actuator endpoint reports it: what its input is checked against.
 *
 * @param label the method, as in {@code VehicleService.create(CreateVehicle)}
 * @param targetClass the class of the bean
 * @param inputType the type of the input, or of the elements of the collection the method takes
 * @param collection whether the method takes a collection
 * @param mode the mode of the check
 * @param rules the ids of the rules the method lists, as it lists them; the chains tell what runs
 * @param excluded the ids of the rules the method excludes, as it lists them
 * @param chains the chains bound so far, by class, as {@link me.yakar.muster.Binding#chains}
 *     reports them
 */
public record AdvisedMethod(
    String label,
    Class<?> targetClass,
    Class<?> inputType,
    boolean collection,
    CheckMode mode,
    List<String> rules,
    List<String> excluded,
    Map<Class<?>, RuleChain<?>> chains) {}
