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
package me.yakar.muster.spring

import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/** Captures what Muster logs through the JDK, at every level, until it is closed. */
final class LogCapture implements AutoCloseable {

  /** The lines logged so far; threads may log at once. */
  final List<LogRecord> records = new CopyOnWriteArrayList<>()

  private final Logger logger = Logger.getLogger('me.yakar.muster')
  private final Level level = logger.level
  private final Handler handler = new Handler() {
    void publish(LogRecord record) {
      records << record
    }

    void flush() {}

    void close() {}
  }

  LogCapture() {
    logger.level = Level.ALL
    logger.addHandler(handler)
  }

  List<String> getMessages() {
    records*.message
  }

  void close() {
    logger.removeHandler(handler)
    logger.level = level
  }
}
