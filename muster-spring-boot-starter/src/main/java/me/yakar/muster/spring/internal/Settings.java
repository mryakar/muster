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
package me.yakar.muster.spring.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;
import me.yakar.muster.CheckMode;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.IterableConfigurationPropertySource;
import org.springframework.core.env.Environment;

/**
 * Reads the properties that the starter takes: {@code muster.rules.<rule id>.enabled}, {@code
 * muster.mode}, {@code muster.transaction.reorder-advisor}, {@code
 * muster.problem-details.mixed-status} and {@code spring.aop.proxy-target-class}.
 *
 * <p>The keys of the rules are read one rule id at a time rather than bound as a map: a rule id has
 * dots and hyphens, which a map key loses, and so does an environment variable such as {@code
 * MUSTER_RULES_VEHICLE_NAMEUNIQUE_ENABLED}. Since every rule id is known, every key under {@code
 * muster.rules} that switches no rule is a mistake, such as a misspelled id.
 */
public final class Settings {

  private static final ConfigurationPropertyName RULES =
      ConfigurationPropertyName.of("muster.rules");
  private static final ConfigurationPropertyName MODE = ConfigurationPropertyName.of("muster.mode");
  private static final ConfigurationPropertyName REORDER_ADVISOR =
      ConfigurationPropertyName.of("muster.transaction.reorder-advisor");
  private static final ConfigurationPropertyName MIXED_STATUS =
      ConfigurationPropertyName.of("muster.problem-details.mixed-status");
  private static final ConfigurationPropertyName PROXY_TARGET_CLASS =
      ConfigurationPropertyName.of("spring.aop.proxy-target-class");

  /** Unprocessable Content, the status of violations without a category. */
  private static final int UNPROCESSABLE_CONTENT = 422;

  private Settings() {}

  /**
   * Returns the mode the configuration sets.
   *
   * @throws IllegalStateException if {@code muster.mode} is set to an empty value
   */
  public static CheckMode mode(Environment environment) {
    var binder = Binder.get(environment);
    requireValue(binder, MODE, "fail-fast or collect-all");
    return binder.bind(MODE, Bindable.of(CheckMode.class)).orElse(CheckMode.FAIL_FAST);
  }

  /**
   * Returns the ids of the rules that the configuration switches off.
   *
   * @throws IllegalStateException if {@code muster.rules} or a key under it switches no rule, or a
   *     rule's key is set to nothing
   */
  public static List<String> disabled(Environment environment, Collection<String> ids) {
    var binder = Binder.get(environment);
    var keys = new HashMap<ConfigurationPropertyName, String>();
    var disabled = new ArrayList<String>();
    for (var id : ids) {
      // An id that is no valid name is not a valid rule id either, which Muster reports.
      var key = ConfigurationPropertyName.adapt(RULES + "." + id + ".enabled", '.');
      var other = keys.putIfAbsent(key, id);
      if (other != null) {
        throw new IllegalStateException(
            "the rules '"
                + other
                + "' and '"
                + id
                + "' share the key "
                + key
                + ", since configuration ignores hyphens; give one of them another id");
      }
      requireValue(binder, key, "true or false");
      if (!binder.bind(key, Bindable.of(Boolean.class)).orElse(true)) {
        disabled.add(id);
      }
    }
    var unknown = new TreeSet<String>();
    for (var source : ConfigurationPropertySources.get(environment)) {
      if (source instanceof IterableConfigurationPropertySource iterable) {
        iterable.filter(name -> RULES.equals(name) || RULES.isAncestorOf(name)).stream()
            .filter(name -> !keys.containsKey(name))
            .forEach(name -> unknown.add(name.toString()));
      }
    }
    if (!unknown.isEmpty()) {
      throw new IllegalStateException(
          "no rule has the key "
              + String.join(", ", unknown)
              + "; a rule is switched with muster.rules.<rule id>.enabled, and the rule ids are "
              + String.join(", ", new TreeSet<>(ids)));
    }
    return disabled;
  }

  /**
   * Fails if the configuration sets {@code muster.rules} or {@code muster.mode}, which configure
   * only the {@code Muster} that the starter builds, while the application defines its own.
   *
   * @throws IllegalStateException if either is set, naming the keys
   */
  public static void requireNoneForOwnMuster(Environment environment) {
    var keys = new TreeSet<String>();
    for (var source : ConfigurationPropertySources.get(environment)) {
      if (source instanceof IterableConfigurationPropertySource iterable) {
        iterable
            .filter(name -> MODE.equals(name) || RULES.equals(name) || RULES.isAncestorOf(name))
            .forEach(name -> keys.add(name.toString()));
      }
    }
    if (!keys.isEmpty()) {
      throw new IllegalStateException(
          String.join(", ", keys)
              + " configure only the Muster that the starter builds, but the application defines"
              + " a Muster bean of its own; remove these properties, or that bean");
    }
  }

  /**
   * Returns whether Muster may move the transaction advice one step ahead of its own.
   *
   * @throws IllegalStateException if {@code muster.transaction.reorder-advisor} is set to nothing
   */
  public static boolean reorderAdvisor(Environment environment) {
    var binder = Binder.get(environment);
    requireValue(binder, REORDER_ADVISOR, "true or false");
    return binder.bind(REORDER_ADVISOR, Bindable.of(Boolean.class)).orElse(true);
  }

  /**
   * Returns the status that answers violations of different categories.
   *
   * @throws IllegalStateException if {@code muster.problem-details.mixed-status} is set to nothing,
   *     or to a status that is not a client error
   */
  public static int mixedStatus(Environment environment) {
    var binder = Binder.get(environment);
    var values = "a status code from 400 to 499";
    requireValue(binder, MIXED_STATUS, values);
    int status =
        binder.bind(MIXED_STATUS, Bindable.of(Integer.class)).orElse(UNPROCESSABLE_CONTENT);
    if (status < 400 || status > 499) {
      throw new IllegalStateException(
          MIXED_STATUS
              + " is "
              + status
              + "; set it to "
              + values
              + ", since a violation is an error of the client");
    }
    return status;
  }

  /** Returns whether proxies extend the class of their target, as in Spring Boot by default. */
  public static boolean proxyTargetClass(Environment environment) {
    return Binder.get(environment)
        .bind(PROXY_TARGET_CLASS, Bindable.of(Boolean.class))
        .orElse(true);
  }

  /**
   * Rejects a key that is set to nothing, such as {@code muster.mode=}, which Spring Boot would
   * read as not set at all.
   */
  private static void requireValue(Binder binder, ConfigurationPropertyName key, String values) {
    if (binder.bind(key, Bindable.of(String.class)).map(String::isBlank).orElse(false)) {
      throw new IllegalStateException(key + " is set to nothing; set it to " + values);
    }
  }
}
