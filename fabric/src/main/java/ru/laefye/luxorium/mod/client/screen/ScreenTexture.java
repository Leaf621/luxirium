package ru.laefye.luxorium.mod.client.screen;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.TextureFormat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import ru.laefye.luxorium.player.types.VideoFrame;

public class ScreenTexture extends AbstractTexture {
    
    private final int width;
    private final int height;

    public ScreenTexture(int width, int height) {
        this.width = width;
        this.height = height;
        createTexture();
        buffer = ByteBuffer.allocateDirect(width * height * 4)
                .order(ByteOrder.nativeOrder())
                .asIntBuffer();
    }

    private byte[] bytes;

    private IntBuffer buffer;

    public void setFrame(VideoFrame frame, boolean sync) {
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
        int writeWidth = Math.min(frame.width, width);
        int writeHeight = Math.min(frame.height, height);
        
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
            buffer.clear();

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
            
            // Проверяем размер буфера перед записью
            if (buffer.remaining() != writeWidth * writeHeight) {
                System.err.println("Неправильный размер буфера: ожидается " + (writeWidth * writeHeight) + 
                                 ", получено " + buffer.remaining());
                return;
            }

            if (sync) {
                updateTexture(buffer, writeWidth, writeHeight);
            } else {
                MinecraftClient.getInstance().execute(() -> updateTexture(buffer, writeWidth, writeHeight));
            }
                                 
        } catch (Exception e) {
            System.err.println("Ошибка при записи кадра в текстуру: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateTexture(IntBuffer buffer, int writeWidth, int writeHeight)
    {
        RenderSystem.getDevice().createCommandEncoder()
                .writeToTexture(glTexture, buffer, NativeImage.Format.RGBA,
                        0, // mipLevel - уровень мипмапа (0 = базовый уровень)
                        0, // layer - слой текстуры (0 для обычной 2D текстуры)
                        0, // x - начальная позиция по X
                        0, // y - начальная позиция по Y
                        writeWidth,  // width - ширина записываемой области
                        writeHeight); // height - высота записываемой области
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
            glTexture = gpuDevice.createTexture("video_screen", 5, TextureFormat.RGBA8, width, height, 1, 1);
            
            if (glTexture == null) {
                throw new RuntimeException("Не удалось создать текстуру");
            }
            
            glTextureView = gpuDevice.createTextureView(glTexture);
            if (glTextureView == null) {
                throw new RuntimeException("Не удалось создать представление текстуры");
            }
            
            setFilter(false, false);
            setClamp(false);

            System.out.println("Текстура создана успешно: " + width + "x" + height);

        } catch (Exception e) {
            System.err.println("Критическая ошибка при создании текстуры: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Не удалось создать текстуру для видео", e);
        }
    }

    public int getTextureWidth() {
        return width;
    }
    
    public int getTextureHeight() {
        return height;
    }
    
}
