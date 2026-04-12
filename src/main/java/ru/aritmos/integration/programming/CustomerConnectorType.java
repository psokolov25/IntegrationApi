package ru.aritmos.integration.programming;

/**
 * Тип транспорта для интеграции с внешними CRM/МИС/службой предзаписи.
 */
public enum CustomerConnectorType {
    REST_API,
    DATA_BUS,
    MESSAGE_BROKER;

    public static CustomerConnectorType fromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return REST_API;
        }
        return CustomerConnectorType.valueOf(raw.trim().toUpperCase());
    }
}
