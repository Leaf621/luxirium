package ru.laefye.luxorium.mod.client;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.TextureFormat;

import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import ru.laefye.luxorium.player.types.VideoFrame;

public class ScreenTexture extends AbstractTexture {
    
    private static final int TEXTURE_WIDTH = 576;
    private static final int TEXTURE_HEIGHT = 576;
    private static final int CHECKERBOARD_SIZE = 2; // Размер клетки шахматной доски

    public ScreenTexture() {
        createTexture();
    }

    private byte[] bytes;

    public synchronized void setFrame(VideoFrame frame) {
        // Проверяем валидность входных данных
        if (frame == null || frame.data == null || frame.width <= 0 || frame.height <= 0) {
            System.err.println("Получен невалидный VideoFrame");
            return;
        }
        
        // Проверяем, что текстура создана
        if (glTexture == null) {
            System.err.println("Текстура не создана, не можем обновить кадр");
            return;
        }
        
        System.out.println("Устанавливаем кадр в текстуру, размер: " + frame.width + "x" + frame.height + 
                          ", lineSize: " + frame.lineSize + ", data length: " + frame.data.length);
        
        // Проверяем размеры кадра
        int expectedDataSize = frame.lineSize * frame.height;
        if (frame.data.length < expectedDataSize) {
            System.err.println("Недостаточно данных в кадре: получено " + frame.data.length + 
                             ", ожидается " + expectedDataSize);
            return;
        }
        
        // Проверяем, что lineSize соответствует ожидаемому для RGB формата
        int expectedLineSize = frame.width * 3; // 3 байта на пиксель для RGB
        if (frame.lineSize < expectedLineSize) {
            System.err.println("Неожиданный lineSize: получено " + frame.lineSize + 
                             ", ожидается минимум " + expectedLineSize);
            return;
        }
        
        // Вычисляем размеры для записи в текстуру (масштабируем или обрезаем)
        int writeWidth = Math.min(frame.width, TEXTURE_WIDTH);
        int writeHeight = Math.min(frame.height, TEXTURE_HEIGHT);
        
        // Подготавливаем буфер для записи в текстуру
        int neededSize = writeWidth * writeHeight * 4; // 4 байта на пиксель (RGBA)
        if (bytes == null || bytes.length < neededSize) {
            bytes = new byte[neededSize];
        }
        
        // Копируем данные из кадра в буфер, учитывая lineSize
        int bytesPerPixel = 3; // RGB формат
        for (int y = 0; y < writeHeight; y++) {
            for (int x = 0; x < writeWidth; x++) {
                int srcIndex = y * frame.lineSize + x * bytesPerPixel;
                int dstIndex = (y * writeWidth + x) * 4;
                
                // Проверяем границы массива
                if (srcIndex + 2 >= frame.data.length || dstIndex + 3 >= bytes.length) {
                    System.err.println("Выход за границы массива при копировании пикселя (" + x + "," + y + ")");
                    continue;
                }
                
                bytes[dstIndex] = frame.data[srcIndex];         // R
                bytes[dstIndex + 1] = frame.data[srcIndex + 1]; // G
                bytes[dstIndex + 2] = frame.data[srcIndex + 2]; // B
                bytes[dstIndex + 3] = (byte) 0xFF;              // A (альфа-канал)
            }
        }

        try {
            // Создаем IntBuffer напрямую с правильным размером
            IntBuffer buffer = ByteBuffer.allocateDirect(neededSize)
                    .order(ByteOrder.nativeOrder())
                    .asIntBuffer();
            
            // Конвертируем RGBA байты в int значения правильно
            for (int i = 0; i < writeWidth * writeHeight; i++) {
                int byteIndex = i * 4;
                
                // Проверяем границы
                if (byteIndex + 3 < bytes.length) {
                    int r = bytes[byteIndex] & 0xFF;
                    int g = bytes[byteIndex + 1] & 0xFF;
                    int b = bytes[byteIndex + 2] & 0xFF;
                    int a = bytes[byteIndex + 3] & 0xFF;
                    
                    // Упаковываем в int в формате ABGR (little-endian)
                    int pixel = (a << 24) | (b << 16) | (g << 8) | r;
                    buffer.put(pixel);
                } else {
                    // Заполняем черным цветом если выходим за границы
                    buffer.put(0xFF000000);
                }
            }
            
            buffer.flip(); // Подготавливаем для чтения
            
            System.out.println("Записываем в текстуру: " + writeWidth + "x" + writeHeight + 
                             " пикселей (" + neededSize + " байт), buffer size: " + buffer.remaining());

            // Проверяем размер буфера перед записью
            if (buffer.remaining() != writeWidth * writeHeight) {
                System.err.println("Неправильный размер буфера: ожидается " + (writeWidth * writeHeight) + 
                                 ", получено " + buffer.remaining());
                return;
            }

            // Записываем данные в текстуру с дополнительными проверками
            RenderSystem.getDevice().createCommandEncoder()
                .writeToTexture(glTexture, buffer, NativeImage.Format.RGBA,
                                 0, // mipLevel - уровень мипмапа (0 = базовый уровень)
                                 0, // layer - слой текстуры (0 для обычной 2D текстуры)
                                 0, // x - начальная позиция по X
                                 0, // y - начальная позиция по Y
                                 writeWidth,  // width - ширина записываемой области
                                 writeHeight); // height - высота записываемой области
                                 
        } catch (Exception e) {
            System.err.println("Ошибка при записи кадра в текстуру: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createTexture() {
        System.out.println("Начинаем создание текстуры...");
        
        try {
            GpuDevice gpuDevice = RenderSystem.getDevice();
            if (gpuDevice == null) {
                throw new RuntimeException("GpuDevice недоступен");
            }
            
            // Создаем текстуру с правильными параметрами
            // Параметры: name, usage, format, width, height, depth, mipLevels
            // usage = 5 включает USAGE_COPY_DST (1) и USAGE_TEXTURE_BINDING (4)
            glTexture = gpuDevice.createTexture("video_screen", 5, TextureFormat.RGBA8,
                                              TEXTURE_WIDTH, TEXTURE_HEIGHT, 1, 1);
            
            if (glTexture == null) {
                throw new RuntimeException("Не удалось создать текстуру");
            }
            
            glTextureView = gpuDevice.createTextureView(glTexture);
            if (glTextureView == null) {
                throw new RuntimeException("Не удалось создать представление текстуры");
            }
            
            setFilter(false, false);
            setClamp(false);
            
            System.out.println("Текстура создана успешно: " + TEXTURE_WIDTH + "x" + TEXTURE_HEIGHT);
            
            // Инициализируем текстуру шахматным паттерном (опционально)
            // initializeWithCheckerboard();
            
        } catch (Exception e) {
            System.err.println("Критическая ошибка при создании текстуры: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Не удалось создать текстуру для видео", e);
        }
    }
    
    /**
     * Инициализирует текстуру шахматным паттерном (для отладки)
     */
    private void initializeWithCheckerboard() {
        try {
            System.out.println("Инициализация текстуры шахматным паттерном...");
            
            // Создаем шахматную сетку
            IntBuffer checkerboardData = createCheckerboardPattern();
            
            // Проверяем, что буфер создан корректно
            if (checkerboardData != null && checkerboardData.hasRemaining()) {
                // Записываем начальные данные в текстуру
                RenderSystem.getDevice().createCommandEncoder()
                    .writeToTexture(glTexture, checkerboardData, NativeImage.Format.RGBA,
                              0, // mipLevel - уровень мипмапа (0 = базовый уровень)
                              0, // layer - слой текстуры (0 для обычной 2D текстуры)
                              0, // x - начальная позиция по X
                              0, // y - начальная позиция по Y
                              TEXTURE_WIDTH,  // width - ширина записываемой области
                              TEXTURE_HEIGHT); // height - высота записываемой области

                System.out.println("Шахматный паттерн успешно записан в текстуру!");
                
            } else {
                System.err.println("Ошибка: буфер данных пуст или null!");
            }
        } catch (Exception e) {
            System.err.println("Ошибка при инициализации текстуры: " + e.getMessage());
            e.printStackTrace();
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
    
    /**
     * Освобождает ресурсы текстуры
     */
    public void cleanup() {
        try {
            if (glTexture != null) {
                // Освобождение ресурсов GPU текстуры
                // Minecraft/WebGPU должен сам управлять памятью, но мы можем обнулить ссылки
                System.out.println("Освобождение ресурсов текстуры...");
            }
            
            // Очищаем буфер данных
            bytes = null;
            
            System.out.println("Ресурсы текстуры освобождены");
        } catch (Exception e) {
            System.err.println("Ошибка при освобождении ресурсов текстуры: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Получает текущие размеры текстуры
     */
    public int getTextureWidth() {
        return TEXTURE_WIDTH;
    }
    
    public int getTextureHeight() {
        return TEXTURE_HEIGHT;
    }
    
}
