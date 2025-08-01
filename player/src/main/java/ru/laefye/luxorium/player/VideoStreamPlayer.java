package ru.laefye.luxorium.player;

import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avutil;
import ru.laefye.luxorium.player.interfaces.MediaHelper;
import ru.laefye.luxorium.player.types.CycledQueue;
import ru.laefye.luxorium.player.types.Maybe;
import ru.laefye.luxorium.player.types.VideoFrame;
import ru.laefye.luxorium.player.utils.Rescaler;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class VideoStreamPlayer extends StreamPlayer {
    private Thread playThread = null;
    private final Rescaler rescaler;
    private final CycledQueue<Maybe<VideoFrame>> queue;
    private final AtomicBoolean gettingFrames = new AtomicBoolean(false);
    private final Consumer<VideoFrame> onFrame;

    public VideoStreamPlayer(Media media, int streamIndex, Consumer<VideoFrame> onFrame) {
        super(media, streamIndex);
        rescaler = new Rescaler(
            getCodecContext(),
                avutil.AV_PIX_FMT_RGB24,
                getCodecContext().width(),
                getCodecContext().height()
        );
        queue = new CycledQueue<>(60 * 3, () -> Maybe.from(new VideoFrame()));
        this.onFrame = onFrame;
    }

    @Override
    void processFrame(AVFrame frame) {
        gettingFrames.set(true);
        if (frame.height() == 0 || frame.linesize(0) == 0) {
            return;
        }
        var duration = frame.duration() * avutil.av_q2d(stream().time_base());
        var timestamp = frame.pts() * avutil.av_q2d(stream().time_base());
        var rescaledFrame = rescaler.rescale(frame);
        try {
            queue.offer(videoFrameMaybe -> {
                var frameSize = rescaledFrame.linesize(0) * rescaledFrame.height();
                videoFrameMaybe.isPresent = true;
                videoFrameMaybe.value.duration = duration;
                videoFrameMaybe.value.lineSize = rescaledFrame.linesize(0);
                videoFrameMaybe.value.height = rescaledFrame.height();
                videoFrameMaybe.value.width = rescaledFrame.width();
                videoFrameMaybe.value.timestamp = timestamp;
                if (videoFrameMaybe.value.data.length < frameSize) {
                    videoFrameMaybe.value.data = new byte[frameSize];
                }
                rescaledFrame.data(0).get(videoFrameMaybe.value.data, 0, frameSize);
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
                if (queue.getCount() < 3 && gettingFrames.get()) {
                    gettingFrames.set(true);
                    mediaHelper.getNextFrames();
                }
                var videoFrame = frame.value;
                onFrame.accept(videoFrame);
                var delay = (long) (videoFrame.duration * 1000) - (System.currentTimeMillis() - begin);
                if (delay > 0) {
                    Thread.sleep(delay);
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void start(MediaHelper mediaHelper) {
        playThread = new Thread(() -> play(mediaHelper), "VideoStreamPlayer-" + streamIndex);
        playThread.start();
    }

    @Override
    public void stop() {
        try {
            flush();
            queue.offer(videoFrameMaybe -> {
                videoFrameMaybe.isPresent = false;
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
        try {
            if (playThread != null) {
                queue.offer(videoFrameMaybe -> {
                    videoFrameMaybe.isPresent = false;
                });
                playThread.join();
                playThread = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        rescaler.close();
        super.close();
    }
}
