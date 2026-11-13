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

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The format of a typed placeholder, such as {@code number,percent} in {@code
 * {rate,number,percent}}: a type, {@code number}, {@code date}, {@code time} or {@code datetime},
 * and an optional style.
 *
 * <p>A number is formatted with {@link NumberFormat}, as {@link java.text.MessageFormat} formats
 * it, except that a {@link BigDecimal} keeps its scale. A date or a time is formatted with the
 * localized styles of {@link DateTimeFormatter}, so it is a {@code java.time} value that has the
 * fields the style needs.
 *
 * <p>Immutable. The formatters are created for each value, since a {@link NumberFormat} is not safe
 * for use by multiple threads.
 */
final class ValueFormat {

  private enum Type {
    NUMBER,
    INTEGER,
    PERCENT,
    DATE,
    TIME,
    DATETIME
  }

  /** The most digits a {@link BigDecimal} may have before or after its point to be formatted. */
  static final int MAX_DIGITS = 1000;

  private final Type type;
  private final FormatStyle style;

  private ValueFormat(Type type, FormatStyle style) {
    this.type = type;
    this.style = style;
  }

  /**
   * Reads the format after the name of a placeholder.
   *
   * @param format the type and the style, such as {@code date} or {@code date,short}
   * @return the format, or {@code null} if it is not one
   */
  static @Nullable ValueFormat parse(String format) {
    var parts = format.split(",", 2);
    var style = parts.length > 1 ? parts[1] : null;
    return switch (parts[0]) {
      case "number" -> number(style);
      case "date" -> temporal(Type.DATE, style);
      case "time" -> temporal(Type.TIME, style);
      case "datetime" -> temporal(Type.DATETIME, style);
      default -> null;
    };
  }

  private static @Nullable ValueFormat number(@Nullable String style) {
    if (style == null) {
      return new ValueFormat(Type.NUMBER, FormatStyle.MEDIUM);
    }
    return switch (style) {
      case "integer" -> new ValueFormat(Type.INTEGER, FormatStyle.MEDIUM);
      case "percent" -> new ValueFormat(Type.PERCENT, FormatStyle.MEDIUM);
      default -> null;
    };
  }

  private static @Nullable ValueFormat temporal(Type type, @Nullable String style) {
    if (style == null) {
      return new ValueFormat(type, FormatStyle.MEDIUM);
    }
    return switch (style) {
      case "short" -> new ValueFormat(type, FormatStyle.SHORT);
      case "medium" -> new ValueFormat(type, FormatStyle.MEDIUM);
      case "long" -> new ValueFormat(type, FormatStyle.LONG);
      case "full" -> new ValueFormat(type, FormatStyle.FULL);
      default -> null;
    };
  }

  /**
   * Formats a value in a locale.
   *
   * @param value the value
   * @param locale the locale
   * @return the text, or {@code null} if the value is not of the type, lacks what the style needs,
   *     such as the zone of a time in the long style, or is a {@link BigDecimal} with more than
   *     {@value #MAX_DIGITS} digits before or after its point
   */
  @Nullable String format(Object value, Locale locale) {
    return switch (type) {
      case NUMBER, INTEGER, PERCENT ->
          value instanceof Number number ? number(number, locale) : null;
      case DATE, TIME, DATETIME ->
          value instanceof TemporalAccessor temporal ? temporal(temporal, locale) : null;
    };
  }

  private @Nullable String number(Number number, Locale locale) {
    if (number instanceof BigDecimal decimal
        && (decimal.scale() > MAX_DIGITS || decimal.precision() - decimal.scale() > MAX_DIGITS)) {
      // 1E+10000000 is short, but would be written with ten million digits.
      return null;
    }
    var format =
        switch (type) {
          case INTEGER -> NumberFormat.getIntegerInstance(locale);
          case PERCENT -> NumberFormat.getPercentInstance(locale);
          default -> NumberFormat.getNumberInstance(locale);
        };
    if (type == Type.NUMBER && number instanceof BigDecimal decimal) {
      // Raises the maximum with it. A negative scale, as in 1E+3, takes none: the setter clamps it.
      format.setMinimumFractionDigits(decimal.scale());
    }
    return format.format(number);
  }

  private @Nullable String temporal(TemporalAccessor temporal, Locale locale) {
    var formatter =
        switch (type) {
          case DATE -> DateTimeFormatter.ofLocalizedDate(style);
          case TIME -> DateTimeFormatter.ofLocalizedTime(style);
          default -> DateTimeFormatter.ofLocalizedDateTime(style);
        };
    try {
      return formatter.withLocale(locale).format(temporal);
    } catch (DateTimeException e) {
      // An Instant has no date, a LocalDate no time, a LocalTime no zone for the long style.
      return null;
    }
  }
}
