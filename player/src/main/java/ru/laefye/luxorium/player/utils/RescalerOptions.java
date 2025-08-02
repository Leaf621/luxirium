package ru.laefye.luxorium.player.utils;

import java.util.Optional;

public class RescalerOptions {
    private Optional<Integer> width = Optional.empty();
    private Optional<Integer> height = Optional.empty();

    public static RescalerOptions create() {
        return new RescalerOptions();
    }

    public RescalerOptions withWidth(int width) {
        this.width = Optional.of(width);
        return this;
    }

    public RescalerOptions withHeight(int height) {
        this.height = Optional.of(height);
        return this;
    }

    public Optional<Integer> getWidth() {
        return width;
    }

    public Optional<Integer> getHeight() {
        return height;
    }
}
