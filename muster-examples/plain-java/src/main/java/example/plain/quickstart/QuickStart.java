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
package example.plain.quickstart;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import me.yakar.muster.BusinessRuleViolationException;
import me.yakar.muster.Muster;
import me.yakar.muster.rule.Category;
import me.yakar.muster.rule.Violation;
import me.yakar.muster.spi.MessageResolver;

/**
 * The quick start of the documentation, step by step: two rules for signing up, a Muster with them,
 * and the answers to three sign-ups. It fails if Muster does not answer as the quick start says.
 */
public final class QuickStart {

  private QuickStart() {}

  /** Runs the steps of the quick start. */
  public static void run() {
    // tag::build[]
    var muster =
        Muster.builder().rules(new AdultsOnly(), new UsernameFree(Set.of("ece", "can"))).build();
    // end::build[]

    // tag::try[]
    var deniz = signUp(muster, new SignUp("deniz", 30)); // empty: both rules passed
    var ece = signUp(muster, new SignUp("ece", 30)).orElseThrow(); // the name is taken
    ece.code(); // "signup.username-free", the id of the rule
    ece.target(); // Optional[username], the field to correct
    ece.clientArgs(); // {username=ece}, what a message may tell the user
    var young = signUp(muster, new SignUp("deniz", 16)).orElseThrow(); // too young
    young.code(); // "signup.adult"
    // end::try[]
    expect(deniz, Optional.empty());
    expect(ece.code(), "signup.username-free");
    expect(ece.target(), Optional.of("username"));
    expect(ece.clientArgs().get("username"), "ece");
    expect(ece.category(), Optional.of(Category.CONFLICT));
    expect(young.code(), "signup.adult");
    expect(young.clientArgs().get("age"), 16);
    // Both rules fail, and fail-fast stops at the first: rules of a type run in the order of ids.
    try {
      muster.check(new SignUp("can", 12));
      throw new AssertionError("expected a violation");
    } catch (BusinessRuleViolationException e) {
      expect(e.violations().stream().map(Violation::code).toList(), List.of("signup.adult"));
    }

    // tag::messages[]
    var messages =
        MessageResolver.ofResourceBundles(
            locale -> ResourceBundle.getBundle("example.plain.quickstart.messages", locale));
    var english = messages.resolve(ece, Locale.ENGLISH); // "The username ece is taken"
    var turkish = messages.resolve(ece, Locale.forLanguageTag("tr")); // "ece kullanıcı adı alınmış"
    // end::messages[]
    expect(english, "The username ece is taken");
    expect(turkish, "ece kullanıcı adı alınmış");
    expect(
        messages.resolve(young, Locale.ENGLISH), "You must be 18 or older to sign up; you are 16");
  }

  // tag::sign-up[]
  /** Checks a sign-up; returns the violation that rejected it, or nothing if it passed. */
  static Optional<Violation> signUp(Muster muster, SignUp input) {
    try {
      muster.check(input); // runs every rule of SignUp; throws if one is violated
      return Optional.empty();
    } catch (BusinessRuleViolationException e) {
      return Optional.of(e.violations().getFirst()); // the first violated rule
    }
  }

  // end::sign-up[]

  private static void expect(Object actual, Object expected) {
    if (!actual.equals(expected)) {
      throw new AssertionError("expected " + expected + " but got " + actual);
    }
  }
}
