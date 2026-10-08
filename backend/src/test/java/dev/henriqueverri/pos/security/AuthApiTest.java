package dev.henriqueverri.pos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dev.henriqueverri.pos.support.ApiIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.ResultActions;

class AuthApiTest extends ApiIntegrationTest {

  private static final String TEST_SECRET = "test-only-secret-0123456789abcdef0123456789abcdef";

  @Autowired private JwtDecoder jwtDecoder;

  @Autowired private UserRepository users;

  private ResultActions login(String email, String password) throws Exception {
    return mvc.perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
  }

  @Test
  void loginReturnsAnEightHourHs256Token() throws Exception {
    JsonNode body =
        json(
            login(CASHIER_EMAIL, CASHIER_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE)));

    assertThat(body.get("token_type").asText()).isEqualTo("Bearer");
    assertThat(body.get("expires_in").asLong()).isEqualTo(28_800);
    assertThat(body.get("user").get("email").asText()).isEqualTo(CASHIER_EMAIL);
    assertThat(body.get("user").get("name").asText()).isEqualTo("Caixa de Teste");
    assertThat(body.get("user").get("role").asText()).isEqualTo("CASHIER");

    Jwt jwt = jwtDecoder.decode(body.get("access_token").asText());
    User cashier = users.findByEmail(CASHIER_EMAIL).orElseThrow();
    assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    assertThat(jwt.getSubject()).isEqualTo(cashier.getId().toString());
    assertThat(jwt.getClaimAsString("email")).isEqualTo(CASHIER_EMAIL);
    assertThat(jwt.getClaimAsString("role")).isEqualTo("CASHIER");
    assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
        .isEqualTo(Duration.ofHours(8));
  }

  @Test
  void loginTokenAuthenticatesMe() throws Exception {
    String token =
        json(login(ADMIN_EMAIL.toUpperCase(), ADMIN_PASSWORD).andExpect(status().isOk()))
            .get("access_token")
            .asText();

    mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
        .andExpect(jsonPath("$.role").value("ADMIN"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Test
  void wrongPasswordAndUnknownEmailAreIndistinguishable401() throws Exception {
    JsonNode wrongPassword =
        json(
            login(ADMIN_EMAIL, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(
                    header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE)));
    JsonNode unknownEmail =
        json(login("nobody@test.pos.example", ADMIN_PASSWORD).andExpect(status().isUnauthorized()));

    assertThat(wrongPassword.get("code").asText()).isEqualTo("invalid_credentials");
    assertThat(unknownEmail.get("code").asText()).isEqualTo("invalid_credentials");
    assertThat(unknownEmail.get("detail")).isEqualTo(wrongPassword.get("detail"));
    assertThat(wrongPassword.has("access_token")).isFalse();
  }

  @Test
  void validatesLoginRequest() throws Exception {
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation_failed"))
        .andExpect(jsonPath("$.errors.email").isArray())
        .andExpect(jsonPath("$.errors.password").isArray());
  }

  @Test
  void missingTokenIs401ProblemDetail() throws Exception {
    mvc.perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("unauthorized"))
        .andExpect(jsonPath("$.instance").value("/api/auth/me"));
  }

  @Test
  void malformedTokenIs401() throws Exception {
    expectUnauthorized("not-a-jwt");
  }

  @Test
  void expiredTokenIs401() throws Exception {
    Instant past = Instant.now().minus(Duration.ofHours(9));
    expectUnauthorized(sign(TEST_SECRET, adminClaims(past, past.plus(Duration.ofHours(8)))));
  }

  @Test
  void tokenSignedWithAnotherSecretIs401() throws Exception {
    Instant now = Instant.now();
    String forged =
        sign(
            "attacker-secret-0123456789abcdef0123456789abcdef",
            adminClaims(now, now.plus(Duration.ofHours(8))));
    expectUnauthorized(forged);
  }

  @Test
  void validTokenForAUserThatNoLongerExistsIs401OnMe() throws Exception {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(UUID.randomUUID().toString())
            .claim("email", "gone@test.pos.example")
            .claim("role", "ADMIN")
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofHours(1)))
            .build();

    mvc.perform(
            get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(sign(TEST_SECRET, claims))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("unauthorized"));
  }

  @Test
  void publicEndpointsNeedNoToken() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    login(ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isOk());
  }

  private void expectUnauthorized(String token) throws Exception {
    mvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, bearer(token)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("unauthorized"));
  }

  private JwtClaimsSet adminClaims(Instant issuedAt, Instant expiresAt) {
    User admin = users.findByEmail(ADMIN_EMAIL).orElseThrow();
    return JwtClaimsSet.builder()
        .subject(admin.getId().toString())
        .claim("email", admin.getEmail())
        .claim("role", "ADMIN")
        .issuedAt(issuedAt)
        .expiresAt(expiresAt)
        .build();
  }

  private static String sign(String secret, JwtClaimsSet claims) {
    NimbusJwtEncoder encoder =
        new NimbusJwtEncoder(
            new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
