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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import me.yakar.muster.spi.CheckListener;
import org.jspecify.annotations.Nullable;

/**
 * The listeners registered with a {@code Muster}, as one: told that a check or a rule starts in the
 * order they were registered, and that it ends in the reverse order, so that what their observers
 * open nests. The list is copied when the {@code Muster} is built and never changed, so checks
 * share it.
 *
 * <p>Every observer that was told that a check or a rule started is told that it ended, also when
 * another listener or observer throws: the first exception propagates, and those thrown after it
 * are added to it as suppressed.
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

  /**
   * Asks every listener for its observer. If one throws, the observers returned before it are told
   * that the check ended with that exception.
   */
  @Override
  public @Nullable Observer checkStarted(Check check) {
    List<Observer> observers = null;
    for (var listener : listeners) {
      Observer observer;
      try {
        observer = listener.checkStarted(check);
      } catch (Throwable e) {
        if (observers != null) {
          var end = new CheckEnd(Outcome.ERROR, Duration.ZERO, List.of(), e);
          tellEnd(observers, observers.size(), Observer::checkEnded, end, e, e);
        }
        throw e;
      }
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

  /**
   * Tells every observer that a rule or the check ended, last one first, then throws the first
   * exception one of them threw, with those thrown after it suppressed on it.
   */
  private static <E> void tellEveryEnd(
      List<Observer> observers, BiConsumer<Observer, E> event, E end, @Nullable Throwable told) {
    for (var i = observers.size() - 1; i >= 0; i--) {
      try {
        event.accept(observers.get(i), end);
      } catch (Throwable e) {
        tellEnd(observers, i, event, end, e, told);
        throw e;
      }
    }
  }

  /**
   * Tells the observers before an index that a rule or the check ended, last one first. What one
   * throws is suppressed on the exception that propagates, unless it is the exception the end tells
   * of, which propagates anyway.
   */
  private static <E> void tellEnd(
      List<Observer> observers,
      int before,
      BiConsumer<Observer, E> event,
      E end,
      Throwable propagating,
      @Nullable Throwable told) {
    for (var i = before - 1; i >= 0; i--) {
      try {
        event.accept(observers.get(i), end);
      } catch (Throwable fromObserver) {
        if (fromObserver != told) {
          ObservedCheck.suppress(propagating, fromObserver);
        }
      }
    }
  }

  /**
   * The observers of one check, told that a rule starts in the order of their listeners, and that
   * it or the check ends in the reverse order. If one throws when a rule starts, those told before
   * it are told that the rule ended with that exception.
   */
  private record Observers(List<Observer> observers) implements Observer {

    @Override
    public void ruleStarted(String ruleId) {
      for (var i = 0; i < observers.size(); i++) {
        try {
          observers.get(i).ruleStarted(ruleId);
        } catch (Throwable e) {
          var end = new RuleEnd(ruleId, Outcome.ERROR, Duration.ZERO, List.of(), e);
          tellEnd(observers, i, Observer::ruleEnded, end, e, e);
          throw e;
        }
      }
    }

    @Override
    public void ruleEnded(RuleEnd end) {
      tellEveryEnd(observers, Observer::ruleEnded, end, end.error());
    }

    @Override
    public void checkEnded(CheckEnd end) {
      tellEveryEnd(observers, Observer::checkEnded, end, end.error());
    }
  }
}
