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

import org.springframework.stereotype.Service;

/**
 * Its bridge method, {@code save(RuleTarget)}, calls {@code save(Vehicle)} on {@code this}, and its
 * override calls the base through the erased signature, with the input the proxy checked.
 */
@Service
public class VehicleDepot extends Depot<Vehicle> {

  @Override
  public void save(Vehicle target) {
    super.save(target);
  }

  @Override
  protected Vehicle target(String name) {
    return new Vehicle(name);
  }
}
