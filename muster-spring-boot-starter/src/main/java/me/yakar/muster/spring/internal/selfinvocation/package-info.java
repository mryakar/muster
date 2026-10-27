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
/**
 * Finds the calls that bypass Muster's advice: a method of an advised class that calls an advised
 * method on {@code this} passes no proxy, so the rules of that method do not run. Not part of the
 * API: anything in this package may change in any release.
 */
@NullMarked
package me.yakar.muster.spring.internal.selfinvocation;

import org.jspecify.annotations.NullMarked;
