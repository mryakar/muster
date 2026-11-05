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
package example.plain;

import java.util.function.Supplier;

/**
 * Runs the work of a call in a transaction that the rules and the repositories share through the
 * thread, as an application without Spring would with a JDBC connection whose auto-commit is off.
 */
final class Transactions {

  /** A transaction of the database. */
  interface Transaction {
    void commit();

    void rollback();

    void close();
  }

  // tag::transaction[]
  private static final ThreadLocal<Transaction> CURRENT = new ThreadLocal<>();

  private final Supplier<Transaction> begin;

  Transactions(Supplier<Transaction> begin) {
    this.begin = begin;
  }

  <T> T inTransaction(Supplier<T> work) {
    if (CURRENT.get() != null) {
      throw new IllegalStateException("a transaction is already open on this thread");
    }
    var transaction = begin.get(); // with JDBC, a connection whose auto-commit is off
    CURRENT.set(transaction);
    try {
      var result = work.get(); // the check first, then the writes
      transaction.commit();
      return result;
    } catch (RuntimeException | Error e) {
      transaction.rollback(); // a rejected check has written nothing, and nothing else is kept
      throw e;
    } finally {
      CURRENT.remove(); // leaves nothing behind on a thread of a pool
      transaction.close();
    }
  }

  /** Returns the transaction of the current call, which the rules and the repositories use. */
  static Transaction current() {
    var transaction = CURRENT.get();
    if (transaction == null) {
      throw new IllegalStateException("no transaction on this thread");
    }
    return transaction;
  }
  // end::transaction[]
}
