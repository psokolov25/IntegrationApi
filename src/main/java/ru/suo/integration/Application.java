package ru.suo.integration;

import io.micronaut.runtime.Micronaut;

/**
 * Точка запуска интеграционного шлюза VisitManager.
 */
public class Application {

    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
