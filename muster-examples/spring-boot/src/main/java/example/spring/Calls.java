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
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The calls the guide describes, and the answers it shows. */
final class Calls {

  private static final String TURKISH = "tr-TR";
  private static final JsonMapper JSON = JsonMapper.shared();

  private final HttpClient client;
  private final String base;

  Calls(HttpClient client, String base) {
    this.client = client;
    this.base = base;
  }

  void run() {
    // Rules bind to the command by type, inside the transaction of the service method.
    expect(post("/vehicles", "ece", TURKISH, vehicle("Atlas", "34 ABC 123")), 200);
    expect(post("/vehicles", "ece", TURKISH, vehicle("Ilgaz", "06 ANK 42")), 200);
    expect(post("/vehicles", "ece", TURKISH, vehicle("ilgaz", "06 ANK 43")), 409, "name-taken");
    // A caller whose language has no bundle reads the base bundle.
    var french = post("/vehicles", "ece", "fr", vehicle("ILGAZ", "06 ANK 44"));
    expectMessage(french, "The name ILGAZ is taken");
    expectTitle(french, "Business rule violation");
    // Method validation comes first: a blank plate answers 400, although the name is taken too.
    expect(post("/vehicles", "ece", TURKISH, vehicle("Atlas", "")), 400);
    expectMessage(
        post("/vehicles", "ece", TURKISH, vehicle("Boreas", "34-ABC")),
        "34-ABC plakası burada geçerli değil");
    // Without a region in the locale, the plate format does not apply.
    expect(post("/vehicles", "ece", "en", vehicle("Kestrel", "K1")), 200);
    expect(post("/vehicles", null, TURKISH, vehicle("Zephyr", "34 ZEP 20")), 401);

    // The caller is the user that Spring Security signed in.
    expect(put("/vehicles/1/name", "can", name("Atlas II")), 403, "not-owner");
    // The rename collects all: a taken name and a vehicle of another user, of two categories.
    var both = expect(put("/vehicles/1/name", "can", name("Ilgaz")), 422);
    expectCodes(both, "vehicle.name-unique", "vehicle.owned-by-caller");
    expectMessage(put("/vehicles/99/name", "ece", name("Atlas II")), "99 numaralı araç yok");
    // The service answers with the vehicle that VehicleExists loaded, renamed.
    expect(put("/vehicles/1/name", "ece", name("Atlas II")), 200, "renamed");

    expect(
        post("/vehicles/batch", "ece", TURKISH, batch("Boreas", "boreas")),
        409,
        "batch-name-taken");
    expectMessage(
        post("/vehicles/batch", "ece", TURKISH, batch("Boreas", "Zephyr", "Sirocco", "Mistral")),
        "Bir seferde en fazla 3 araç");
    // A missing element is rejected before the rules run.
    expect(post("/vehicles/batch", "ece", TURKISH, "[null]"), 400);
    expect(post("/vehicles/batch", "ece", TURKISH, batch("Boreas", "Zephyr")), 200);

    // A call through the bean, not through this, checks the rules of the method it calls.
    expect(post("/vehicles/1/name-after-plate", "can", TURKISH, ""), 403);
    expect(post("/vehicles/1/name-after-plate", "ece", TURKISH, ""), 200);

    expect(get("/actuator/muster"), 200, "actuator-muster");
    expectTags("muster.check", "name", "outcome");
    expectTags("muster.rule", "rule", "outcome");
  }

  /** Expects the timer that observations of the given name record, with the given tags. */
  private void expectTags(String observation, String... tags) {
    var metric = JSON.readTree(expect(get("/actuator/metrics/" + observation), 200).body());
    var found = metric.get("availableTags").findValuesAsString("tag");
    if (!found.containsAll(List.of(tags))) {
      throw new IllegalStateException("expected the tags " + List.of(tags) + " in " + metric);
    }
  }

  private static void expectCodes(HttpResponse<String> response, String... codes) {
    var found = JSON.readTree(response.body()).path("violations").findValuesAsString("code");
    if (!found.equals(List.of(codes))) {
      throw new IllegalStateException(
          "expected the codes " + List.of(codes) + " in " + response.body());
    }
  }

  private static String vehicle(String name, String plate) {
    return JSON.writeValueAsString(new CreateVehicle(name, plate));
  }

  private static String name(String name) {
    return JSON.writeValueAsString(new VehicleController.NewName(name));
  }

  private static String batch(String... names) {
    var plates = new String[] {"34 BOR 10", "34 ZEP 20", "34 SIR 30", "34 MIS 40"};
    var commands = new CreateVehicle[names.length];
    for (var i = 0; i < names.length; i++) {
      commands[i] = new CreateVehicle(names[i], plates[i]);
    }
    return JSON.writeValueAsString(commands);
  }

  private HttpResponse<String> post(String path, String user, String language, String body) {
    return send(request(path, user, language).POST(HttpRequest.BodyPublishers.ofString(body)));
  }

  private HttpResponse<String> put(String path, String user, String body) {
    return send(request(path, user, TURKISH).PUT(HttpRequest.BodyPublishers.ofString(body)));
  }

  private HttpResponse<String> get(String path) {
    return send(request(path, null, TURKISH).GET());
  }

  private HttpRequest.Builder request(String path, String user, String language) {
    var request =
        HttpRequest.newBuilder(URI.create(base + path))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Accept-Language", language);
    if (user != null) {
      var credentials = user + ":" + user + "-password";
      request.header(
          "Authorization",
          "Basic "
              + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
    }
    return request;
  }

  private HttpResponse<String> send(HttpRequest.Builder request) {
    try {
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static HttpResponse<String> expect(HttpResponse<String> response, int status) {
    if (response.statusCode() != status) {
      throw new IllegalStateException(
          "expected " + status + " but got " + response.statusCode() + " " + response.body());
    }
    return response;
  }

  /** Expects the status, and the body of the file of that name under responses, as JSON. */
  private static void expect(HttpResponse<String> response, int status, String file) {
    expect(response, status);
    var type = response.headers().firstValue("Content-Type").orElse("");
    if (status >= 400 && !type.equals("application/problem+json")) {
      throw new IllegalStateException("expected problem details but got " + type);
    }
    JsonNode expected;
    try (var in = Calls.class.getResourceAsStream("/responses/" + file + ".json")) {
      expected = JSON.readTree(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    if (!expected.equals(JSON.readTree(response.body()))) {
      throw new IllegalStateException(
          "expected the body of " + file + ".json but got " + response.body());
    }
  }

  private static void expectTitle(HttpResponse<String> response, String title) {
    var actual = JSON.readTree(response.body()).path("title");
    if (!actual.asString().equals(title)) {
      throw new IllegalStateException("expected the title " + title + " in " + response.body());
    }
  }

  private static void expectMessage(HttpResponse<String> response, String message) {
    var actual = JSON.readTree(response.body()).path("violations").path(0).path("message");
    if (!actual.asString().equals(message)) {
      throw new IllegalStateException("expected the message " + message + " in " + response.body());
    }
  }
}
