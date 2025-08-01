package ru.laefye.luxorium.mod.client;

import java.io.IOException;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.ReloadableTexture;
import net.minecraft.client.texture.TextureContents;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

public class LuxoriumClient implements ClientModInitializer {
    // Простой экранный слой без сложных настроек
    private static final RenderLayer SCREEN_LAYER = RenderLayer.getEntityCutout(
        Identifier.of("minecraft", "textures/block/white_concrete.png")
    );

    private static LuxoriumClient INSTANCE;

    public static LuxoriumClient getInstance() {
        return INSTANCE;
    }

    private Identifier screenTexture = null;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
    }

    public Identifier getScreenTexture() {
        if (screenTexture == null) {
            MinecraftClient.getInstance().getTextureManager().registerTexture(id("uwu"), new ScreenTexture());
        }
        screenTexture = Identifier.of("luxorium", "uwu");
        return screenTexture;
    }

    public static Identifier id(String path) {
        return Identifier.of("luxorium", path);
    }

    public RenderLayer getScreenLayer() {
        return SCREEN_LAYER;
    }
    
    // Метод для создания экранного слоя с кастомной текстурой
    public static RenderLayer createScreenLayer(Identifier texture) {
        // Используем простой слой без зависимости от освещения
        return RenderLayer.getEntityCutout(texture);
    }
}
