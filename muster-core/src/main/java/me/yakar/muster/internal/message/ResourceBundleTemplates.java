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
package me.yakar.muster.internal.message;

import static java.util.Objects.requireNonNull;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Looks up message templates in a family of resource bundles, with the code as the key.
 *
 * <p>The bundle for a locale is the bundle of that locale or of a more general one, and otherwise
 * the base bundle. Unlike {@link ResourceBundle#getBundle(String, Locale)}, it never falls back to
 * the bundle of the default locale of the JVM: a server in Turkey answers a French client from the
 * base bundle, not in Turkish. A {@link ResourceBundle.Control} could prevent that fallback, but
 * {@code getBundle} rejects one in a named module, so a bundle from another locale is replaced with
 * the base bundle instead.
 *
 * <p>It keeps no state of its own besides the function; the JDK caches the bundles.
 */
public final class ResourceBundleTemplates implements BiFunction<String, Locale, Optional<String>> {

  private static final ResourceBundle.Control CONTROL =
      ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_DEFAULT);

  private final Function<Locale, ResourceBundle> bundles;

  /**
   * Creates the lookup, and checks that the family has a base bundle.
   *
   * @param bundles returns the bundle for a locale
   * @throws IllegalArgumentException if the family has no base bundle
   */
  public ResourceBundleTemplates(Function<Locale, ResourceBundle> bundles) {
    this.bundles = requireNonNull(bundles, "bundles");
    ResourceBundle base;
    try {
      base = bundle(Locale.ROOT);
    } catch (MissingResourceException e) {
      throw new IllegalArgumentException(noBase(e.getMessage()), e);
    }
    if (!base.getLocale().equals(Locale.ROOT)) {
      throw new IllegalArgumentException(
          noBase(
              "for Locale.ROOT the function returned the bundle for '" + base.getLocale() + "'"));
    }
  }

  private static String noBase(String detail) {
    return "the resource bundles have no base bundle ("
        + detail
        + "); add one without a locale, such as messages.properties, for the locales that have no"
        + " bundle of their own";
  }

  @Override
  public Optional<String> apply(String code, Locale locale) {
    var bundle = bundle(locale);
    if (!CONTROL.getCandidateLocales("", locale).contains(bundle.getLocale())) {
      bundle = bundle(Locale.ROOT);
    }
    return bundle.containsKey(code) ? Optional.of(bundle.getString(code)) : Optional.empty();
  }

  private ResourceBundle bundle(Locale locale) {
    var bundle =
        requireNonNull(
            bundles.apply(locale), () -> "the function returned no bundle for '" + locale + "'");
    if (bundle.getLocale() == null) {
      // Only getBundle tells a bundle its locale, and the fallback needs it.
      throw new IllegalArgumentException(
          "the function returned a bundle for '"
              + locale
              + "' that ResourceBundle.getBundle did not load; return the bundles getBundle loads,"
              + " or use MessageResolver.ofTemplates for templates from another source");
    }
    return bundle;
  }
}
