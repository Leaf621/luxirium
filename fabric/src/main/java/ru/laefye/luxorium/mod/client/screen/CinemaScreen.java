package ru.laefye.luxorium.mod.client.screen;

import net.minecraft.util.Identifier;

public class CinemaScreen implements AutoCloseable {
    private final Identifier screenId;
    private final ScreenTextureHolder textureHolder;

    public CinemaScreen(Identifier screenId) {
        this.screenId = screenId;
        this.textureHolder = new ScreenTextureHolder(screenId);
    }

    public ScreenTextureHolder getTextureHolder() {
        return textureHolder;
    }

    @Override
    public void close() throws Exception {
        textureHolder.close();
    }
}
