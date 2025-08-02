package ru.laefye.luxorium.player.types;

import java.nio.ByteBuffer;

public class VideoFrame {
    public int lineSize;
    public int height;
    public int width;
    public byte[] data;
    public double duration;
    public double timestamp;

    public VideoFrame() {
        this.lineSize = 0;
        this.height = 0;
        this.data = new byte[0];
        this.duration = 0.0;
        this.timestamp = 0.0;
    }

    public VideoFrame(int lineSize, int height, int width, byte[] data, double duration, double timestamp) {
        this.lineSize = lineSize;
        this.height = height;
        this.width = width;
        this.data = data;
        this.duration = duration;
        this.timestamp = timestamp;
    }
}
