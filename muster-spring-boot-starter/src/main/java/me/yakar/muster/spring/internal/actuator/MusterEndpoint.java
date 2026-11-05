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
package me.yakar.muster.spring.internal.actuator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.yakar.muster.CheckMode;
import me.yakar.muster.Muster;
import me.yakar.muster.spring.internal.advice.AdviceTable;
import me.yakar.muster.spring.internal.advice.AdvisedMethod;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.endpoint.OperationResponseBody;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * The actuator endpoint {@code muster}: the rules of every advised method, with the chains bound so
 * far and the rules in the order they run, the rules that the configuration switches off, and the
 * chains bound by type so far. It reads what Muster has prepared and binds nothing itself: a class
 * that is not prepared yet, such as that of a lazy bean not called so far, is not listed. The chain
 * of an input type that is final or not a rule target is listed from startup, and the chain of the
 * class of a rule target from its first check.
 */
@Endpoint(id = "muster")
public final class MusterEndpoint {

  private final ObjectProvider<Muster> muster;
  private final AdviceTable table;

  MusterEndpoint(ObjectProvider<Muster> muster, AdviceTable table) {
    this.muster = muster;
    this.table = table;
  }

  /**
   * Lists the rules.
   *
   * @return the mode of Muster, the disabled rules, the advised methods and the chains bound by
   *     type
   */
  @ReadOperation
  public Report rules() {
    var muster = this.muster.getObject();
    return new Report(
        muster.mode(),
        List.copyOf(muster.disabledRuleIds()),
        table.advised().stream().map(Report.Method::of).toList(),
        byName(muster.boundRuleIds()),
        byName(muster.collectionRuleIds()));
  }

  /** Keys the ids of the rules by the names of their classes, in the same order. */
  private static Map<String, List<String>> byName(Map<Class<?>, List<String>> ruleIds) {
    var byName = new LinkedHashMap<String, List<String>>();
    ruleIds.forEach((type, ids) -> byName.put(type.getTypeName(), ids));
    return Collections.unmodifiableMap(byName);
  }

  /**
   * What the endpoint reports. An {@code OperationResponseBody}, so that the actuator writes it
   * with its own JSON mapper, whatever the JSON settings of the application.
   *
   * @param mode the mode of Muster, which a method or a binding may override
   * @param disabled the ids of the rules the configuration switches off, in their order
   * @param methods the advised methods, by their label and then by the name of their class
   * @param types the chains that {@code Muster.check} bound by type so far, by class, each the ids
   *     of its rules in the order they run
   * @param collections the chains that {@code Muster.checkAll} bound by type so far, by the class
   *     of the elements
   */
  public record Report(
      CheckMode mode,
      List<String> disabled,
      List<Method> methods,
      Map<String, List<String>> types,
      Map<String, List<String>> collections)
      implements OperationResponseBody {

    /**
     * An advised method.
     *
     * @param method the method, as in {@code VehicleService.create(CreateVehicle)}
     * @param target the class of the bean
     * @param input the type of the input, or of the elements of the collection the method takes
     * @param collection whether the method takes a collection
     * @param mode the mode of the check
     * @param rules the ids of the rules the method lists, as it lists them; {@code ruleIds} tells
     *     what runs
     * @param excluded the ids of the rules the method excludes, as it lists them
     * @param ruleIds the ids of the rules of the chains bound so far, by class, each in the order
     *     they run: the chain of the input type, bound at startup, for a type that is final or not
     *     a rule target, and the chain of the class of each rule target checked so far
     */
    public record Method(
        String method,
        String target,
        String input,
        boolean collection,
        CheckMode mode,
        List<String> rules,
        List<String> excluded,
        Map<String, List<String>> ruleIds) {

      /** Describes an advised method with the names of its classes. */
      static Method of(AdvisedMethod advised) {
        return new Method(
            advised.label(),
            advised.targetClass().getTypeName(),
            advised.inputType().getTypeName(),
            advised.collection(),
            advised.mode(),
            advised.rules(),
            advised.excluded(),
            byName(advised.ruleIds()));
      }
    }
  }
}
