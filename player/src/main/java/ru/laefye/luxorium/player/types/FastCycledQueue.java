package ru.laefye.luxorium.player.types;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Высокопроизводительная lock-free очередь для аудио данных
 * Оптимизирована для работы с OpenAL
 */
public class FastCycledQueue<T> {
    private final T[] buffer;
    private final int mask;
    private final AtomicInteger writeIndex = new AtomicInteger(0);
    private final AtomicInteger readIndex = new AtomicInteger(0);
    private final AtomicReference<Thread> waitingConsumer = new AtomicReference<>();
    
    @SuppressWarnings("unchecked")
    public FastCycledQueue(int size, Supplier<T> generator) {
        // Размер должен быть степенью двойки для оптимизации
        int actualSize = Integer.highestOneBit(size);
        if (actualSize < size) {
            actualSize <<= 1;
        }
        
        this.buffer = (T[]) new Object[actualSize];
        this.mask = actualSize - 1;
        
        // Предварительно заполняем буфер объектами
        for (int i = 0; i < actualSize; i++) {
            buffer[i] = generator.get();
        }
    }
    
    public T take() throws InterruptedException {
        int currentRead = readIndex.get();
        int currentWrite = writeIndex.get();
        
        // Если очередь пуста, ждем
        while (currentRead == currentWrite) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            
            Thread currentThread = Thread.currentThread();
            waitingConsumer.set(currentThread);
            
            // Двойная проверка после регистрации
            currentWrite = writeIndex.get();
            if (currentRead != currentWrite) {
                waitingConsumer.compareAndSet(currentThread, null);
                break;
            }
            
            LockSupport.parkNanos(1000); // Ждем 1 микросекунду
            currentRead = readIndex.get();
            currentWrite = writeIndex.get();
        }
        
        T item = buffer[currentRead & mask];
        readIndex.lazySet(currentRead + 1);
        return item;
    }
    
    public boolean offer(Consumer<T> consumer) {
        int currentWrite = writeIndex.get();
        int nextWrite = currentWrite + 1;
        int currentRead = readIndex.get();
        
        // Проверяем, не переполнена ли очередь
        if (nextWrite - currentRead > buffer.length) {
            return false; // Очередь полна
        }
        
        T item = buffer[currentWrite & mask];
        consumer.accept(item);
        
        writeIndex.lazySet(nextWrite);
        
        // Уведомляем ожидающего потребителя
        Thread waiting = waitingConsumer.getAndSet(null);
        if (waiting != null) {
            LockSupport.unpark(waiting);
        }
        
        return true;
    }
    
    public int getCount() {
        return writeIndex.get() - readIndex.get();
    }
    
    public void clear() {
        readIndex.set(writeIndex.get());
        waitingConsumer.set(null);
    }
    
    public int getCapacity() {
        return buffer.length;
    }
    
    // Специальный метод для завершения работы
    public void shutdown() {
        Thread waiting = waitingConsumer.getAndSet(null);
        if (waiting != null) {
            waiting.interrupt();
        }
    }
}
