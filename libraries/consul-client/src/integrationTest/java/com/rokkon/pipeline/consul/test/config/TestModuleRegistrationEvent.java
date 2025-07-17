package com.rokkon.pipeline.consul.test.config;

import com.rokkon.pipeline.events.ModuleRegistrationResponseEvent;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;
import java.lang.annotation.Annotation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Test implementation of Event for module registration.
 * Used in integration tests to avoid CDI dependencies.
 */
public class TestModuleRegistrationEvent implements Event<ModuleRegistrationResponseEvent> {
    
    @Override
    public void fire(ModuleRegistrationResponseEvent event) {
        // No-op for tests
    }
    
    @Override
    public <U extends ModuleRegistrationResponseEvent> CompletionStage<U> fireAsync(U event) {
        // Return completed future for tests
        return CompletableFuture.completedFuture(event);
    }
    
    @Override
    public <U extends ModuleRegistrationResponseEvent> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
        // Return completed future for tests
        return CompletableFuture.completedFuture(event);
    }
    
    @Override
    public Event<ModuleRegistrationResponseEvent> select(Annotation... qualifiers) {
        return this;
    }
    
    @Override
    public <U extends ModuleRegistrationResponseEvent> Event<U> select(Class<U> subtype, Annotation... qualifiers) {
        return (Event<U>) this;
    }
    
    @Override
    public <U extends ModuleRegistrationResponseEvent> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
        return (Event<U>) this;
    }
}