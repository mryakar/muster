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
 * {@link PublishResults}, which publishes the results of a JMH run of the benchmarks to the
 * documentation: a copy of them, the summaries of their medians, a chart, and the tables of the
 * benchmarks page; and {@link BenchmarkTables}, which writes the tables again from the published
 * results, or checks that they are those the published results give.
 */
@NullMarked
package me.yakar.muster.benchmark.report;

import org.jspecify.annotations.NullMarked;
