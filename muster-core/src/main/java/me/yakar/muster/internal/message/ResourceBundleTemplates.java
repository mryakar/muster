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
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Looks up message templates in a family of resource bundles, with the code as the key, and never
 * in the bundle of the JVM's default locale. A {@link ResourceBundle.Control} could prevent that
 * fallback, but {@code getBundle} rejects one in a named module, so a bundle of another locale is
 * replaced with the base bundle.
 *
 * <p>The values of a template are formatted in the locale of the call if the template is written in
 * its language, region included, and in the locale of the base bundle if the template comes from
 * the base bundle and the base bundle is not in the language of the call. A bundle inherits the
 * templates it lacks from the base bundle; a template that is the same as the base bundle's counts
 * as the base bundle's.
 *
 * <p>Thread-safe: the only state is the function and the locale, and the JDK caches the bundles.
 */
final class ResourceBundleTemplates implements TemplateLookup {

  private static final ResourceBundle.Control CONTROL =
      ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_DEFAULT);

  private final Function<Locale, ResourceBundle> bundles;
  private final Locale baseLocale;

  /**
   * Creates the lookup, and checks that the family has a base bundle.
   *
   * @param bundles returns the bundle for a locale
   * @param baseLocale the locale of the base bundle, in which its values are formatted
   * @throws IllegalArgumentException if the family has no base bundle
   */
  ResourceBundleTemplates(Function<Locale, ResourceBundle> bundles, Locale baseLocale) {
    this.bundles = requireNonNull(bundles, "bundles");
    this.baseLocale = requireNonNull(baseLocale, "baseLocale");
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

  private static String noBase(@Nullable String detail) {
    return "the resource bundles have no base bundle ("
        + detail
        + "); add one without a locale, such as messages.properties, for the locales that have no"
        + " bundle of their own";
  }

  @Override
  public Optional<LocalizedTemplate> find(String code, Locale locale) {
    var bundle = bundle(locale);
    if (!CONTROL.getCandidateLocales("", locale).contains(bundle.getLocale())) {
      bundle = bundle(Locale.ROOT);
    }
    if (!bundle.containsKey(code)) {
      return Optional.empty();
    }
    var text = bundle.getString(code);
    return Optional.of(new LocalizedTemplate(text, formatLocale(bundle, code, text, locale)));
  }

  private Locale formatLocale(ResourceBundle bundle, String code, String text, Locale locale) {
    if (!isBase(bundle, code, text)) {
      return locale;
    }
    // A base bundle in the language of the call takes the region of the call too.
    var sameLanguage =
        !baseLocale.equals(Locale.ROOT)
            && CONTROL.getCandidateLocales("", locale).contains(baseLocale);
    return sameLanguage ? locale : baseLocale;
  }

  private boolean isBase(ResourceBundle bundle, String code, String text) {
    if (bundle.getLocale().equals(Locale.ROOT)) {
      return true;
    }
    var base = bundle(Locale.ROOT);
    return base.containsKey(code) && text.equals(base.getString(code));
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
