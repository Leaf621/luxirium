package ru.laefye.luxorium.mod.client.screen;

import java.util.Optional;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import ru.laefye.luxorium.player.types.VideoFrame;

public class ScreenTextureHolder implements AutoCloseable {
    private final Identifier textureId;

    private ScreenTexture screenTexture;

    public ScreenTextureHolder(Identifier textureId) {
        this.textureId = textureId;
    }

    private TextureManager getTextureManager() {
        return MinecraftClient.getInstance().getTextureManager();
    }

    private void createTexture(int width, int height) {
        if (screenTexture != null) {
            getTextureManager().destroyTexture(textureId);
        }
        screenTexture = new ScreenTexture(width, height);
        getTextureManager().registerTexture(textureId, screenTexture);
        System.out.println("Создана текстура для экрана: " + textureId + " с размерами " + width + "x" + height);
    }

    private void createTexture(int width, int height, Runnable next) {
        MinecraftClient.getInstance().execute(() -> {
            createTexture(width, height);
            next.run();
        });
    }

    public void setFrame(VideoFrame frame) {
        if (screenTexture == null || screenTexture.getTextureWidth() != frame.width || screenTexture.getTextureHeight() != frame.height) {
            createTexture(frame.width, frame.height, () -> screenTexture.setFrame(frame, true));
        } else {
            screenTexture.setFrame(frame, false);
        }
    }

    public Optional<Identifier> getTextureId() {
        if (screenTexture != null) {
            return Optional.of(textureId);
        }
        return Optional.empty();
    }
    
    public Optional<RenderLayer> getRenderLayer() {
        return getTextureId().map(id -> RenderLayer.getEntityCutout(id));
    }

    @Override
    public void close() throws Exception {
        if (screenTexture != null) {
            getTextureManager().destroyTexture(textureId);
        }
    }
}
