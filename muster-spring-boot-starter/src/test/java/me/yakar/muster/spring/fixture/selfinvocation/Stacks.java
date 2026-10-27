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
 * Calls its advised method with arguments whose code moves the operand stack in every way javac
 * does: copies under other values, wide values, switches, monitors, handlers and arrays.
 */
@Service
public class Stacks {

  private int count;
  private long total;
  private final int[] counts = new int[4];
  private final long[] totals = new long[4];

  public void save(Vehicle vehicle) {}

  public long total() {
    return total;
  }

  public double ratio() {
    return count / 2.0;
  }

  public void compound(int index) {
    save(new Vehicle(String.valueOf(counts[index]++)));
  }

  public void wideCompound(int index) {
    save(new Vehicle(String.valueOf(totals[index] += 1L)));
  }

  public void wideField() {
    save(new Vehicle(String.valueOf(total += 2L)));
  }

  public void assignment() {
    save(new Vehicle(String.valueOf(count = 5)));
  }

  public void discarding() {
    total();
    ratio();
    save(new Vehicle("discarded"));
  }

  public void arithmetic(long a, double b, float c, int d, short e, byte f, char g) {
    var mixed = a * 3 - d % 2 + (long) (b / c) + (e << 1) + (f & 3) + (g | 1) + (int) -b - -a;
    save(new Vehicle(String.valueOf(mixed > 0 ? mixed : (float) a + (double) c)));
  }

  public void switching(int kind, String name) {
    save(
        new Vehicle(
            switch (kind) {
              case 1 -> "one";
              case 2 -> "two";
              default ->
                  switch (name) {
                    case "a" -> "A";
                    case "b" -> "B";
                    default -> name;
                  };
            }));
  }

  public void guarded(Vehicle vehicle) {
    synchronized (this) {
      save(vehicle);
    }
  }

  public void finallySaving(String name) {
    try {
      count++;
    } finally {
      save(new Vehicle(name));
    }
  }

  public void arrays(Object value) {
    var boxes = new Object[] {value};
    var grid = new int[2][3];
    save(new Vehicle(value instanceof String text ? text : boxes.length + "x" + grid.length));
  }

  public void looping(int times) {
    for (var lap = 0; lap < times; lap++) {
      save(new Vehicle("lap " + lap));
    }
  }
}
