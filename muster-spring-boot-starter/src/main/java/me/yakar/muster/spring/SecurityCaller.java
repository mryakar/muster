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

import java.security.Principal;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

/**
 * Reads the caller from Spring Security: the authentication of the current call, or none for an
 * anonymous call. Used only when Spring Security is on the class path.
 */
final class SecurityCaller {

  private final Supplier<SecurityContextHolderStrategy> strategy;

  /**
   * Creates the reader.
   *
   * @param strategy supplies the strategy that holds the security context, which an application may
   *     define as a bean, as Spring Security's own filters read it
   */
  SecurityCaller(Supplier<SecurityContextHolderStrategy> strategy) {
    this.strategy = strategy;
  }

  @Nullable Principal current() {
    var authentication = strategy.get().getContext().getAuthentication();
    return authentication instanceof AnonymousAuthenticationToken ? null : authentication;
  }
}
