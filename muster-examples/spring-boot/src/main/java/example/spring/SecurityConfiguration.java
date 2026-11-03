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
package example.spring;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Signs users in with HTTP Basic; the starter passes the signed-in user to the rules as the caller.
 * An application signs its users in as it already does.
 */
@Configuration
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain api(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/actuator/**")
                    .permitAll() // for the example only: an application protects its actuator
                    .anyRequest()
                    .authenticated())
        .httpBasic(withDefaults())
        .csrf(csrf -> csrf.disable()) // an API for clients that send credentials, not a browser
        .build();
  }

  /** Two users, with passwords that only an example may have. */
  @Bean
  InMemoryUserDetailsManager users() {
    return new InMemoryUserDetailsManager(
        User.withUsername("ece").password("{noop}ece-password").roles("USER").build(),
        User.withUsername("can").password("{noop}can-password").roles("USER").build());
  }
}
