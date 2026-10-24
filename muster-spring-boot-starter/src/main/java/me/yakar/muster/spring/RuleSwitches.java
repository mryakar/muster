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
package me.yakar.muster.spring;

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
 * Reads the configuration of Muster: {@code muster.mode}, and {@code muster.rules.<rule
 * id>.enabled} for every rule.
 *
 * <p>The keys of the rules are read one rule id at a time rather than bound as a map: a rule id has
 * dots and hyphens, which a map key loses, and so does an environment variable such as {@code
 * MUSTER_RULES_VEHICLE_NAMEUNIQUE_ENABLED}. Since every rule id is known, every key under {@code
 * muster.rules} that switches no rule is a mistake, such as a misspelled id.
 */
final class RuleSwitches {

  private static final ConfigurationPropertyName RULES =
      ConfigurationPropertyName.of("muster.rules");

  private RuleSwitches() {}

  static CheckMode mode(Environment environment) {
    return Binder.get(environment).bind("muster.mode", CheckMode.class).orElse(CheckMode.FAIL_FAST);
  }

  /**
   * Returns the ids of the rules that the configuration switches off.
   *
   * @throws IllegalStateException if a key under {@code muster.rules} switches no rule
   */
  static List<String> disabled(Environment environment, Collection<String> ids) {
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
      if (!binder.bind(key, Bindable.of(Boolean.class)).orElse(true)) {
        disabled.add(id);
      }
    }
    var unknown = new TreeSet<String>();
    for (var source : ConfigurationPropertySources.get(environment)) {
      if (source instanceof IterableConfigurationPropertySource iterable) {
        iterable.filter(RULES::isAncestorOf).stream()
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
}
