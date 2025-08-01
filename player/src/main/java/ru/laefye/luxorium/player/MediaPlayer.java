package ru.laefye.luxorium.player;

import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.ffmpeg.global.avformat;
import ru.laefye.luxorium.player.exceptions.NativeException;
import ru.laefye.luxorium.player.interfaces.MediaHelper;

import java.util.List;
import java.util.concurrent.CountDownLatch;


public class MediaPlayer implements AutoCloseable {
    private final Media media;
    private final AVPacket packet;
    private final AVFrame frame;
    private final List<StreamPlayer> streamPlayers;
    private CountDownLatch readLatch = new CountDownLatch(0);
    private boolean stopped = true;
    private Thread readThread = null;

    public MediaPlayer(Media media, List<StreamPlayer> streamPlayers) {
        this.media = media;
        this.packet = avcodec.av_packet_alloc();
        this.frame = avutil.av_frame_alloc();
        this.streamPlayers = streamPlayers;
    }

    private boolean getNextFrame() {
        while (true) {
            for (var streamPlayer : streamPlayers) {
                if (avcodec.avcodec_receive_frame(streamPlayer.getCodecContext(), frame) >= 0) {
                    streamPlayer.processFrame(frame);
                    return true;
                }
            }

            if (avformat.av_read_frame(media.getContext(), packet) < 0) {
                return false;
            }

            var optionalStreamPlayer = streamPlayers.stream()
                    .filter(streamPlayer -> streamPlayer.streamIndex == packet.stream_index())
                    .findFirst();

            optionalStreamPlayer.ifPresent(streamPlayer -> {
                avcodec.avcodec_send_packet(streamPlayer.getCodecContext(), packet);
            });

            avcodec.av_packet_unref(packet);
        }
    }

    private void runRead() {
        readLatch.countDown();
        boolean eof = false;

        while (!eof && !stopped) {
            try {
                readLatch.await();
                for (int i = 0; i < 64; i++) {
                    if (!getNextFrame()) {
                        eof = true;
                        break;
                    }
                }
                readLatch = new CountDownLatch(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void play() {
        if (!stopped) {
            return;
        }
        try {
            for (var streamPlayer : streamPlayers) {
                streamPlayer.start(new MediaHelper() {
                    @Override
                    public void getNextFrames() {
                        readLatch.countDown();
                    }
                });
            }
            stopped = false;
            readThread = new Thread(this::runRead, "MediaPlayer-ReadThread");
            readThread.start();
            readThread.join();
            stopped = true;
            streamPlayers.forEach(StreamPlayer::waitForEnd);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        stopped = true;
        readLatch.countDown();
        try {
            readThread.join();
            streamPlayers.forEach(StreamPlayer::stop);
            streamPlayers.forEach(StreamPlayer::waitForEnd);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        stop();
        for (var streamPlayer : streamPlayers) {
            streamPlayer.close();
        }
        avcodec.av_packet_free(packet);
        avutil.av_frame_free(frame);
    }

    public void seek(double seconds) {
        var streamIndex = streamPlayers.getFirst().streamIndex;
        var seekTime = streamPlayers.getFirst().getTimestamp(seconds);
        if (avformat.av_seek_frame(media.getContext(), streamIndex, seekTime, avformat.AVSEEK_FLAG_ANY) < 0) {
            throw new NativeException("Could not seek to " + seconds + " seconds in media file.");
        }
        if (!stopped) {
            streamPlayers.forEach(StreamPlayer::flush);
            if (readThread == null) {
                readThread = new Thread(this::runRead, "MediaPlayer-ReadThread");
            }
            readLatch.countDown();
        }
    }
}
