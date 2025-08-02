package ru.laefye.luxorium.mod.client.sound;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.openal.AL10;

public class SoundEmitter {
    private final int sourceId;
    private final List<Integer> activeBuffers = new ArrayList<>();
    private static final int MAX_BUFFERS = 64; // Ограничиваем количество буферов
    private boolean isPlaying = false;

    public SoundEmitter() {
        int[] sources = new int[1];
        AL10.alGenSources(sources);
        sourceId = sources[0];
        
        if (sourceId == 0) {
            throw new RuntimeException("Failed to create OpenAL source");
        }
    }

    public void play() {
        if (sourceId != 0 && !isPlaying) {
            int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING && !activeBuffers.isEmpty()) {
                AL10.alSourcePlay(sourceId);
                isPlaying = true;
                System.out.println("SoundEmitter: Started playback");
            }
        }
    }

    public void stop() {
        if (sourceId != 0 && isPlaying) {
            AL10.alSourceStop(sourceId);
            isPlaying = false;
            clearAllBuffers();
            System.out.println("SoundEmitter: Stopped playback");
        }
    }

    public void setVolume(float volume) {
        if (sourceId != 0) {
            AL10.alSourcef(sourceId, AL10.AL_GAIN, Math.max(0.0f, Math.min(1.0f, volume)));
        }
    }

    private void removeProcessedBuffers() {
        if (sourceId == 0) return;
        
        int processed = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_PROCESSED);
        
        if (processed > 0) {
            int[] processedBuffers = new int[processed];
            AL10.alSourceUnqueueBuffers(sourceId, processedBuffers);
            
            // Удаляем буферы
            for (int buffer : processedBuffers) {
                if (buffer != 0) {
                    AL10.alDeleteBuffers(buffer);
                    activeBuffers.remove(Integer.valueOf(buffer));
                }
            }
            
            System.out.println("SoundEmitter: Removed " + processed + " processed buffers. Active: " + activeBuffers.size());
        }
        
        // Проверяем, не закончилось ли воспроизведение
        if (isPlaying) {
            int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
            int queued = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_QUEUED);
            
            if (state != AL10.AL_PLAYING && queued == 0) {
                isPlaying = false;
                System.out.println("SoundEmitter: Playback finished naturally");
            }
        }
    }

    public boolean isQueueFull() {
        removeProcessedBuffers();
        return activeBuffers.size() >= MAX_BUFFERS;
    }

    public void write(byte[] pcmBytes, int countBytes, int sampleRate) {
        if (sourceId == 0 || pcmBytes == null || countBytes <= 0) {
            return;
        }
        
        // Удаляем обработанные буферы
        removeProcessedBuffers();
        
        // Проверяем, не переполнена ли очередь
        if (activeBuffers.size() >= MAX_BUFFERS) {
            System.out.println("SoundEmitter: Buffer queue full, skipping data");
            return;
        }
        
        try {
            // Создаем новый буфер
            int[] buffers = new int[1];
            AL10.alGenBuffers(buffers);
            int bufferId = buffers[0];
            
            if (bufferId == 0) {
                System.err.println("SoundEmitter: Failed to generate buffer");
                return;
            }
            
            // Копируем данные в ByteBuffer
            ByteBuffer buffer = ByteBuffer.allocateDirect(countBytes);
            buffer.put(pcmBytes, 0, countBytes);
            buffer.flip();
            
            // Загружаем данные в буфер
            AL10.alBufferData(bufferId, AL10.AL_FORMAT_MONO16, buffer, sampleRate);
            
            // Проверяем ошибки
            int error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                System.err.println("SoundEmitter: OpenAL error after alBufferData: " + error);
                AL10.alDeleteBuffers(bufferId);
                return;
            }
            
            // Добавляем буфер в очередь
            AL10.alSourceQueueBuffers(sourceId, bufferId);
            activeBuffers.add(bufferId);
            
            System.out.println("SoundEmitter: Queued buffer " + bufferId + " with " + countBytes + " bytes. Queue size: " + activeBuffers.size());
            
            // Автоматически запускаем воспроизведение
            if (!isPlaying && activeBuffers.size() >= 2) {
                play();
            }
            
        } catch (Exception e) {
            System.err.println("SoundEmitter: Error in write(): " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void clearAllBuffers() {
        if (sourceId == 0) return;
        
        try {
            // Останавливаем источник
            AL10.alSourceStop(sourceId);
            
            // Удаляем все буферы из очереди
            int queued = AL10.alGetSourcei(sourceId, AL10.AL_BUFFERS_QUEUED);
            if (queued > 0) {
                int[] queuedBuffers = new int[queued];
                AL10.alSourceUnqueueBuffers(sourceId, queuedBuffers);
                
                for (int buffer : queuedBuffers) {
                    if (buffer != 0) {
                        AL10.alDeleteBuffers(buffer);
                    }
                }
            }
            
            activeBuffers.clear();
            System.out.println("SoundEmitter: Cleared all buffers");
            
        } catch (Exception e) {
            System.err.println("SoundEmitter: Error clearing buffers: " + e.getMessage());
            
            // Если не удалось корректно очистить через очередь, удаляем буферы напрямую
            for (Integer bufferId : activeBuffers) {
                try {
                    AL10.alDeleteBuffers(bufferId);
                } catch (Exception ignored) {}
            }
            activeBuffers.clear();
        }
    }
    
    public void cleanup() {
        System.out.println("SoundEmitter: Starting cleanup");
        stop();
        clearAllBuffers();
        
        if (sourceId != 0) {
            AL10.alDeleteSources(sourceId);
            System.out.println("SoundEmitter: Deleted source " + sourceId);
        }
    }
    
    public void tick() {
        removeProcessedBuffers();
    }
    
    public boolean isPlaying() {
        return isPlaying && sourceId != 0;
    }
}
