package com.vinsguru.netflux.user;

import com.vinsguru.netflux.user.dto.AddWatchlistRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class WatchlistApiTest {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Autowired
    RestTestClient restTestClient;

    @Test
    @DisplayName("Should return movieIds of the user in insertion order")
    @Sql(scripts = "/sql/insert-watchlist.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/delete-watchlist.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void shouldReturnWatchlist() {
        var movieIds = getWatchlist(100L);

        assertThat(movieIds).containsExactly(123L, 456L);
    }

    @Test
    @DisplayName("Should return empty list when user has no watchlist entries")
    void shouldReturnEmptyWatchlist() {
        var movieIds = getWatchlist(100L);

        assertThat(movieIds).isEmpty();
    }

    @Test
    @DisplayName("Should add movie to watchlist")
    @Sql(scripts = "/sql/delete-watchlist.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void shouldAddMovie() {
        restTestClient.post()
                .uri("/api/users/watchlist")
                .header(USER_ID_HEADER, "100")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AddWatchlistRequest(123L))
                .exchange()
                .expectStatus().isCreated()
                .expectBody().isEmpty();

        assertThat(getWatchlist(100L)).containsExactly(123L);
    }

    @Test
    @DisplayName("Should return 201 without creating a duplicate when movie already exists")
    @Sql(scripts = "/sql/insert-watchlist.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/delete-watchlist.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void shouldNotDuplicateMovie() {
        restTestClient.post()
                .uri("/api/users/watchlist")
                .header(USER_ID_HEADER, "100")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AddWatchlistRequest(123L))
                .exchange()
                .expectStatus().isCreated();

        assertThat(getWatchlist(100L)).containsExactly(123L, 456L);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L})
    @DisplayName("Should return 400 ProblemDetail for invalid movieId")
    void shouldReturn400ForInvalidMovieId(Long movieId) {
        var problem = restTestClient.post()
                .uri("/api/users/watchlist")
                .header(USER_ID_HEADER, "100")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AddWatchlistRequest(movieId))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo("Validation failed");
    }

    @Test
    @DisplayName("Should remove movie from watchlist")
    @Sql(scripts = "/sql/insert-watchlist.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "/sql/delete-watchlist.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void shouldRemoveMovie() {
        restTestClient.delete()
                .uri("/api/users/watchlist/{movieId}", 123)
                .header(USER_ID_HEADER, "100")
                .exchange()
                .expectStatus().isNoContent();

        assertThat(getWatchlist(100L)).containsExactly(456L);
    }

    @Test
    @DisplayName("Should return 204 when removing a movie not in the watchlist")
    void shouldReturn204WhenMovieNotInWatchlist() {
        restTestClient.delete()
                .uri("/api/users/watchlist/{movieId}", 999)
                .header(USER_ID_HEADER, "100")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    @DisplayName("Should return 401 ProblemDetail when X-User-Id header is missing")
    void shouldReturn401WhenHeaderMissing() {
        var problem = restTestClient.get()
                .uri("/api/users/watchlist")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(problem.getDetail()).isEqualTo("Missing or invalid X-User-Id header");
    }

    @Test
    @DisplayName("Should return 401 ProblemDetail when X-User-Id header is not a number")
    void shouldReturn401WhenHeaderInvalid() {
        var problem = restTestClient.get()
                .uri("/api/users/watchlist")
                .header(USER_ID_HEADER, "abc")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody(ProblemDetail.class)
                .returnResult()
                .getResponseBody();

        assertThat(problem.getStatus()).isEqualTo(401);
        assertThat(problem.getDetail()).isEqualTo("Missing or invalid X-User-Id header");
    }

    private List<Long> getWatchlist(Long userId) {
        return restTestClient.get()
                .uri("/api/users/watchlist")
                .header(USER_ID_HEADER, String.valueOf(userId))
                .exchange()
                .expectStatus().isOk()
                .expectBody(new ParameterizedTypeReference<List<Long>>() {})
                .returnResult()
                .getResponseBody();
    }
}
