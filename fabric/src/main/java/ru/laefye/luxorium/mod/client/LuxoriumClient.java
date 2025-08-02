package ru.laefye.luxorium.mod.client;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import ru.laefye.luxorium.mod.client.screen.CinemaScreen;
import ru.laefye.luxorium.mod.client.sound.SoundEmitter;
import ru.laefye.luxorium.player.Media;
import ru.laefye.luxorium.player.MediaPlayer;
import ru.laefye.luxorium.player.utils.RescalerOptions;

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
                         media.createAudioStreamPlayer(t -> {
                             while (source.isQueueFull()) {
                                    try {
                                        System.out.println("SoundEmitter: Buffer full, waiting...");
                                        Thread.sleep((long)(t.duration * 1000));
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                             }
                             source.write(t.data, t.sampleSize * t.numberSamples, t.sampleRate);
                         })
                        ,
                        media.createVideoStreamPlayer(videoFrame -> {
                            getCinemaScreen().getTextureHolder().setFrame(videoFrame);
                        }, RescalerOptions.create().withWidth(1280).withHeight(720))
                ));
                mediaPlayer.setHighPerformanceMode(true);
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
