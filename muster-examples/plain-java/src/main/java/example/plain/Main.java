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
package example.plain;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import me.yakar.muster.BusinessRuleViolationException;
import me.yakar.muster.CheckMode;
import me.yakar.muster.Muster;
import me.yakar.muster.RuleChain;
import me.yakar.muster.rule.Violation;
import me.yakar.muster.spi.MessageResolver;

/**
 * An application without Spring, on the module path, whose samples the guide to Muster without
 * Spring includes. It makes the calls the guide describes, and fails if Muster does not answer as
 * the guide says.
 */
public final class Main {

  private static final User ECE = new User("ece");
  private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");

  /** The first vehicle created, Atlas. */
  private static final long ATLAS = 1;

  private Main() {}

  public static void main(String[] args) {
    // tag::muster[]
    var vehicles = new Vehicles();
    var log = new CopyOnWriteArrayList<String>(); // the listener adds to it from every thread
    var muster =
        Muster.builder()
            .rules(
                new VehicleNameUnique(vehicles),
                new VehiclePlateFormat(),
                new VehicleExists(vehicles),
                new VehicleOwnedByCaller(vehicles),
                new BatchLimit(3))
            .caller(CurrentCall::caller)
            .locale(CurrentCall::locale)
            .listener(new CheckLog(log::add))
            .build();
    // end::muster[]
    // tag::messages[]
    var messages =
        MessageResolver.ofResourceBundles(
            locale -> ResourceBundle.getBundle("example.plain.messages", locale));
    // end::messages[]
    var problems = new Problems(messages);
    var service = new VehicleService(muster, vehicles);

    CurrentCall.run(ECE, TURKISH, () -> creates(service, problems));
    CurrentCall.run(ECE, TURKISH, () -> checksByType(muster));
    CurrentCall.run(ECE, TURKISH, () -> collectsAll(muster, problems));
    renames(service, problems, log);
    CurrentCall.run(ECE, TURKISH, () -> createsAll(muster, service, problems));
    switchesOff(vehicles);
    CurrentCall.run(ECE, TURKISH, () -> resolvesMessages(muster, messages));
    System.out.println("The plain Java example works");
  }

  private static void creates(VehicleService service, Problems problems) {
    service.create(new CreateVehicle("Atlas", "34 ABC 123"));
    service.create(new CreateVehicle("Ilgaz", "06 ANK 42"));
    // A name is taken whatever its case, also when a Turkish caller writes it with a dotted i.
    var taken =
        expectRejected(
            () -> service.create(new CreateVehicle("ilgaz", "06 ANK 43")), "vehicle.name-unique");
    var conflict = problems.problem(taken, TURKISH);
    expectProblem(conflict, 409, "ilgaz adı kullanılıyor");
    expect(conflict.violations().getFirst().target(), "name");
    expect(conflict.violations().getFirst().category(), "CONFLICT");
    var plate =
        expectRejected(
            () -> service.create(new CreateVehicle("Boreas", "34-ABC")), "vehicle.plate-format");
    expectProblem(problems.problem(plate, TURKISH), 422, "34-ABC plakası burada geçerli değil");
    // Without a region, no plate format is known, and the rule does not apply.
    CurrentCall.run(ECE, Locale.ENGLISH, () -> service.create(new CreateVehicle("Kestrel", "K1")));
    CurrentCall.run(
        ECE, Locale.GERMANY, () -> service.create(new CreateVehicle("Föhn", "M-AB 1234")));
  }

  private static void checksByType(Muster muster) {
    // tag::check-by-type[]
    muster.check(new CreateVehicle("Boreas", "34 BOR 10")); // every rule that accepts the class
    // end::check-by-type[]
    // tag::bound-rule-ids[]
    var bound = muster.boundRuleIds().get(CreateVehicle.class); // [vehicle.name-unique, …]
    // end::bound-rule-ids[]
    expect(bound, List.of("vehicle.name-unique", "vehicle.plate-format"));
    // tag::rule-ids[]
    var rename = muster.chain(RenameVehicle.class, VehicleOwnedByCaller.class);
    List<String> ids = rename.ruleIds(); // [vehicle.exists, vehicle.owned-by-caller]
    // end::rule-ids[]
    expect(ids, List.of("vehicle.exists", "vehicle.owned-by-caller"));
  }

  private static void collectsAll(Muster muster, Problems problems) {
    var invalid = new CreateVehicle("Atlas", "ABC");
    // Fail-fast, the default, reports the first violated rule.
    expectRejected(() -> muster.check(invalid), "vehicle.name-unique");
    // tag::collect-all[]
    RuleChain<CreateVehicle> everything =
        muster
            .chain(CreateVehicle.class, VehiclePlateFormat.class, VehicleNameUnique.class)
            .withMode(CheckMode.COLLECT_ALL);
    // end::collect-all[]
    var both =
        expectRejected(
            () -> everything.check(invalid), "vehicle.plate-format", "vehicle.name-unique");
    // Violations of different categories answer 422.
    expect(problems.problem(both, TURKISH).status(), 422);
    expectRejected(
        () -> muster.check(invalid, CheckMode.COLLECT_ALL),
        "vehicle.name-unique",
        "vehicle.plate-format");
    expect(everything.ruleIds(), List.of("vehicle.plate-format", "vehicle.name-unique"));
  }

  private static void renames(VehicleService service, Problems problems, List<String> log) {
    CurrentCall.run(
        new User("can"),
        TURKISH,
        () -> {
          var forbidden =
              expectRejected(
                  () -> service.rename(new RenameVehicle(ATLAS, "Atlas II")),
                  "vehicle.owned-by-caller");
          var problem = problems.problem(forbidden, TURKISH);
          expectProblem(problem, 403, "Bu aracı yalnız sahibi değiştirebilir");
          expect(problem.violations().getFirst().args(), Map.of());
          expect(forbidden.violations().getFirst().internalArgs(), Map.of("owner", "ece"));
        });
    CurrentCall.run(
        ECE,
        TURKISH,
        () -> {
          log.clear();
          var missing =
              expectRejected(
                  () -> service.rename(new RenameVehicle(99, "Atlas II")), "vehicle.exists");
          expectProblem(problems.problem(missing, TURKISH), 404, "99 numaralı araç yok");
          // The binding collects all, and checks the owner only of a vehicle that exists.
          expectLogged(log, "VehicleService.rename: vehicle.exists FAILED");
          expectLogged(log, "VehicleService.rename: vehicle.name-unique PASSED");
          expectLogged(log, "VehicleService.rename: vehicle.owned-by-caller SKIPPED");
          service.rename(new RenameVehicle(ATLAS, "Atlas II"));
        });
  }

  private static void createsAll(Muster muster, VehicleService service, Problems problems) {
    var twice =
        expectRejected(
            () ->
                service.createAll(
                    List.of(
                        new CreateVehicle("Boreas", "34 BOR 10"),
                        new CreateVehicle("boreas", "34 BOR 11"))),
            "vehicle.name-unique");
    expect(twice.violations().getFirst().target(), Optional.of("[1].name"));
    var many =
        expectRejected(
            () ->
                service.createAll(
                    List.of(
                        new CreateVehicle("Boreas", "34 BOR 10"),
                        new CreateVehicle("Zephyr", "34 ZEP 20"),
                        new CreateVehicle("Sirocco", "34 SIR 30"),
                        new CreateVehicle("Mistral", "34 MIS 40"))),
            "vehicle.batch-limit");
    expectProblem(problems.problem(many, TURKISH), 422, "Bir seferde en fazla 3 araç");
    // tag::check-all[]
    var commands =
        List.of(new CreateVehicle("Boreas", "34 BOR 10"), new CreateVehicle("Zephyr", "34 ZEP 20"));
    muster.checkAll(commands); // each element, and the whole collection, in one call
    // end::check-all[]
    service.createAll(commands);
    // Both names are taken now; the rule that overrides checkAll reports both at once.
    expectRejected(() -> muster.checkAll(commands), "vehicle.name-unique", "vehicle.name-unique");
  }

  private static void switchesOff(Vehicles vehicles) {
    // tag::disable[]
    var lenient =
        Muster.builder()
            .rules(new VehicleNameUnique(vehicles), new VehiclePlateFormat())
            .disable("vehicle.plate-format") // the ids from your configuration, for example
            .build();
    // end::disable[]
    expect(lenient.disabledRuleIds(), Set.of("vehicle.plate-format"));
    lenient.check(new CreateVehicle("Halley", "no plate"));
  }

  private static void resolvesMessages(Muster muster, MessageResolver messages) {
    var violation =
        expectRejected(
                () -> muster.check(new CreateVehicle("Atlas II", "34 ATL 10")),
                "vehicle.name-unique")
            .violations()
            .getFirst();
    // A server whose default locale is Turkish answers a French client from the base bundle.
    var serverLocale = Locale.getDefault();
    Locale.setDefault(Locale.forLanguageTag("tr"));
    try {
      expect(messages.resolve(violation, TURKISH), "Atlas II adı kullanılıyor");
      expect(messages.resolve(violation, Locale.FRENCH), "The name Atlas II is taken");
    } finally {
      Locale.setDefault(serverLocale);
    }
  }

  private static BusinessRuleViolationException expectRejected(Runnable call, String... codes) {
    try {
      call.run();
    } catch (BusinessRuleViolationException e) {
      var found = e.violations().stream().map(Violation::code).toList();
      if (!found.equals(List.of(codes))) {
        throw new AssertionError(
            "expected the violations " + List.of(codes) + " but got " + found, e);
      }
      return e;
    }
    throw new AssertionError("the call passed, but the rules should have rejected it");
  }

  private static void expectProblem(Problems.Problem problem, int status, String message) {
    expect(problem.status(), status);
    expect(problem.violations().getFirst().message(), message);
  }

  private static void expectLogged(List<String> log, String start) {
    if (log.stream().noneMatch(line -> line.startsWith(start + " in "))) {
      throw new AssertionError("expected a line that starts with '" + start + "' in " + log);
    }
  }

  private static void expect(Object actual, Object expected) {
    if (!actual.equals(expected)) {
      throw new AssertionError("expected " + expected + " but got " + actual);
    }
  }
}
