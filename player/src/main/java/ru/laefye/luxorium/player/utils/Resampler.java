package ru.laefye.luxorium.player.utils;

import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avutil.AVChannelLayout;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.ffmpeg.global.swresample;
import org.bytedeco.ffmpeg.swresample.SwrContext;
import ru.laefye.luxorium.player.exceptions.NativeException;

public class Resampler implements AutoCloseable {
    private final int targetSampleFormat = avutil.AV_SAMPLE_FMT_S16P;
    private final int targetCountChannels = 1;
    private final AVChannelLayout targetChannelLayout = new AVChannelLayout();
    private final AVCodecContext codecContext;
    private final AVFrame resampledFrame;
    private final SwrContext swrContext;

    public int getTargetSampleFormat() {
        return targetSampleFormat;
    }

    public Resampler(AVCodecContext codecContext) {
        this.codecContext = codecContext;
        var inputSampleFormat = codecContext.sample_fmt();
        var inputChannelLayout = codecContext.ch_layout();
        var inputSampleRate = codecContext.sample_rate();

        avutil.av_channel_layout_default(targetChannelLayout, targetCountChannels);

        swrContext = swresample.swr_alloc();

        if (swresample.swr_alloc_set_opts2(swrContext,
                targetChannelLayout, targetSampleFormat, codecContext.sample_rate(),
                inputChannelLayout, inputSampleFormat, inputSampleRate,
                0, null) < 0) {
            throw new NativeException("Could not allocate SwrContext for resampling");
        }

        if (swresample.swr_init(swrContext) < 0) {
            throw new NativeException("Could not initialize SwrContext for resampling");
        }

        resampledFrame = avutil.av_frame_alloc();
        if (resampledFrame == null) {
            throw new NativeException("Could not allocate AVFrame for resampled output");
        }
    }

    public AVFrame resample(AVFrame inputFrame) {
        var outSamples = swresample.swr_get_out_samples(
                swrContext,
                inputFrame.nb_samples()
        );

        if (outSamples <= 0) {
            throw new NativeException("Could not get output samples count for resampling");
        }

        // Переиспользуем фрейм, только если нужно изменить размер
        if (resampledFrame.nb_samples() != outSamples || 
            resampledFrame.format() != targetSampleFormat ||
            avutil.av_channel_layout_compare(resampledFrame.ch_layout(), targetChannelLayout) != 0) {
            
            avutil.av_frame_unref(resampledFrame);
            
            resampledFrame.nb_samples(outSamples);
            resampledFrame.format(targetSampleFormat);
            resampledFrame.ch_layout(targetChannelLayout);
            resampledFrame.sample_rate(codecContext.sample_rate());

            if (avutil.av_frame_get_buffer(resampledFrame, 0) < 0) {
                throw new NativeException("Could not allocate buffer for resampled AVFrame");
            }
        }

        var convertedSamples = swresample.swr_convert(
                swrContext,
                resampledFrame.data(),
                resampledFrame.nb_samples(),
                inputFrame.data(),
                inputFrame.nb_samples()
        );

        if (convertedSamples <= 0) {
            throw new NativeException("Could not convert samples for resampling");
        }

        // Обновляем количество сэмплов на фактически конвертированное
        resampledFrame.nb_samples(convertedSamples);
        return resampledFrame;
    }

    @Override
    public void close() throws Exception {
        avutil.av_frame_free(resampledFrame);
        swresample.swr_free(swrContext);
    }
}
