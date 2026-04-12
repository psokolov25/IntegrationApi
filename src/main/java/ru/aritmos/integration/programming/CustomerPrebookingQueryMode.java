package ru.aritmos.integration.programming;

/**
 * Режимы универсального запроса к МИС/службе предзаписи.
 */
public enum CustomerPrebookingQueryMode {
    LEGACY_PREBOOKING,
    TIME_WINDOWS,
    SERVICES_FLAT,
    SERVICES_WITH_PREREQUISITES,
    TIME_WINDOWS_AND_SERVICES,
    TIME_WINDOWS_AND_SERVICES_WITH_PREREQUISITES;

    public static CustomerPrebookingQueryMode fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return LEGACY_PREBOOKING;
        }
        return CustomerPrebookingQueryMode.valueOf(raw.trim().toUpperCase());
    }

    public boolean requiresTimeWindows() {
        return this == TIME_WINDOWS
                || this == TIME_WINDOWS_AND_SERVICES
                || this == TIME_WINDOWS_AND_SERVICES_WITH_PREREQUISITES;
    }

    public boolean requiresServices() {
        return this == SERVICES_FLAT
                || this == SERVICES_WITH_PREREQUISITES
                || this == TIME_WINDOWS_AND_SERVICES
                || this == TIME_WINDOWS_AND_SERVICES_WITH_PREREQUISITES;
    }

    public boolean requiresPrerequisites() {
        return this == SERVICES_WITH_PREREQUISITES
                || this == TIME_WINDOWS_AND_SERVICES_WITH_PREREQUISITES;
    }
}
