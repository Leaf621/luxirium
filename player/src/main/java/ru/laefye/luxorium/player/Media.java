package ru.laefye.luxorium.player;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.global.avformat;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.PointerPointer;
import ru.laefye.luxorium.player.exceptions.MediaLoadException;
import ru.laefye.luxorium.player.interfaces.MediaDescription;
import ru.laefye.luxorium.player.types.AudioFrame;
import ru.laefye.luxorium.player.types.VideoFrame;

import java.util.Optional;
import java.util.function.Consumer;

public class Media implements MediaDescription, AutoCloseable {
    private final AVFormatContext context = avformat.avformat_alloc_context();

    public Media(String filename) {
        if (avformat.avformat_open_input(context, filename, null, null) < 0) {
            throw new MediaLoadException("Could not open media file: " + filename);
        }
        if (avformat.avformat_find_stream_info(context, new PointerPointer<AVDictionary>()) < 0) {
            throw new MediaLoadException("Could not find stream information for: " + filename);
        }
    }


    @Override
    public void close() throws Exception {
        avformat.avformat_close_input(context);
        avformat.avformat_free_context(context);
    }

    @Override
    public double getDuration() {
        return (double) context.duration() / avutil.AV_TIME_BASE;
    }

    public AVFormatContext getContext() {
        return context;
    }

    public Optional<Integer> getVideoStreamIndex() {
        var streamIndex = avformat.av_find_best_stream(
                context,
                avutil.AVMEDIA_TYPE_VIDEO,
                -1, -1,
                new PointerPointer<AVCodec>(),
                0
        );
        if (streamIndex < 0) {
            return Optional.empty();
        }
        return Optional.of(streamIndex);
    }

    public Optional<Integer> getAudioStreamIndex() {
        var streamIndex = avformat.av_find_best_stream(
                context,
                avutil.AVMEDIA_TYPE_AUDIO,
                -1, -1,
                new PointerPointer<AVCodec>(),
                0
        );
        if (streamIndex < 0) {
            return Optional.empty();
        }
        return Optional.of(streamIndex);
    }

    public VideoStreamPlayer createVideoStreamPlayer(Consumer<VideoFrame> onFrame) {
        var videoStreamIndex = getVideoStreamIndex();
        if (videoStreamIndex.isEmpty()) {
            throw new IllegalStateException("No video stream found in media file.");
        }
        return new VideoStreamPlayer(this, videoStreamIndex.get(), onFrame);
    }

    public AudioStreamPlayer createAudioStreamPlayer(Consumer<AudioFrame> onFrame) {
        var audioStreamIndex = getAudioStreamIndex();
        if (audioStreamIndex.isEmpty()) {
            throw new IllegalStateException("No audio stream found in media file.");
        }
        return new AudioStreamPlayer(this, audioStreamIndex.get(), onFrame);
    }

    public int getSampleRate() {
        var audioStreamIndex = getAudioStreamIndex();
        if (audioStreamIndex.isEmpty()) {
            throw new IllegalStateException("No audio stream found in media file.");
        }
        var stream = context.streams(audioStreamIndex.get());
        return stream.codecpar().sample_rate();
    }
}
