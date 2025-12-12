package org.apache.pulsar.client.impl;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.pulsar.client.api.AuthenticationInitContext;

public class AuthenticationInitContextImpl implements AuthenticationInitContext {

    private final ConcurrentHashMap<Class<?>, Object> services = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> namedServices = new ConcurrentHashMap<>();

    @Override
    public <T> Optional<T> getService(Class<T> serviceClass) {
        return Optional.ofNullable(serviceClass.cast(services.get(serviceClass)));
    }

    @Override
    public <T> Optional<T> getServiceByName(Class<T> serviceClass, String name) {
        return Optional.ofNullable(serviceClass.cast(namedServices.get(name)));
    }

    public void registerService(Class<?> serviceClass, Object serviceInstance) {
        services.put(serviceClass, serviceInstance);
    }

    public void registerServiceByName(String name, Object serviceInstance) {
        namedServices.put(name, serviceInstance);
    }
}
