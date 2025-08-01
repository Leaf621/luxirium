package ru.laefye.luxorium.mod.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.TextureFormat;

import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;

public class ScreenTexture extends AbstractTexture {
    
    private static final int TEXTURE_WIDTH = 16;
    private static final int TEXTURE_HEIGHT = 16;
    private static final int CHECKERBOARD_SIZE = 2; // Размер клетки шахматной доски

    public ScreenTexture() {
        createTexture();
    }

    private void createTexture() {
        System.out.println("Начинаем создание текстуры...");
        
        GpuDevice gpuDevice = RenderSystem.getDevice();
        
        // Создаем текстуру с правильными параметрами
        // Параметры: name, usage, format, width, height, depth, mipLevels
        // usage = 5 включает USAGE_COPY_DST (1) и USAGE_TEXTURE_BINDING (4)
        glTexture = gpuDevice.createTexture("checkerboard", 5, TextureFormat.RGBA8, 
                                          TEXTURE_WIDTH, TEXTURE_HEIGHT, 1, 1);
        glTextureView = gpuDevice.createTextureView(glTexture);
        setFilter(false, false);
        setClamp(false);
        
        System.out.println("Текстура создана, начинаем генерацию данных...");
        
        // Создаем шахматную сетку и записываем в текстуру
        IntBuffer checkerboardData = createCheckerboardPattern();
        
        // Проверяем, что буфер создан корректно
        if (checkerboardData != null && checkerboardData.hasRemaining()) {
            try {
                System.out.println("Записываем данные в текстуру...");
                
                // Параметры writeToTexture:
                // writeToTexture(texture, buffer, format, mipLevel, layer, x, y, width, height)
                gpuDevice.createCommandEncoder()
                    .writeToTexture(glTexture, checkerboardData, NativeImage.Format.RGBA, 
                              0, // mipLevel - уровень мипмапа (0 = базовый уровень)
                              0, // layer - слой текстуры (0 для обычной 2D текстуры)
                              0, // x - начальная позиция по X
                              0, // y - начальная позиция по Y  
                              TEXTURE_WIDTH,  // width - ширина записываемой области
                              TEXTURE_HEIGHT); // height - высота записываемой области
                
                System.out.println("Данные успешно записаны в текстуру!");
                
            } catch (Exception e) {
                System.err.println("Ошибка при записи в текстуру: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("Ошибка: буфер данных пуст или null!");
        }
    }
    
    /**
     * Создает паттерн шахматной доски в виде IntBuffer
     * @return IntBuffer с данными шахматной доски
     */
    private IntBuffer createCheckerboardPattern() {
        // Создаем буфер с нативным порядком байтов
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(TEXTURE_WIDTH * TEXTURE_HEIGHT * 4); // 4 байта на пиксель (RGBA)
        byteBuffer.order(ByteOrder.nativeOrder());
        IntBuffer buffer = byteBuffer.asIntBuffer();
        
        // Цвета для шахматной доски в формате ABGR (обратный порядок для little-endian)
        int whiteColor = 0xFFFFFFFF; // Белый (A=FF, B=FF, G=FF, R=FF)
        int blackColor = 0xFF000000; // Черный (A=FF, B=00, G=00, R=00)
        
        for (int y = 0; y < TEXTURE_HEIGHT; y++) {
            for (int x = 0; x < TEXTURE_WIDTH; x++) {
                // Определяем, какого цвета должна быть клетка
                int checkX = x / CHECKERBOARD_SIZE;
                int checkY = y / CHECKERBOARD_SIZE;
                
                // Если сумма координат четная - белая клетка, иначе - черная
                boolean isWhite = (checkX + checkY) % 2 == 0;
                
                buffer.put(isWhite ? whiteColor : blackColor);
            }
        }
        
        buffer.flip(); // Подготавливаем буфер для чтения
        
        // Проверяем размер буфера
        System.out.println("Создан буфер размером: " + buffer.remaining() + 
                         " элементов (" + (buffer.remaining() * 4) + " байт)");
        System.out.println("Ожидается: " + (TEXTURE_WIDTH * TEXTURE_HEIGHT) + " элементов");
        
        return buffer;
    }
    
}
