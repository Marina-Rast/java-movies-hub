package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class MoviesStore {
    private final Map<Integer, Movie> movies = new HashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);

    public List<Movie> getAll() {
        return new ArrayList<>(movies.values());
    }

    public Movie add(Movie movie) {
        int id = nextId.getAndIncrement();
        movie.setId(id);
        movies.put(id, movie);
        return movie;
    }

    public Optional<Movie> getById(int id) {
        return Optional.ofNullable(movies.get(id));
    }

    public boolean delete(int id) {
        return movies.remove(id) != null;
    }

    public List<Movie> getByYear(int year) {
        List<Movie> result = new ArrayList<>();
        for (Movie m : movies.values()) {
            if (m.getYear() == year) {
                result.add(m);
            }
        }
        return result;
    }

    public void clear() {
        movies.clear();
        nextId.set(1);
    }
}