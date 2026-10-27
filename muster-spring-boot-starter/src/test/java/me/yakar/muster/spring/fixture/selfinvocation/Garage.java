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
package me.yakar.muster.spring.fixture.selfinvocation;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/** A service whose methods call its advised method on {@code this}, in the ways code does. */
@Service
public class Garage {

  private final List<Vehicle> saved = new CopyOnWriteArrayList<>();

  public void save(Vehicle vehicle) {
    saved.add(vehicle);
  }

  public List<Vehicle> saved() {
    return List.copyOf(saved);
  }

  public void register(String name) {
    save(new Vehicle(name));
  }

  public void registerExplicitly(String name) {
    this.save(new Vehicle(name));
  }

  /** The argument comes from one of two paths; the receiver was pushed before them. */
  public void registerEither(String name, boolean upper) {
    save(upper ? new Vehicle(name.toUpperCase(Locale.ROOT)) : new Vehicle(name));
  }

  public void registerLater(String name) {
    Runnable later = () -> save(new Vehicle(name));
    later.run();
  }

  public void registerNested(String name) {
    Supplier<Runnable> later = () -> () -> save(new Vehicle(name));
    later.get().run();
  }

  public Consumer<Vehicle> saver() {
    return this::save;
  }

  /**
   * A nested class, named after its own simple name; not a bean, so that Garage stays one alone.
   */
  public static class Annex {

    public void save(Vehicle vehicle) {}

    public void register(String name) {
      save(new Vehicle(name));
    }
  }
}
