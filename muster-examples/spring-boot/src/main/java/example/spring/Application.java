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

import java.net.http.HttpClient;
import java.util.Locale;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * A Spring Boot application whose samples the guide to the starter includes. It starts, makes the
 * calls the guide describes over HTTP, and fails if the answers are not those the guide shows.
 */
@SpringBootApplication
public class Application {

  public static void main(String[] args) throws Exception {
    // A server whose own locale is Turkish, to show which messages a French caller reads.
    Locale.setDefault(Locale.forLanguageTag("tr-TR"));
    try (var context =
            new SpringApplicationBuilder(Application.class).properties("server.port=0").run(args);
        var client = HttpClient.newHttpClient()) {
      var port = context.getEnvironment().getRequiredProperty("local.server.port");
      new Calls(client, "http://localhost:" + port).run();
    }
    System.out.println("The Spring Boot example works");
  }
}
