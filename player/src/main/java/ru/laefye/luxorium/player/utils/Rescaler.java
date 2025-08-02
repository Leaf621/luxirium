package ru.laefye.luxorium.player.utils;

import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.ffmpeg.global.swscale;
import org.bytedeco.ffmpeg.swscale.SwsContext;
import org.bytedeco.ffmpeg.swscale.SwsFilter;
import ru.laefye.luxorium.player.exceptions.NativeException;

public class Rescaler implements AutoCloseable {
    private final AVCodecContext context;
    private final int targetPixelFormat;
    private final int targetWidth;
    private final int targetHeight;
    private final SwsContext swsContext;
    private final AVFrame rescaledFrame;


    public Rescaler(AVCodecContext context, int targetPixelFormat, int targetWidth, int targetHeight) {
        this.context = context;
        this.targetPixelFormat = targetPixelFormat;
        this.targetWidth = targetWidth;
        this.targetHeight = targetHeight;

        this.swsContext = swscale.sws_getContext(
                context.width(), context.height(), context.pix_fmt(),
                targetWidth, targetHeight, targetPixelFormat,
                swscale.SWS_BILINEAR, new SwsFilter(), new SwsFilter(), new double[] {}
        );

        if (swsContext == null) {
            throw new NativeException("Could not create SwsContext for rescaling");
        }

        this.rescaledFrame = avutil.av_frame_alloc();
        if (rescaledFrame == null) {
            throw new NativeException("Could not allocate AVFrame for rescaled output");
        }

        rescaledFrame.format(targetPixelFormat);
        rescaledFrame.width(targetWidth);
        rescaledFrame.height(targetHeight);
        if (avutil.av_frame_get_buffer(rescaledFrame, 0) < 0) {
            throw new NativeException("Could not allocate buffer for rescaled AVFrame");
        }
    }

    public AVFrame rescale(AVFrame inputFrame) {
        // Быстрая проверка без вывода в консоль для производительности
        if (inputFrame.width() != context.width() || inputFrame.height() != context.height()) {
            throw new NativeException("Input frame dimensions do not match codec context dimensions");
        }

        // Переиспользуем rescaledFrame без реаллокации, если размеры совпадают
        if (rescaledFrame.width() != targetWidth || rescaledFrame.height() != targetHeight) {
            avutil.av_frame_unref(rescaledFrame);
            rescaledFrame.format(targetPixelFormat);
            rescaledFrame.width(targetWidth);
            rescaledFrame.height(targetHeight);
            if (avutil.av_frame_get_buffer(rescaledFrame, 0) < 0) {
                throw new NativeException("Could not allocate buffer for rescaled AVFrame");
            }
        }

        // Масштабирование
        swscale.sws_scale(swsContext, inputFrame.data(), inputFrame.linesize(),
                0, inputFrame.height(), rescaledFrame.data(), rescaledFrame.linesize());

        return rescaledFrame;
    }

    @Override
    public void close() throws Exception {
        if (swsContext != null) {
            swscale.sws_freeContext(swsContext);
        }
        if (rescaledFrame != null) {
            avutil.av_frame_free(rescaledFrame);
        }
    }
}
