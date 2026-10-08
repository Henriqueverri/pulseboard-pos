package dev.henriqueverri.pos.customer;

import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.henriqueverri.pos.support.ApiIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerApiTest extends ApiIntegrationTest {

  @Test
  void createsCustomerWithNormalizedEmail() throws Exception {
    String local = unique("Maria.Souza");
    String id =
        json(postJson(
                    "/api/customers",
                    "{\"name\":\" Maria Souza \",\"email\":\""
                        + local.toUpperCase()
                        + "@Cliente.POS.example\",\"document\":\"123.456.789-00\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Maria Souza"))
                .andExpect(jsonPath("$.email").value(local.toLowerCase() + "@cliente.pos.example"))
                .andExpect(jsonPath("$.document").value("123.456.789-00")))
            .get("id")
            .asText();

    getJson("/api/customers/" + id).andExpect(status().isOk());
  }

  @Test
  void emailIsUniqueIgnoringCase() throws Exception {
    String email = unique("dup") + "@cliente.pos.example";
    postJson("/api/customers", "{\"name\":\"A\",\"email\":\"" + email + "\"}")
        .andExpect(status().isCreated());

    postJson("/api/customers", "{\"name\":\"B\",\"email\":\"" + email.toUpperCase() + "\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("duplicate_email"));

    String otherId =
        json(postJson(
                "/api/customers",
                "{\"name\":\"C\",\"email\":\"" + unique("other") + "@cliente.pos.example\"}"))
            .get("id")
            .asText();
    putJson("/api/customers/" + otherId, "{\"name\":\"C\",\"email\":\"" + email + "\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("duplicate_email"));
  }

  @Test
  void updatesCustomerKeepingOwnEmail() throws Exception {
    String email = unique("self") + "@cliente.pos.example";
    String id =
        json(postJson("/api/customers", "{\"name\":\"Antes\",\"email\":\"" + email + "\"}"))
            .get("id")
            .asText();

    putJson("/api/customers/" + id, "{\"name\":\"Depois\",\"email\":\"" + email + "\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Depois"))
        .andExpect(jsonPath("$.document").isEmpty());
  }

  @Test
  void validatesCustomer() throws Exception {
    postJson("/api/customers", "{\"name\":\"\",\"email\":\"not-an-email\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_failed"))
        .andExpect(jsonPath("$.errors", hasKey("name")))
        .andExpect(jsonPath("$.errors", hasKey("email")));
    postJson("/api/customers", "{\"name\":\"Sem e-mail\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors", hasKey("email")));
  }

  @Test
  void unknownCustomerReturns404() throws Exception {
    putJson(
            "/api/customers/" + UUID.randomUUID(),
            "{\"name\":\"X\",\"email\":\"x@cliente.pos.example\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("not_found"));
  }

  @Test
  void searchesByNameOrEmail() throws Exception {
    String marker = unique("busca");
    postJson(
            "/api/customers",
            "{\"name\":\"Zé "
                + marker
                + "\",\"email\":\""
                + unique("ze")
                + "@cliente.pos.example\"}")
        .andExpect(status().isCreated());
    postJson(
            "/api/customers",
            "{\"name\":\"Outra\",\"email\":\"" + marker + "@cliente.pos.example\"}")
        .andExpect(status().isCreated());

    getJson("/api/customers?q=" + marker.toUpperCase())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].name").value("Outra"));
    getJson("/api/customers?q=" + marker + "&size=1&page=1")
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.totalPages").value(2));
  }
}
