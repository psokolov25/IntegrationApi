package ru.aritmos.integration.service;

import jakarta.inject.Singleton;

/**
 * Пробник аппаратных ресурсов текущей JVM/хоста.
 */
@Singleton
public class RuntimeHardwareProbe {

    public int availableProcessors() {
        return Runtime.getRuntime().availableProcessors();
    }

    public long maxMemoryBytes() {
        return Runtime.getRuntime().maxMemory();
    }
}
