package dev.henriqueverri.pos.security;

import dev.henriqueverri.pos.security.dto.LoginRequest;
import dev.henriqueverri.pos.security.dto.LoginResponse;
import dev.henriqueverri.pos.security.dto.UserResponse;
import dev.henriqueverri.pos.shared.error.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokenService;

  /** Compared against when the e-mail is unknown, so both failures cost one BCrypt check. */
  private final String unknownUserHash;

  public AuthService(
      UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.tokenService = tokenService;
    this.unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  @Transactional(readOnly = true)
  public LoginResponse login(LoginRequest request) {
    Optional<User> user = users.findByEmail(User.normalizeEmail(request.email()));
    String hash = user.map(User::getPasswordHash).orElse(unknownUserHash);
    if (!passwordEncoder.matches(request.password(), hash) || user.isEmpty()) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "invalid_credentials", "E-mail ou senha inválidos.");
    }
    TokenService.IssuedToken token = tokenService.issue(user.get());
    return new LoginResponse(
        token.value(), "Bearer", token.expiresInSeconds(), UserResponse.from(user.get()));
  }

  /** A valid token whose user no longer exists is treated as unauthenticated. */
  @Transactional(readOnly = true)
  public UserResponse me(UUID userId) {
    return users
        .findById(userId)
        .map(UserResponse::from)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.UNAUTHORIZED, "unauthorized", "Usuário do token não existe mais."));
  }
}
