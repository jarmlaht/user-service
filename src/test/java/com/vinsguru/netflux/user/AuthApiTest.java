package com.vinsguru.netflux.user;

import com.vinsguru.netflux.user.dto.LoginRequest;
import com.vinsguru.netflux.user.dto.LoginResponse;
import com.vinsguru.netflux.user.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.Duration;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class AuthApiTest {

    @Autowired
    RestTestClient restTestClient;

    @Autowired
    JdbcClient jdbcClient;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Should register user and store BCrypt-hashed password")
    @Sql(scripts = "/sql/delete-users.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void shouldRegisterUser() {
        restTestClient.post()
                .uri("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest("alice", "secret123", "Alice"))
                .exchange()
                .expectStatus().isCreated()
                .expectBody().isEmpty();

        var storedPassword = jdbcClient.sql("SELECT password FROM users WHERE username = ?")
                .param("alice")
                .query(String.class)
                .single();
        assertThat(storedPassword).isNotEqualTo("secret123");
        assertThat(passwordEncoder.matches("secret123", storedPassword)).isTrue();
    }

    @Test
    @DisplayName("Should return 409 ProblemDetail when username already exists")
    @Sql(scripts = "/sql/insert-user.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/delete-users.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void shouldReturn409WhenUsernameExists() {
        var problem = restTestClient.post()
                .uri("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest("john", "secret123", "Another John"))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("Conflict");
    }

    @Test
    @DisplayName("Should treat usernames case-sensitively")
    @Sql(scripts = "/sql/insert-user.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/delete-users.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void shouldTreatUsernamesCaseSensitively() {
        restTestClient.post()
                .uri("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegisterRequest("John", "secret123", "Capital John"))
                .exchange()
                .expectStatus().isCreated();
    }

    @ParameterizedTest
    @MethodSource("invalidRegisterRequests")
    @DisplayName("Should return 400 ProblemDetail for invalid register request")
    void shouldReturn400ForInvalidRegisterRequest(RegisterRequest request) {
        var problem = restTestClient.post()
                .uri("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("Validation failed");
    }

    static Stream<RegisterRequest> invalidRegisterRequests() {
        return Stream.of(
                new RegisterRequest("", "secret123", "Alice"),
                new RegisterRequest("alice", " ", "Alice"),
                new RegisterRequest("alice", "secret123", null),
                new RegisterRequest("al", "secret123", "Alice"),
                new RegisterRequest("a".repeat(51), "secret123", "Alice"),
                new RegisterRequest("alice", "short", "Alice"),
                new RegisterRequest("alice", "p".repeat(73), "Alice"),
                new RegisterRequest("alice", "secret123", "n".repeat(101))
        );
    }

    @Test
    @DisplayName("Should return 400 ProblemDetail for malformed JSON")
    void shouldReturn400ForMalformedJson() {
        var problem = restTestClient.post()
                .uri("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{not json")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

        assertThat(problem.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Should return RS256 JWT with userId subject and 1h expiry on login")
    @Sql(scripts = "/sql/insert-user.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/delete-users.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void shouldLoginAndReturnToken() {
        var response = restTestClient.post()
                .uri("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest("john", "password123"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoginResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(response.name()).isEqualTo("John Doe");
        var jwt = NimbusJwtDecoder.withPublicKey(TestRsaKeys.publicKey())
                .build()
                .decode(response.token());
        assertThat(String.valueOf(jwt.getHeaders().get("alg"))).isEqualTo("RS256");
        assertThat(jwt.getSubject()).isEqualTo("100");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("Should return 401 ProblemDetail for wrong password")
    @Sql(scripts = "/sql/insert-user.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/delete-users.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void shouldReturn401ForWrongPassword() {
        var problem = login(new LoginRequest("john", "wrongpassword"));

        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(problem.getDetail()).isEqualTo("Invalid username or password");
    }

    @Test
    @DisplayName("Should return identical 401 ProblemDetail for unknown username")
    void shouldReturn401ForUnknownUsername() {
        var problem = login(new LoginRequest("nobody", "password123"));

        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(problem.getDetail()).isEqualTo("Invalid username or password");
    }

    @Test
    @DisplayName("Should return 400 ProblemDetail for blank login fields")
    void shouldReturn400ForBlankLoginFields() {
        var problem = restTestClient.post()
                .uri("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new LoginRequest("", " "))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

        assertThat(problem.getStatus()).isEqualTo(400);
    }

    private ProblemDetail login(LoginRequest request) {
        return restTestClient.post()
                .uri("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();
    }
}
