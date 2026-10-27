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
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/** A service that calls its advised method through its proxy, or on another instance. */
@Service
public class Careful {

  private final List<Vehicle> saved = new CopyOnWriteArrayList<>();
  private final Careful self;

  public Careful(@Lazy Careful self) {
    this.self = self;
  }

  public void save(Vehicle vehicle) {
    saved.add(vehicle);
  }

  /** Not advised: the name is no rule target. */
  public void save(String name) {}

  public List<Vehicle> saved() {
    return List.copyOf(saved);
  }

  /** Through the proxy, which checks the rules. */
  public void register(String name) {
    self.save(new Vehicle(name));
  }

  public void registerWith(Careful other, String name) {
    other.save(new Vehicle(name));
  }

  /** The first local variable of a static method is a parameter. */
  public static void registerStatically(Careful service, String name) {
    service.save(new Vehicle(name));
  }

  /** The lambda captures the other service, not {@code this}. */
  public void registerEach(Careful other, List<String> names) {
    names.forEach(name -> other.save(new Vehicle(name)));
  }

  public void note(String name) {
    save(name);
  }
}
