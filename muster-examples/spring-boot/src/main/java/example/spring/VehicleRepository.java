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
package example.spring;

import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** The vehicles of the fleet, in the database. */
@Repository
public class VehicleRepository {

  private final JdbcClient jdbc;

  public VehicleRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Vehicle add(String name, String plate, String owner) {
    var keys = new GeneratedKeyHolder();
    jdbc.sql(
            "insert into vehicle (name, name_key, plate, owner)"
                + " values (:name, :key, :plate, :owner)")
        .param("name", name)
        .param("key", key(name))
        .param("plate", plate)
        .param("owner", owner)
        .update(keys, "id");
    return new Vehicle(keys.getKeyAs(Long.class), name, plate, owner);
  }

  public void rename(long id, String name) {
    jdbc.sql("update vehicle set name = :name, name_key = :key where id = :id")
        .param("name", name)
        .param("key", key(name))
        .param("id", id)
        .update();
  }

  public Optional<Vehicle> find(long id) {
    return jdbc.sql("select id, name, plate, owner from vehicle where id = :id")
        .param("id", id)
        .query(Vehicle.class)
        .optional();
  }

  public boolean isTaken(String name) {
    return !takenAmong(List.of(name)).isEmpty();
  }

  /** Returns those of the given names that a vehicle has already, in one query. */
  public Set<String> takenAmong(Collection<String> names) {
    if (names.isEmpty()) {
      return Set.of();
    }
    var taken =
        Set.copyOf(
            jdbc.sql("select name_key from vehicle where name_key in (:keys)")
                .param("keys", names.stream().map(VehicleRepository::key).toList())
                .query(String.class)
                .list());
    return names.stream().filter(name -> taken.contains(key(name))).collect(toSet());
  }

  /** Returns what makes two names the same name: case aside, in one fixed locale. */
  static String key(String name) {
    return name.strip().toLowerCase(Locale.ROOT);
  }
}
