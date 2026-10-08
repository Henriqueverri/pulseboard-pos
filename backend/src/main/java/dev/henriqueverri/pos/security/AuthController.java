package dev.henriqueverri.pos.security;

import dev.henriqueverri.pos.security.dto.LoginRequest;
import dev.henriqueverri.pos.security.dto.LoginResponse;
import dev.henriqueverri.pos.security.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Login com JWT (HS256, 8 h)")
public class AuthController {

  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  @PostMapping("/login")
  @SecurityRequirements
  @Operation(summary = "Troca e-mail e senha por um access token")
  @ApiResponse(responseCode = "200", description = "Token emitido")
  @ApiResponse(
      responseCode = "401",
      description = "invalid_credentials",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return service.login(request);
  }

  @GetMapping("/me")
  @Operation(summary = "Usuário autenticado e seu papel")
  public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
    return service.me(UUID.fromString(jwt.getSubject()));
  }
}
