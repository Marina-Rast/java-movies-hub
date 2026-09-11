package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.model.Movie;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class MoviesApiTest {

    private static final String BASE = "http://localhost:8080";
    private static final Gson gson = new Gson();
    private static MoviesServer server;
    private static HttpClient client;

    @BeforeAll
    static void beforeAll() {
        server = new MoviesServer();
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) server.stop();
    }

    @BeforeEach
    void setUp() {
        server.getStore().clear();
    }

    @Test
    void getMoviesWhenEmptyReturnsEmptyArray() throws Exception {
        HttpResponse<String> resp = sendGet("/movies");
        assertEquals(200, resp.statusCode());
        assertEquals("application/json; charset=UTF-8",
                resp.headers().firstValue("Content-Type").orElse(""));
        assertEquals("[]", resp.body().trim());
    }

    @Test
    void getMoviesWhenHasMoviesReturnsList() throws Exception {
        sendPost("/movies", "{\"title\":\"Матрица\",\"year\":1999}");
        HttpResponse<String> resp = sendGet("/movies");
        assertEquals(200, resp.statusCode());
        List<Movie> movies = parseList(resp.body());
        assertEquals(1, movies.size());
        assertEquals("Матрица", movies.get(0).getTitle());
    }

    @Test
    void postMoviesWhenValidReturns201AndMovie() throws Exception {
        HttpResponse<String> resp = sendPost("/movies",
                "{\"title\":\"Матрица\",\"year\":1999}");
        assertEquals(201, resp.statusCode());
        Movie movie = gson.fromJson(resp.body(), Movie.class);
        assertTrue(movie.getId() > 0);
        assertEquals("Матрица", movie.getTitle());
        assertEquals(1999, movie.getYear());
    }

    @Test
    void postMoviesWhenEmptyTitleReturns422() throws Exception {
        HttpResponse<String> resp = sendPost("/movies",
                "{\"title\":\"\",\"year\":1999}");
        assertEquals(422, resp.statusCode());
    }

    @Test
    void postMoviesWhenTitleTooLongReturns422() throws Exception {
        String longTitle = "a".repeat(101);
        HttpResponse<String> resp = sendPost("/movies",
                "{\"title\":\"" + longTitle + "\",\"year\":1999}");
        assertEquals(422, resp.statusCode());
    }

    @Test
    void postMoviesWhenYearTooOldReturns422() throws Exception {
        HttpResponse<String> resp = sendPost("/movies",
                "{\"title\":\"Фильм\",\"year\":1800}");
        assertEquals(422, resp.statusCode());
    }

    @Test
    void postMoviesWhenWrongContentTypeReturns415() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(415, resp.statusCode());
    }

    @Test
    void getMovieByIdWhenExistsReturns200() throws Exception {
        HttpResponse<String> postResp = sendPost("/movies",
                "{\"title\":\"Матрица\",\"year\":1999}");
        Movie created = gson.fromJson(postResp.body(), Movie.class);

        HttpResponse<String> resp = sendGet("/movies/" + created.getId());
        assertEquals(200, resp.statusCode());
        Movie found = gson.fromJson(resp.body(), Movie.class);
        assertEquals("Матрица", found.getTitle());
    }

    @Test
    void getMovieByIdWhenNotFoundReturns404() throws Exception {
        HttpResponse<String> resp = sendGet("/movies/999");
        assertEquals(404, resp.statusCode());
    }

    @Test
    void getMovieByIdWhenNotNumberReturns400() throws Exception {
        HttpResponse<String> resp = sendGet("/movies/abc");
        assertEquals(400, resp.statusCode());
    }

    @Test
    void deleteMovieWhenExistsReturns204() throws Exception {
        HttpResponse<String> postResp = sendPost("/movies",
                "{\"title\":\"Матрица\",\"year\":1999}");
        Movie created = gson.fromJson(postResp.body(), Movie.class);

        HttpResponse<String> resp = sendDelete("/movies/" + created.getId());
        assertEquals(204, resp.statusCode());
    }

    @Test
    void deleteMovieWhenNotFoundReturns404() throws Exception {
        HttpResponse<String> resp = sendDelete("/movies/999");
        assertEquals(404, resp.statusCode());
    }

    @Test
    void deleteMovieWhenNotNumberReturns400() throws Exception {
        HttpResponse<String> resp = sendDelete("/movies/abc");
        assertEquals(400, resp.statusCode());
    }

    @Test
    void getMoviesByYearReturnsFiltered() throws Exception {
        sendPost("/movies", "{\"title\":\"Матрица\",\"year\":1999}");
        sendPost("/movies", "{\"title\":\"Дюна\",\"year\":2021}");

        HttpResponse<String> resp = sendGet("/movies?year=1999");
        assertEquals(200, resp.statusCode());
        List<Movie> movies = parseList(resp.body());
        assertEquals(1, movies.size());
        assertEquals("Матрица", movies.get(0).getTitle());
    }

    @Test
    void getMoviesByYearWhenNoMatchesReturnsEmpty() throws Exception {
        sendPost("/movies", "{\"title\":\"Матрица\",\"year\":1999}");
        HttpResponse<String> resp = sendGet("/movies?year=2000");
        assertEquals(200, resp.statusCode());
        assertEquals("[]", resp.body().trim());
    }

    @Test
    void postMoviesWhenYearTooFutureReturns422() throws Exception {
        int futureYear = java.time.Year.now().getValue() + 5;
        HttpResponse<String> resp = sendPost("/movies",
                "{\"title\":\"Фильм\",\"year\":" + futureYear + "}");
        assertEquals(422, resp.statusCode());
    }

    @Test
    void getMoviesByYearWhenNotNumberReturns400() throws Exception {
        HttpResponse<String> resp = sendGet("/movies?year=abc");
        assertEquals(400, resp.statusCode());
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .method("PATCH", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, resp.statusCode());
    }

    private HttpResponse<String> sendGet(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .GET()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendPost(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendDelete(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .DELETE()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private List<Movie> parseList(String json) {
        Type type = new TypeToken<List<Movie>>() {}.getType();
        return gson.fromJson(json, type);
    }

    @Test
    void postMoviesWhenInvalidJsonReturns422() throws Exception {
        HttpResponse<String> resp = sendPost("/movies", "{invalid json}");
        assertEquals(422, resp.statusCode());
    }

    @Test
    void postMoviesWhenInvalidReturnsErrorObject() throws Exception {
        HttpResponse<String> resp = sendPost("/movies",
                "{\"title\":\"\",\"year\":1800}");
        assertEquals(422, resp.statusCode());

        JsonObject error = gson.fromJson(resp.body(), JsonObject.class);
        assertTrue(error.has("error"), "Ожидается поле 'error'");
        assertTrue(error.has("details"), "Ожидается поле 'details'");
        assertEquals("Ошибка валидации", error.get("error").getAsString());

        JsonArray details = error.getAsJsonArray("details");
        assertTrue(details.size() >= 2, "Ожидается минимум 2 детали");
    }
}