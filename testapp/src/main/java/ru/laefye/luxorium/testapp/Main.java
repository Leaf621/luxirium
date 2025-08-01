package ru.laefye.luxorium.testapp;

import ru.laefye.luxorium.player.Media;
import ru.laefye.luxorium.player.MediaPlayer;
import ru.laefye.luxorium.player.VideoStreamPlayer;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        MainWindow mainWindow = new MainWindow(new Media(".test.mp4"));
        mainWindow.setVisible(true);
        mainWindow.start();
    }
}
