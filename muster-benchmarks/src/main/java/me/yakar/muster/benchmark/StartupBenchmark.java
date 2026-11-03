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
package me.yakar.muster.benchmark;

import java.util.concurrent.TimeUnit;
import me.yakar.muster.spring.MusterAutoConfiguration;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.aop.Advisor;
import org.springframework.aop.config.AopConfigUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.Role;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.stereotype.Service;

/**
 * What the Spring Boot starter adds to the startup of an application: the first start of an
 * application in a fresh JVM, with its services, its rules found by the scan of its package, and
 * the starter, against the same application without it, with or without a proxy of each service. An
 * application that proxies its services anyway, such as for transactions, pays for the proxies
 * whether it uses the starter or not. Without the starter, the application imports an
 * auto-configuration that configures nothing, so that it starts through the same machinery of
 * Spring Boot.
 *
 * <p>Each fork measures one start: the classes are loaded, and the code runs before the JIT has
 * compiled it, as when an application starts.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(11)
public class StartupBenchmark {

  /**
   * How many services the application has; each takes a command of its own. The build generates an
   * application for each value, as generateStartupSources in build.gradle.kts lists them.
   */
  @Param({"0", "100", "1000"})
  public int services;

  /**
   * The application: without the starter and without proxies, without the starter but with a proxy
   * of each service that does nothing, or with the starter.
   */
  @Param({"plain", "proxied", "muster"})
  public String app;

  private Class<?>[] sources;
  private ConfigurableApplicationContext context;

  /** Finds the application of the services, generated with the benchmarks. */
  @Setup
  public void setUp() throws ClassNotFoundException {
    var application =
        Class.forName("me.yakar.muster.benchmark.startup.s" + services + ".Application");
    sources =
        switch (app) {
          case "plain" -> new Class<?>[] {application, NoAutoConfiguration.class};
          case "proxied" -> new Class<?>[] {application, NoAutoConfiguration.class, Proxied.class};
          case "muster" -> new Class<?>[] {application, Starter.class};
          default -> throw new IllegalArgumentException("unknown application: " + app);
        };
  }

  /**
   * Starts the application, as Spring Boot does, but with no web server, banner or startup log.
   *
   * @return the started application, so that it is not optimized away
   */
  @Benchmark
  public ConfigurableApplicationContext start() {
    context =
        new SpringApplicationBuilder(sources)
            .web(WebApplicationType.NONE)
            .bannerMode(Banner.Mode.OFF)
            .logStartupInfo(false)
            .properties("logging.level.root=warn")
            .run();
    return context;
  }

  /** Stops the application. */
  @TearDown(Level.Iteration)
  public void tearDown() {
    context.close();
  }

  /** The starter. */
  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration(MusterAutoConfiguration.class)
  static class Starter {}

  /** An auto-configuration that configures nothing, in place of the starter's. */
  @Configuration(proxyBeanMethods = false)
  @ImportAutoConfiguration(NoAutoConfiguration.Empty.class)
  static class NoAutoConfiguration {
    @AutoConfiguration
    static class Empty {}
  }

  /**
   * A proxy of each service whose advice does nothing, made by the proxy creator that the starter
   * registers, with class proxies as the starter's default.
   */
  @Configuration(proxyBeanMethods = false)
  @Import(Proxied.ProxyCreator.class)
  static class Proxied {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor noAdviceAdvisor() {
      return new DefaultPointcutAdvisor(
          new AnnotationMatchingPointcut(Service.class),
          (MethodInterceptor) MethodInvocation::proceed);
    }

    /** Registers the proxy creator, as the starter does. */
    static class ProxyCreator implements ImportBeanDefinitionRegistrar {
      @Override
      public void registerBeanDefinitions(
          AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        AopConfigUtils.registerAutoProxyCreatorIfNecessary(registry);
        AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
      }
    }
  }
}
