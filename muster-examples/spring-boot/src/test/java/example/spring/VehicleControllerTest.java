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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import me.yakar.muster.BusinessRuleViolationException;
import me.yakar.muster.rule.Category;
import me.yakar.muster.rule.RuleContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// tag::web-mvc-test[]
/** The slice has the starter's answer to violations and its messages, but no rules: none runs. */
@WebMvcTest(VehicleController.class)
@Import(SecurityConfiguration.class)
class VehicleControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private VehicleService vehicles;

  @Test
  @WithMockUser("ece")
  void answersATakenNameWithProblemDetails() throws Exception {
    var taken =
        RuleContext.forRule(VehicleNameUnique.class)
            .violation()
            .clientArg("name", "Atlas")
            .target("name")
            .category(Category.CONFLICT)
            .build();
    when(vehicles.create(any())).thenThrow(new BusinessRuleViolationException(List.of(taken)));

    mvc.perform(
            post("/vehicles")
                .contentType(APPLICATION_JSON)
                .header("Accept-Language", "tr-TR")
                .content("{\"name\": \"Atlas\", \"plate\": \"34 ABC 124\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.violations[0].message").value("Atlas adı kullanılıyor"));
  }
}
// end::web-mvc-test[]
