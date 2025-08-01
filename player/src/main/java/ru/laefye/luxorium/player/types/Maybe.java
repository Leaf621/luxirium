package ru.laefye.luxorium.player.types;

public class Maybe<T> {
    public boolean isPresent;
    public T value;

    public static <T> Maybe<T> from(T value) {
        Maybe<T> maybe = new Maybe<>();
        maybe.isPresent = true;
        maybe.value = value;
        return maybe;
    }
}
