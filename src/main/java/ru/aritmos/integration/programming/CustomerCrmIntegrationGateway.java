package ru.aritmos.integration.programming;

import ru.aritmos.integration.domain.CustomerLookupRequest;
import ru.aritmos.integration.domain.CustomerMedicalServicesRequest;
import ru.aritmos.integration.domain.CustomerPrebookingRequest;

import java.util.Map;

/**
 * Расширяемый gateway для сценариев CRM/МИС заказчика по идентификации клиента и данным записи.
 */
public interface CustomerCrmIntegrationGateway {

    Map<String, Object> identifyClient(CustomerLookupRequest request);

    Map<String, Object> fetchAvailableMedicalServices(CustomerMedicalServicesRequest request);

    Map<String, Object> fetchPrebookingData(CustomerPrebookingRequest request);
}

