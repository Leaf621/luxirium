package ru.laefye.luxorium.player;

import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avutil;
import ru.laefye.luxorium.player.interfaces.MediaHelper;
import ru.laefye.luxorium.player.types.AudioFrame;
import ru.laefye.luxorium.player.types.FastCycledQueue;
import ru.laefye.luxorium.player.types.Maybe;
import ru.laefye.luxorium.player.utils.Resampler;

import java.util.function.Consumer;

public class AudioStreamPlayer extends StreamPlayer {
    private final Consumer<AudioFrame> onFrame;
    private final Resampler resampler;
    private final FastCycledQueue<Maybe<AudioFrame>> queue;
    private Thread playThread = null;

    public AudioStreamPlayer(Media media, int streamIndex, Consumer<AudioFrame> onFrame) {
        super(media, streamIndex);
        this.onFrame = onFrame;
        this.resampler = new Resampler(getCodecContext());
        // Увеличиваем размер буфера для OpenAL - больше данных в буфере = меньше underrun'ов
        this.queue = new FastCycledQueue<>(256, () -> Maybe.from(new AudioFrame()));
    }

    @Override
    void processFrame(AVFrame frame) {
        var resampledFrame = resampler.resample(frame);
        var numberSamples = resampledFrame.nb_samples();
        var sampleSize = avutil.av_get_bytes_per_sample(resampler.getTargetSampleFormat());
        var timestamp = frame.pts() * avutil.av_q2d(stream().time_base());
        var frameRate = resampledFrame.sample_rate();
        var duration = frame.duration() * avutil.av_q2d(stream().time_base());
        var dataSize = numberSamples * sampleSize;

        // Быстрая неблокирующая запись в очередь
        boolean success = queue.offer(audioFrameMaybe -> {
            audioFrameMaybe.isPresent = true;
            var audioFrame = audioFrameMaybe.value;
            audioFrame.numberSamples = numberSamples;
            audioFrame.sampleSize = sampleSize;
            audioFrame.timestamp = timestamp;
            audioFrame.sampleRate = frameRate;
            audioFrame.duration = duration;
            
            // Избегаем лишних аллокаций
            if (audioFrame.data.length < dataSize) {
                audioFrame.data = new byte[Math.max(dataSize, audioFrame.data.length * 2)];
            }
            resampledFrame.data(0).get(audioFrame.data, 0, dataSize);
        });

        // Если очередь заполнена, пропускаем кадр (лучше чем блокировка)
        if (!success) {
            System.err.println("Audio queue overflow - dropping frame");
        }
    }

    private void play(MediaHelper mediaHelper) {
        try {
            flush();
            while (true) {
                var frame = queue.take();
                if (!frame.isPresent) break;
                
                // Проверяем уровень очереди и запрашиваем новые кадры
                if (queue.getCount() < 10) {
                    mediaHelper.getNextFrames();
                }
                
                var audioFrame = frame.value;
                onFrame.accept(audioFrame);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void start(MediaHelper mediaHelper) {
        playThread = new Thread(() -> {
            try {
                play(mediaHelper);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        playThread.start();
    }

    @Override
    public void stop() {
        flush();
        queue.offer(audioFrameMaybe -> {
            audioFrameMaybe.isPresent = false;
        });
    }

    @Override
    public void flush() {
        queue.clear();
    }

    @Override
    public void waitForEnd() {
        if (playThread != null) {
            queue.offer(audioFrameMaybe -> {
                audioFrameMaybe.isPresent = false;
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
        resampler.close();
        super.close();
    }
}
