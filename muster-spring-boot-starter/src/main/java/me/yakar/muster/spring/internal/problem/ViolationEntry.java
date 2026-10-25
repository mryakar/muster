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
package me.yakar.muster.spring.internal.problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What a response says about a violation. Its annotations hold whatever the JSON settings of the
 * application: {@code args} is always written, with every client argument, even when the
 * application leaves out empty values, and {@code target} and {@code category} only when the
 * violation has them. They are read by Jackson, the JSON library of Spring Boot, whose two major
 * versions share them; another library writes the entry by its own rules.
 *
 * @param code the code of the violation
 * @param message the message of the violation in the locale of the call
 * @param target the target of the violation, if it has one
 * @param category the name of the category of the violation, if it has one
 * @param args the client arguments of the violation
 */
@JsonPropertyOrder({"code", "message", "target", "category", "args"})
record ViolationEntry(
    String code,
    String message,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String target,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String category,
    @JsonInclude(value = JsonInclude.Include.ALWAYS, content = JsonInclude.Include.ALWAYS)
        Map<String, Object> args) {}
