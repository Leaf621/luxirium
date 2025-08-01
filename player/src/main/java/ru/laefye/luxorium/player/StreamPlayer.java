package ru.laefye.luxorium.player;

import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.PointerPointer;
import ru.laefye.luxorium.player.exceptions.NativeException;
import ru.laefye.luxorium.player.interfaces.MediaHelper;

public abstract class StreamPlayer implements AutoCloseable {
    protected final Media media;
    protected final int streamIndex;
    protected final AVCodec codec;
    protected final AVCodecContext codecContext;

    protected StreamPlayer(Media media, int streamIndex) {
        this.media = media;
        this.streamIndex = streamIndex;
        this.codec = avcodec.avcodec_find_decoder(stream().codecpar().codec_id());
        this.codecContext = avcodec.avcodec_alloc_context3(codec);

        if (avcodec.avcodec_parameters_to_context(codecContext, stream().codecpar()) < 0) {
            throw new NativeException("Could not copy codec parameters to context for stream index: " + streamIndex);
        }

        if (avcodec.avcodec_open2(codecContext, codec, new PointerPointer<AVDictionary>()) < 0) {
            throw new NativeException("Could not open codec for stream index: " + streamIndex);
        }
    }

    public AVStream stream() {
        return media.getContext().streams(streamIndex);
    }

    // Возвращает временную отметку, такую чтоб использовать в av_seek_frame
    public long getTimestamp(double time) {
        return (long)(time / avutil.av_q2d(stream().time_base()));
    }

    abstract void processFrame(AVFrame frame);

    @Override
    public void close() throws Exception {
        avcodec.avcodec_free_context(codecContext);
    }

    public AVCodec getCodec() {
        return codec;
    }

    public abstract void start(MediaHelper mediaHelper);

    public abstract void stop();

    public abstract void flush();

    public abstract void waitForEnd();

    public AVCodecContext getCodecContext() {
        return codecContext;
    }
}
