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
package me.yakar.muster;

import java.util.ArrayList;
import java.util.List;
import me.yakar.muster.spi.CheckListener;
import org.jspecify.annotations.Nullable;

/**
 * The listeners registered with a {@code Muster}, told in the order they were registered, as one.
 * The list is copied when the {@code Muster} is built and never changed, so checks share it.
 */
final class CheckListeners implements CheckListener {

  private final List<CheckListener> listeners;

  private CheckListeners(List<CheckListener> listeners) {
    this.listeners = listeners;
  }

  /**
   * Returns the registered listeners as one: {@code null} for none, the listener itself for one, so
   * that a check pays no more than it has to.
   */
  static @Nullable CheckListener of(List<CheckListener> listeners) {
    return switch (listeners.size()) {
      case 0 -> null;
      case 1 -> listeners.getFirst();
      default -> new CheckListeners(List.copyOf(listeners));
    };
  }

  @Override
  public @Nullable Observer checkStarted(Check check) {
    List<Observer> observers = null;
    for (var listener : listeners) {
      var observer = listener.checkStarted(check);
      if (observer != null) {
        if (observers == null) {
          observers = new ArrayList<>(listeners.size());
        }
        observers.add(observer);
      }
    }
    return observers == null
        ? null
        : observers.size() == 1 ? observers.getFirst() : new Observers(observers);
  }

  /** The observers of one check, told in the order of their listeners. */
  private record Observers(List<Observer> observers) implements Observer {

    @Override
    public void ruleStarted(String ruleId) {
      for (var observer : observers) {
        observer.ruleStarted(ruleId);
      }
    }

    @Override
    public void ruleEnded(RuleEnd end) {
      for (var observer : observers) {
        observer.ruleEnded(end);
      }
    }

    @Override
    public void checkEnded(CheckEnd end) {
      for (var observer : observers) {
        observer.checkEnded(end);
      }
    }
  }
}
