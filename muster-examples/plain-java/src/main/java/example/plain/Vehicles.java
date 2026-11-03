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

import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The vehicles of the fleet, kept in memory where an application keeps them in its database. Rules
 * share it with the service, from any thread.
 */
final class Vehicles {

  private final Map<Long, Vehicle> byId = new ConcurrentHashMap<>();
  private final AtomicLong lastId = new AtomicLong();

  Vehicle add(String name, String plate, String owner) {
    var vehicle = new Vehicle(lastId.incrementAndGet(), name, plate, owner);
    byId.put(vehicle.id(), vehicle);
    return vehicle;
  }

  void rename(long id, String name) {
    byId.computeIfPresent(
        id, (key, vehicle) -> new Vehicle(key, name, vehicle.plate(), vehicle.owner()));
  }

  Optional<Vehicle> find(long id) {
    return Optional.ofNullable(byId.get(id));
  }

  boolean isTaken(String name) {
    return !takenAmong(List.of(name)).isEmpty();
  }

  /** Returns those of the given names that a vehicle has already, in one query. */
  Set<String> takenAmong(Collection<String> names) {
    var taken = byId.values().stream().map(vehicle -> key(vehicle.name())).collect(toSet());
    return names.stream().filter(name -> taken.contains(key(name))).collect(toSet());
  }

  // tag::name-key[]
  /**
   * Returns what makes two names the same name: case aside, in one fixed locale. In the locale of
   * the caller, a Turkish caller would lower ILGAZ to ılgaz and take the name ilgaz a second time.
   */
  static String key(String name) {
    return name.strip().toLowerCase(Locale.ROOT);
  }
  // end::name-key[]
}
