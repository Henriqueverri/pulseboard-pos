package dev.henriqueverri.pos.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.henriqueverri.pos.security.Role;
import dev.henriqueverri.pos.security.TokenService;
import dev.henriqueverri.pos.security.UserRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Full application against a real PostgreSQL (Testcontainers). The database is shared by every test
 * class in the context, so tests create their own data with unique values and never assume empty
 * tables (the seeded catalog is the only fixed data, and tests never modify it).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class ApiIntegrationTest {

  public static final String ADMIN_EMAIL = "admin@test.pos.example";
  public static final String ADMIN_PASSWORD = "admin-test-password";
  public static final String CASHIER_EMAIL = "caixa@test.pos.example";
  public static final String CASHIER_PASSWORD = "caixa-test-password";

  @Autowired protected MockMvc mvc;

  @Autowired protected ObjectMapper objectMapper;

  @Autowired private TokenService tokenService;

  @Autowired private UserRepository users;

  /** A real token issued for a seeded user, exactly like the one returned by the login. */
  protected String tokenFor(Role role) {
    String email = role == Role.ADMIN ? ADMIN_EMAIL : CASHIER_EMAIL;
    return tokenService.issue(users.findByEmail(email).orElseThrow()).value();
  }

  protected static String bearer(String token) {
    return "Bearer " + token;
  }

  protected ResultActions getJson(String path) throws Exception {
    return mvc.perform(authenticate(get(path)));
  }

  protected ResultActions postJson(String path, String body) throws Exception {
    return mvc.perform(
        authenticate(post(path).contentType(MediaType.APPLICATION_JSON).content(body)));
  }

  protected ResultActions putJson(String path, String body) throws Exception {
    return mvc.perform(
        authenticate(put(path).contentType(MediaType.APPLICATION_JSON).content(body)));
  }

  /** Domain tests run as ADMIN, who may call every endpoint; the role matrix has its own test. */
  protected MockHttpServletRequestBuilder authenticate(MockHttpServletRequestBuilder request) {
    return request.header(HttpHeaders.AUTHORIZATION, bearer(tokenFor(Role.ADMIN)));
  }

  protected JsonNode json(ResultActions result) throws Exception {
    MvcResult mvcResult = result.andReturn();
    return objectMapper.readTree(mvcResult.getResponse().getContentAsString());
  }

  protected static String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  protected UUID createCustomer() throws Exception {
    String email = unique("cliente") + "@cliente.pos.example";
    String body = "{\"name\":\"Ana Lima\",\"email\":\"" + email + "\"}";
    return UUID.fromString(
        json(postJson("/api/customers", body).andExpect(status().isCreated())).get("id").asText());
  }

  protected UUID seededProductId(String sku) throws Exception {
    JsonNode page = json(getJson("/api/products?q=" + sku).andExpect(status().isOk()));
    return UUID.fromString(page.get("content").get(0).get("id").asText());
  }

  protected JsonNode createOrder(UUID customerId, String itemsJson) throws Exception {
    String body = "{\"customerId\":\"" + customerId + "\",\"items\":" + itemsJson + "}";
    return json(postJson("/api/orders", body).andExpect(status().isCreated()));
  }

  protected JsonNode createPendingOrder() throws Exception {
    UUID product = seededProductId("PB-001-ESS");
    return createOrder(createCustomer(), "[{\"productId\":\"" + product + "\",\"quantity\":1}]");
  }
}
