package ru.laefye.luxorium.mod.client;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.openal.AL10;

public class MySource {
    private int sourceId;
    private List<Integer> bufferQueue = new ArrayList<>();
    private boolean isPlaying = false;
    private static final int MAX_BUFFERS = 16; // Максимум буферов в очереди
    
    public MySource() {
        // Создаем OpenAL источник
        int[] sources = new int[1];
        AL10.alGenSources(sources);
        sourceId = sources[0];
    }
    
    public void play() {
        if (!isPlaying && !bufferQueue.isEmpty()) {
            AL10.alSourcePlay(sourceId);
            isPlaying = true;
            System.out.println("Воспроизводим стрим!");
        }
    }
    
    public void stop() {
        if (isPlaying) {
            AL10.alSourceStop(sourceId);
            isPlaying = false;
            clearBuffers();
        }
    }
    
    public void setVolume(float volume) {
        AL10.alSourcef(sourceId, AL10.AL_GAIN, volume);
    }
    
    public void setLooping(boolean loop) {
        // Для стримов зацикливание не используется
        // AL10.alSourcei(sourceId, AL10.AL_LOOPING, loop ? AL10.AL_TRUE : AL10.AL_FALSE);
    }
    
    public void write(byte[] pcmBytes, int countBytes) {
        // Удаляем обработанные буферы
        removeProcessedBuffers();
        
        // Если очередь заполнена, ждем
        if (bufferQueue.size() >= MAX_BUFFERS) {
            return; // Пропускаем данные если очередь полная
        }
        
        // Создаем новый буфер
        int[] buffers = new int[1];
        AL10.alGenBuffers(buffers);
        int bufferId = buffers[0];
        
        // Создаем ByteBuffer из переданных данных
        ByteBuffer audioData = ByteBuffer.allocateDirect(countBytes);
        audioData.put(pcmBytes, 0, countBytes);
        audioData.flip();
        
        // Загружаем данные в буфер
        AL10.alBufferData(bufferId, AL10.AL_FORMAT_MONO16, audioData, 44100);
        
        // Добавляем буфер в очередь источника
        AL10.alSourceQueueBuffers(sourceId, new int[]{bufferId});
        bufferQueue.add(bufferId);
        
        System.out.println("Добавлен буфер с " + countBytes + " байт. Всего буферов: " + bufferQueue.size());
        
        // Автоматически запускаем воспроизведение если не играет
        if (!isPlaying && bufferQueue.size() > 2) { // Ждем накопления буферов
            play();
        }
    }
    
    private void removeProcessedBuffers() {
        int processedBuffers = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_PROCESSED);
        
        if (processedBuffers > 0) {
            int[] buffers = new int[processedBuffers];
            AL10.alSourceUnqueueBuffers(sourceId, buffers);
            
            // Удаляем буферы
            AL10.alDeleteBuffers(buffers);
            
            // Удаляем из нашего списка
            for (int i = 0; i < processedBuffers && !bufferQueue.isEmpty(); i++) {
                bufferQueue.remove(0);
            }
            
            System.out.println("Удалено " + processedBuffers + " обработанных буферов");
        }
    }
    
    private void clearBuffers() {
        // Останавливаем источник
        AL10.alSourceStop(sourceId);
        
        // Удаляем все буферы из очереди
        while (!bufferQueue.isEmpty()) {
            try {
                int buffersQueued = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_QUEUED);
                if (buffersQueued > 0) {
                    int[] buffers = new int[buffersQueued];
                    AL10.alSourceUnqueueBuffers(sourceId, buffers);
                    AL10.alDeleteBuffers(buffers);
                }
                break;
            } catch (Exception e) {
                // Если не можем удалить буферы из очереди, удаляем их напрямую
                for (Integer bufferId : bufferQueue) {
                    AL10.alDeleteBuffers(bufferId);
                }
                break;
            }
        }
        
        bufferQueue.clear();
    }
    
    public void tick() {
        if (isPlaying) {
            removeProcessedBuffers();
            
            // Проверяем, не закончились ли буферы
            int buffersQueued = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_QUEUED);
            if (buffersQueued == 0) {
                isPlaying = false;
                System.out.println("Воспроизведение завершено - буферы закончились");
            }
        }
    }
    
    public void cleanup() {
        stop();
        clearBuffers();
        AL10.alDeleteSources(sourceId);
    }
    
    public boolean isPlaying() {
        return AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
    }
}
