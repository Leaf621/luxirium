package ru.laefye.luxorium.player;

import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avutil;

import ru.laefye.luxorium.player.interfaces.MediaHelper;
import ru.laefye.luxorium.player.types.FastCycledQueue;
import ru.laefye.luxorium.player.types.Maybe;
import ru.laefye.luxorium.player.types.VideoFrame;
import ru.laefye.luxorium.player.utils.Rescaler;
import ru.laefye.luxorium.player.utils.RescalerOptions;

import java.util.function.Consumer;

public class VideoStreamPlayer extends StreamPlayer {
    private Thread playThread = null;
    private final Rescaler rescaler;
    private final FastCycledQueue<Maybe<VideoFrame>> queue;
    private final Consumer<VideoFrame> onFrame;

    public VideoStreamPlayer(Media media, int streamIndex, Consumer<VideoFrame> onFrame, RescalerOptions options) {
        super(media, streamIndex);
        rescaler = new Rescaler(
                getCodecContext(),
                avutil.AV_PIX_FMT_RGB24,
                options.getWidth().orElse(getCodecContext().width()),
                options.getHeight().orElse(getCodecContext().height())
        );
        // Увеличиваем буфер и используем быструю очередь
        queue = new FastCycledQueue<>(128, () -> Maybe.from(new VideoFrame()));
        this.onFrame = onFrame;
    }

    @Override
    void processFrame(AVFrame frame) {
        if (frame.height() == 0 || frame.linesize(0) == 0) {
            return;
        }

        // Быстрое масштабирование и конвертация
        var duration = frame.duration() * avutil.av_q2d(stream().time_base());
        var timestamp = frame.pts() * avutil.av_q2d(stream().time_base());
        var rescaledFrame = rescaler.rescale(frame);
        
        var frameSize = rescaledFrame.linesize(0) * rescaledFrame.height();
        
        // Неблокирующая запись в очередь
        queue.offer(videoFrameMaybe -> {
            videoFrameMaybe.isPresent = true;
            var videoFrame = videoFrameMaybe.value;
            videoFrame.duration = duration;
            videoFrame.lineSize = rescaledFrame.linesize(0);
            videoFrame.height = rescaledFrame.height();
            videoFrame.width = rescaledFrame.width();
            videoFrame.timestamp = timestamp;
            
            // Избегаем лишних аллокаций
            if (videoFrame.data.length < frameSize) {
                videoFrame.data = new byte[Math.max(frameSize, videoFrame.data.length * 2)];
            }
            rescaledFrame.data(0).get(videoFrame.data, 0, frameSize);
        });
    }

    private void play(MediaHelper mediaHelper) {
        try {
            flush();
            while (true) {
                var begin = System.currentTimeMillis();
                var frame = queue.take();
                if (!frame.isPresent) break;
                
                // Проверяем уровень очереди и запрашиваем новые кадры
                if (queue.getCount() < 5) {
                    mediaHelper.getNextFrames();
                }
                
                var videoFrame = frame.value;
                onFrame.accept(videoFrame);
                
                // Рассчитываем задержку для поддержания FPS
                var delay = (long) (videoFrame.duration * 1000) - (System.currentTimeMillis() - begin);
                if (delay > 0) {
                    Thread.sleep(delay);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void start(MediaHelper mediaHelper) {
        playThread = new Thread(() -> play(mediaHelper), "VideoStreamPlayer-" + streamIndex);
        playThread.start();
    }

    @Override
    public void stop() {
        flush();
        queue.offer(videoFrameMaybe -> {
            videoFrameMaybe.isPresent = false;
        });
    }

    @Override
    public void flush() {
        queue.clear();
    }

    @Override
    public void waitForEnd() {
        if (playThread != null) {
            queue.offer(videoFrameMaybe -> {
                videoFrameMaybe.isPresent = false;
            });
            try {
                playThread.join();
                playThread = null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() throws Exception {
        rescaler.close();
        super.close();
    }
}
