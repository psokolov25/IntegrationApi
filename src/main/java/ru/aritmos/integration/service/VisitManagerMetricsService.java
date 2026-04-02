package ru.aritmos.integration.service;

import jakarta.inject.Singleton;
import ru.aritmos.integration.domain.VisitManagerMetricDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Счетчики успешных/ошибочных вызовов по инсталляциям VisitManager.
 */
@Singleton
public class VisitManagerMetricsService {

    private final Map<String, AtomicLong> successCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorCounters = new ConcurrentHashMap<>();

    public void recordSuccess(String target) {
        successCounters.computeIfAbsent(target, key -> new AtomicLong()).incrementAndGet();
    }

    public void recordError(String target) {
        errorCounters.computeIfAbsent(target, key -> new AtomicLong()).incrementAndGet();
    }

    public List<VisitManagerMetricDto> snapshot() {
        List<VisitManagerMetricDto> result = new ArrayList<>();
        successCounters.forEach((target, success) -> result.add(new VisitManagerMetricDto(
                target,
                success.get(),
                errorCounters.getOrDefault(target, new AtomicLong()).get()
        )));
        errorCounters.forEach((target, error) -> {
            if (successCounters.containsKey(target)) {
                return;
            }
            result.add(new VisitManagerMetricDto(target, 0, error.get()));
        });
        return result;
    }
}
