package ru.laefye.luxorium.testapp;

import ru.laefye.luxorium.player.Media;
import ru.laefye.luxorium.player.MediaPlayer;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.List;

public class MainWindow extends JFrame implements AutoCloseable {
    private final Media media;
    private final MediaPlayer mediaPlayer;
    private BufferedImage image = null;

    public MainWindow(Media media) {
        setTitle("Luxorium Test App");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        this.media = media;
        this.mediaPlayer = new MediaPlayer(media, List.of(
                media.createVideoStreamPlayer(videoFrame -> {
                    if (image == null || image.getWidth() != videoFrame.lineSize / 3 || image.getHeight() != videoFrame.height) {
                        image = new BufferedImage(videoFrame.lineSize / 3, videoFrame.height, BufferedImage.TYPE_INT_RGB);
                        setSize(videoFrame.lineSize / 3, videoFrame.height);
                    }
                    int[] rgbArray = new int[videoFrame.lineSize * videoFrame.height / 3];
                    for (int i = 0; i < rgbArray.length; i++) {
                        int r = videoFrame.data[i * 3] & 0xFF;
                        int g = videoFrame.data[i * 3 + 1] & 0xFF;
                        int b = videoFrame.data[i * 3 + 2] & 0xFF;
                        rgbArray[i] = (r << 16) | (g << 8) | b;
                    }
                    System.out.println(rgbArray.length);
                    image.setRGB(0, 0, videoFrame.lineSize / 3, videoFrame.height, rgbArray, 0, videoFrame.lineSize / 3);
                    repaint();
                })
        ));
    }

    @Override
    public void close() throws Exception {
        media.close();
    }

    public void start() {
        mediaPlayer.play();
    }

    @Override
    public void paint(Graphics g) {
        if (image != null) {
            g.drawImage(image, 0, 0, null);
        }
    }
}
