package ru.laefye.luxorium.player.types;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CycledQueue<T> {
    private final List<T> list;
    private final int size;
    private int currentIndex = 0;
    private int index = 0;
    private CountDownLatch latch = new CountDownLatch(0);
    private final Semaphore mutex = new Semaphore(1);

    public CycledQueue(int max, Supplier<T> generator) {
        this.list = new ArrayList<>(max);
        for (int i = 0; i < max; i++) {
            list.add(generator.get());
        }
        this.size = list.size();
    }

    public T take() throws InterruptedException {
        if (currentIndex == index) {
            latch = new CountDownLatch(1);
            latch.await();
        }
        T obj = list.get(currentIndex);
        currentIndex = (currentIndex + 1) % size;
        return obj;
    }

    public void offer(Consumer<T> consumer) throws InterruptedException {
        mutex.acquire();
        try {
            consumer.accept(list.get(index));
            index = (index + 1) % size;
            latch.countDown();
        } finally {
            mutex.release();
        }
    }

    public int getCount() {
        return (index - currentIndex + size) % size;
    }

    public void clear() {
        currentIndex = index;
    }

    public int getSize() {
        return size;
    }
}
