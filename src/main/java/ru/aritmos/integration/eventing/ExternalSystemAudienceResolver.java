package ru.aritmos.integration.eventing;

import java.util.Set;

/**
 * Расширяемая точка определения целевых внешних систем для событий DataBus.
 * Нужна для роли посредника между VisitManager и контурами заказчика (АРМ/приемная и др.).
 */
public interface ExternalSystemAudienceResolver {

    Set<String> resolve(IntegrationEvent event);
}
