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
package consumer;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import me.yakar.muster.BusinessRuleViolationException;
import me.yakar.muster.Muster;
import me.yakar.muster.RuleChain;
import me.yakar.muster.rule.BusinessRule;
import me.yakar.muster.rule.Rule;
import me.yakar.muster.rule.RuleContext;
import me.yakar.muster.rule.RuleResult;
import me.yakar.muster.rule.RuleTarget;
import me.yakar.muster.rule.Violation;
import me.yakar.muster.spi.MessageResolver;

/**
 * Checks a command the way the README shows, and fails if Muster does not report the violation or
 * does not resolve its message from the bundles of this module.
 */
public final class Main {

  private Main() {}

  /** The fields that the rules check. */
  public interface VehicleFields extends RuleTarget {

    /**
     * Returns the vehicle name.
     *
     * @return the name
     */
    String name();
  }

  /**
   * Creates a vehicle.
   *
   * @param name the vehicle name
   */
  public record CreateVehicle(String name) implements VehicleFields {}

  /** A vehicle name must not be taken. */
  @Rule(id = "vehicle.name-unique")
  public static final class VehicleNameUnique implements BusinessRule<VehicleFields> {

    private final Set<String> taken;

    VehicleNameUnique(Set<String> taken) {
      this.taken = taken;
    }

    @Override
    public RuleResult check(VehicleFields input, RuleContext ctx) {
      return taken.contains(input.name())
          ? RuleResult.failed(ctx.violation().clientArg("name", input.name()).build())
          : RuleResult.passed();
    }
  }

  /**
   * Runs the checks.
   *
   * @param args not used
   */
  public static void main(String[] args) {
    var muster = Muster.builder().rules(new VehicleNameUnique(Set.of("Atlas"))).build();
    RuleChain<CreateVehicle> create = muster.chain(CreateVehicle.class, VehicleNameUnique.class);

    create.check(new CreateVehicle("Boreas"));
    muster.check(new CreateVehicle("Boreas"));
    expectViolation(() -> create.check(new CreateVehicle("Atlas")));
    expectViolation(() -> muster.check(new CreateVehicle("Atlas")));
    var violation = expectViolation(() -> create.check(new CreateVehicle("Atlas")));

    // A server whose default locale is Turkish answers a French client from the base bundle.
    Locale.setDefault(Locale.forLanguageTag("tr"));
    var messages =
        MessageResolver.ofResourceBundles(
            locale -> ResourceBundle.getBundle("consumer.messages", locale));
    expectMessage(
        messages.resolve(violation, Locale.forLanguageTag("tr-TR")), "Atlas adı kullanılıyor");
    expectMessage(messages.resolve(violation, Locale.FRENCH), "The name Atlas is taken");
    System.out.println("Muster works for a consumer: " + create);
  }

  private static Violation expectViolation(Runnable check) {
    try {
      check.run();
    } catch (BusinessRuleViolationException e) {
      if (!e.violations().getFirst().code().equals("vehicle.name-unique")) {
        throw new AssertionError("unexpected violation: " + e.getMessage(), e);
      }
      return e.violations().getFirst();
    }
    throw new AssertionError("the taken name passed the check");
  }

  private static void expectMessage(String message, String expected) {
    if (!message.equals(expected)) {
      throw new AssertionError("expected the message '" + expected + "' but got '" + message + "'");
    }
  }
}
