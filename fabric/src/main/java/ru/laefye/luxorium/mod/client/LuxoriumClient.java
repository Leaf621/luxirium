package ru.laefye.luxorium.mod.client;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import javax.sound.sampled.AudioFormat;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.Source;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.ReloadableTexture;
import net.minecraft.client.texture.TextureContents;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.command.CommandSource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import ru.laefye.luxorium.mod.client.screen.CinemaScreen;
import ru.laefye.luxorium.mod.client.screen.ScreenTexture;
import ru.laefye.luxorium.mod.client.sound.SoundEmitter;
import ru.laefye.luxorium.player.Media;
import ru.laefye.luxorium.player.MediaPlayer;

public class LuxoriumClient implements ClientModInitializer {
    private static LuxoriumClient INSTANCE;

    public static LuxoriumClient getInstance() {
        return INSTANCE;
    }

    private int play(CommandContext<FabricClientCommandSource> context) {
        SoundEmitter source = new SoundEmitter();
        source.setVolume(1f); // 50% громкости
        new Thread(() -> {
            Logger.getLogger("LuxoriumClient").info("Media playback starting");
            try (Media media = new Media("../../.test.webm")) {
                // Получаем sample rate один раз и сохраняем
                final int sampleRate = media.getSampleRate();
                Logger.getLogger("LuxoriumClient").info("Sample rate: " + sampleRate);
                
                MediaPlayer mediaPlayer = new MediaPlayer(media, List.of(
                        media.createVideoStreamPlayer(videoFrame -> {
                            MinecraftClient.getInstance().execute(() -> {
                                getCinemaScreen().getTextureHolder().setFrame(videoFrame);
                            });
                        })
                        // media.createAudioStreamPlayer(t -> {
                        //     while (source.isQueueFull()) {
                        //         try {
                        //             System.out.println("Ожидание освобождения буферов...");
                        //             Thread.sleep((long) (t.duration * 1000)); // Ждем 8 мс для освобождения буферов
                        //         } catch (InterruptedException e) {
                        //             e.printStackTrace();
                        //         } 
                        //     }
                        //     source.write(t.data, t.numberSamples * t.sampleSize, media.getSampleRate());
                        // })
                ));
                mediaPlayer.play();
                mediaPlayer.close();
                source.play(); 

                Logger.getLogger("LuxoriumClient").info("Media playback finished.");
            } catch (Exception e) {
                Logger.getLogger("LuxoriumClient").severe("Критическая ошибка в медиаплеере: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Очищаем ресурсы SoundManager
            }
        }, "LuxoriumVideoPlayer").start();
        return Command.SINGLE_SUCCESS;
    }

    @Override
    public void onInitializeClient() {
        INSTANCE = this;

        ClientCommandRegistrationCallback.EVENT.register((commandDispatcher, commandRegistryAccess) -> {
            commandDispatcher.register(
                    ClientCommandManager.literal("play_video")
                            .executes(this::play)
            );
        });
    }

    private CinemaScreen screen;

    public CinemaScreen getCinemaScreen() {
        if (screen == null) {
            screen = new CinemaScreen(id("uwu"));
        }
        return screen;
    }

    public static Identifier id(String path) {
        return Identifier.of("luxorium", path);
    }

    public static RenderLayer createScreenLayer(Identifier texture) {
        return RenderLayer.getEntityCutout(texture);
    }
}
