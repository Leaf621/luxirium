package ru.laefye.luxorium.player;

import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avutil;
import ru.laefye.luxorium.player.interfaces.MediaHelper;
import ru.laefye.luxorium.player.types.AudioFrame;
import ru.laefye.luxorium.player.types.CycledQueue;
import ru.laefye.luxorium.player.types.Maybe;
import ru.laefye.luxorium.player.utils.Resampler;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class AudioStreamPlayer extends StreamPlayer {
    private final Consumer<AudioFrame> onFrame;
    private final Resampler resampler;
    private final CycledQueue<Maybe<AudioFrame>> queue;
    private final AtomicBoolean gettingFrames = new AtomicBoolean(false);
    private Thread playThread = null;

    public AudioStreamPlayer(Media media, int streamIndex, Consumer<AudioFrame> onFrame) {
        super(media, streamIndex);
        this.onFrame = onFrame;
        this.resampler = new Resampler(getCodecContext());
        this.queue = new CycledQueue<>(60 * 3, () -> Maybe.from(new AudioFrame()));
    }

    @Override
    void processFrame(AVFrame frame) {
        gettingFrames.set(true);
        var resampledFrame = resampler.resample(frame);

        try {
            var numberSamples = resampledFrame.nb_samples();
            var sampleSize = avutil.av_get_bytes_per_sample(resampler.getTargetSampleFormat());
            var timestamp = frame.pts() * avutil.av_q2d(stream().time_base());
            var frameRate = resampledFrame.sample_rate();
            var duration = frame.duration() * avutil.av_q2d(stream().time_base());

            queue.offer(audioFrameMaybe -> {
                audioFrameMaybe.isPresent = true;
                audioFrameMaybe.value.numberSamples = numberSamples;
                audioFrameMaybe.value.sampleSize = sampleSize;
                audioFrameMaybe.value.timestamp = timestamp;
                audioFrameMaybe.value.sampleRate = frameRate;
                audioFrameMaybe.value.duration = duration;
                if (audioFrameMaybe.value.data.length < numberSamples * sampleSize) {
                    audioFrameMaybe.value.data = new byte[numberSamples * sampleSize];
                }
                resampledFrame.data(0).get(audioFrameMaybe.value.data, 0, numberSamples * sampleSize);
            });
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void play(MediaHelper mediaHelper) {
        try {
            flush();
            while (true) {
                var begin = System.currentTimeMillis();
                var frame = queue.take();
                if (!frame.isPresent) break;
                if (queue.getCount() < 5 && gettingFrames.get()) {
                    gettingFrames.set(true);
                    mediaHelper.getNextFrames();
                }
                var audioFrame = frame.value;
                onFrame.accept(audioFrame);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
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
        try {
            queue.offer(audioFrameMaybe -> {
                audioFrameMaybe.isPresent = false;
            });
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void flush() {
        queue.clear();
        gettingFrames.set(false);
    }

    @Override
    public void waitForEnd() {
        if (playThread != null) {
            try {
                queue.offer(audioFrameMaybe -> {
                    audioFrameMaybe.isPresent = false;
                });
                playThread.join();
                playThread = null;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void close() throws Exception {
        resampler.close();
        super.close();
    }
}
