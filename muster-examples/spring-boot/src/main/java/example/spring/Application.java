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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Starts a Spring Boot application the way the README shows, sends it requests over HTTP, and fails
 * if the starter does not check the rule of the service or does not answer the violation with
 * problem details in the language of the client.
 */
@SpringBootApplication
public class Application {

  /** Creates the application. */
  public Application() {}

  /**
   * Runs the checks.
   *
   * @param args not used
   * @throws Exception if a request fails
   */
  public static void main(String[] args) throws Exception {
    try (var context =
        new SpringApplicationBuilder(Application.class).properties("server.port=0").run(args)) {
      var port = context.getEnvironment().getRequiredProperty("local.server.port");
      var uri = URI.create("http://localhost:" + port + "/vehicles");
      try (var client = HttpClient.newHttpClient()) {
        expect(create(client, uri, "Boreas"), 200, "", "");

        var violated = create(client, uri, "Atlas");
        expect(
            violated,
            409,
            "application/problem+json",
            """
            {
              "title": "Conflict",
              "status": 409,
              "instance": "/vehicles",
              "violations": [
                {
                  "code": "vehicle.name-taken",
                  "message": "Atlas adı alınmış",
                  "target": "name",
                  "category": "CONFLICT",
                  "args": {"name": "Atlas"}
                }
              ]
            }
            """);
      }
    }
  }

  private static HttpResponse<String> create(HttpClient client, URI uri, String name)
      throws IOException, InterruptedException {
    var request =
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Accept-Language", "tr-TR")
            .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"" + name + "\"}"))
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static void expect(
      HttpResponse<String> response, int status, String contentType, String body) {
    var actualType = response.headers().firstValue("Content-Type").orElse("");
    var json = JsonMapper.shared();
    var bodyMatches =
        body.isEmpty()
            ? response.body().isEmpty()
            : json.readTree(body).equals(json.readTree(response.body()));
    if (response.statusCode() != status || !actualType.equals(contentType) || !bodyMatches) {
      throw new IllegalStateException(
          "expected "
              + status
              + " "
              + contentType
              + " "
              + body
              + ", got "
              + response.statusCode()
              + " "
              + actualType
              + " "
              + response.body());
    }
  }
}
