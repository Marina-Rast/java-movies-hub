package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
import com.google.gson.JsonSyntaxException;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.io.InputStream;
import java.time.Year;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.nio.charset.StandardCharsets;

class MoviesHandler extends BaseHttpHandler {
    private static final int MIN_YEAR = 1888;
    private final MoviesStore store;

    public MoviesHandler(MoviesStore store) {
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String query = ex.getRequestURI().getQuery();

        try {
            if (path.equals("/movies")) {
                if (method.equalsIgnoreCase("GET")) {
                    handleGetAll(ex, query);
                } else if (method.equalsIgnoreCase("POST")) {
                    handlePost(ex);
                } else {
                    sendError(ex, 405, "Такого метода нету");
                }
            } else if (path.startsWith("/movies/")) {
                String idPart = path.substring("/movies/".length());
                if (method.equalsIgnoreCase("GET")) {
                    handleGetById(ex, idPart);
                } else if (method.equalsIgnoreCase("DELETE")) {
                    handleDelete(ex, idPart);
                } else {
                    sendError(ex, 405, "Такого метода нету");
                }
            } else {
                sendError(ex, 404, "Не найдено");
            }
        } catch (Exception e) {
            sendError(ex, 500, "Внутренняя ошибка сервера");
        }
    }

    private void handleGetAll(HttpExchange ex, String query) throws IOException {
        if (query != null && query.startsWith("year=")) {
            String yearStr = query.substring("year=".length());
            try {
                int year = Integer.parseInt(yearStr);
                List<Movie> filtered = store.getByYear(year);
                sendJson(ex, 200, gson.toJson(filtered));
            } catch (NumberFormatException e) {
                sendError(ex, 400, "Неправильный параметр — 'year'");
            }
            return;
        }
        sendJson(ex, 200, gson.toJson(store.getAll()));
    }

    private void handlePost(HttpExchange ex) throws IOException {
        String contentType = ex.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            sendError(ex, 415, "Такого метода нету");
            return;
        }

        String body = readBody(ex);
        Movie input;
        try {
            input = gson.fromJson(body, Movie.class);
        } catch (JsonSyntaxException e) {
            sendError(ex, 422, "Некорректный JSON");
            return;
        }

        if (input == null) {
            sendError(ex, 422, "Пустое тело запроса");
            return;
        }

        List<String> details = new ArrayList<>();
        if (input.getTitle() == null || input.getTitle().isBlank()) {
            details.add("название не должно быть пустым");
        } else if (input.getTitle().length() > 100) {
            details.add("название не должно превышать 100 символов");
        }

        int currentYear = Year.now().getValue();
        if (input.getYear() < MIN_YEAR || input.getYear() > currentYear + 1) {
            details.add("год должен быть между " + MIN_YEAR + " и " + (currentYear + 1));
        }

        if (!details.isEmpty()) {
            sendError(ex, 422, "Ошибка валидации", details);
            return;
        }

        Movie saved = store.add(input);
        sendJson(ex, 201, gson.toJson(saved));
    }

    private void handleGetById(HttpExchange ex, String idPart) throws IOException {
        int id;
        try {
            id = Integer.parseInt(idPart);
        } catch (NumberFormatException e) {
            sendError(ex, 400, "Некорректный ID");
            return;
        }

        Optional<Movie> movie = store.getById(id);
        if (movie.isEmpty()) {
            sendError(ex, 404, "Фильм не найден");
            return;
        }
        sendJson(ex, 200, gson.toJson(movie.get()));
    }

    private void handleDelete(HttpExchange ex, String idPart) throws IOException {
        int id;
        try {
            id = Integer.parseInt(idPart);
        } catch (NumberFormatException e) {
            sendError(ex, 400, "Некорректный ID");
            return;
        }

        if (!store.delete(id)) {
            sendError(ex, 404, "Фильм не найден");
            return;
        }
        sendNoContent(ex);
    }

    private String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
