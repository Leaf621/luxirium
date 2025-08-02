package ru.laefye.luxorium.player.types;

public class AudioFrame {
    public int numberSamples;
    public int sampleSize;
    public byte[] data;
    public double timestamp;
    public int sampleRate;
    public double duration;

    public AudioFrame() {
        this.numberSamples = 0;
        this.sampleSize = 0;
        this.data = new byte[0];
        this.timestamp = 0.0;
        this.duration = 0.0;
        this.sampleRate = 0;
    }
}
