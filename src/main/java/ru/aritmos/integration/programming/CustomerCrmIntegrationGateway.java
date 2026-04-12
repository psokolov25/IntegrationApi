package ru.aritmos.integration.programming;

import ru.aritmos.integration.domain.CustomerLookupRequest;
import ru.aritmos.integration.domain.CustomerMedicalServicesRequest;
import ru.aritmos.integration.domain.CustomerOptimalServiceSelectionRequest;
import ru.aritmos.integration.domain.CustomerServiceAvailabilityRequest;
import ru.aritmos.integration.domain.CustomerPrebookingRequest;
import ru.aritmos.integration.security.core.SubjectPrincipal;

import java.util.Map;

/**
 * Расширяемый gateway для сценариев CRM/МИС заказчика по идентификации клиента и данным записи.
 */
public interface CustomerCrmIntegrationGateway {

    default Map<String, Object> identifyClient(CustomerLookupRequest request) {
        return identifyClient(request, null);
    }

    Map<String, Object> identifyClient(CustomerLookupRequest request, SubjectPrincipal subject);

    default Map<String, Object> fetchAvailableMedicalServices(CustomerMedicalServicesRequest request) {
        return fetchAvailableMedicalServices(request, null);
    }

    Map<String, Object> fetchAvailableMedicalServices(CustomerMedicalServicesRequest request, SubjectPrincipal subject);

    default Map<String, Object> fetchMedicalServicesWithPrerequisites(CustomerMedicalServicesRequest request) {
        return fetchMedicalServicesWithPrerequisites(request, null);
    }

    Map<String, Object> fetchMedicalServicesWithPrerequisites(CustomerMedicalServicesRequest request, SubjectPrincipal subject);

    default Map<String, Object> calculateEligibleMedicalServices(CustomerServiceAvailabilityRequest request) {
        return calculateEligibleMedicalServices(request, null);
    }

    Map<String, Object> calculateEligibleMedicalServices(CustomerServiceAvailabilityRequest request, SubjectPrincipal subject);

    default Map<String, Object> fetchPrebookingData(CustomerPrebookingRequest request) {
        return fetchPrebookingData(request, null);
    }

    Map<String, Object> fetchPrebookingData(CustomerPrebookingRequest request, SubjectPrincipal subject);

    default Map<String, Object> fetchUnifiedPrebookingData(CustomerPrebookingRequest request) {
        return fetchUnifiedPrebookingData(request, null);
    }

    default Map<String, Object> fetchUnifiedPrebookingData(CustomerPrebookingRequest request, SubjectPrincipal subject) {
        return fetchPrebookingData(request, subject);
    }

    default Map<String, Object> selectOptimalMedicalService(CustomerOptimalServiceSelectionRequest request) {
        return selectOptimalMedicalService(request, null);
    }

    Map<String, Object> selectOptimalMedicalService(CustomerOptimalServiceSelectionRequest request, SubjectPrincipal subject);
}
