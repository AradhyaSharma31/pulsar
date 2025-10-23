package org.apache.pulsar.client.impl;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.pulsar.client.api.AuthenticationInitContext;

/*
 * implementation of AuthenticationInitContext that provides access
 * to shared services like EventLoopGroup, DnsResolver, and Timer.
 */
public class AuthenticationInitContextImpl implements AuthenticationInitContext {
    private final ConcurrentMap<Class<?>, Object> servicesByClass;
    private final ConcurrentMap<String, Object> servicesByName;

    public AuthenticationInitContextImpl() {
        this.servicesByClass = new ConcurrentHashMap<>();
        this.servicesByName = new ConcurrentHashMap<>();
    }

    /*
     * Register a service instance that can be retrieved by its class.
     */
    public <T> void registerService(Class<T> serviceClass, T serviceInstance) {
        if (serviceClass == null) {
            throw new NullPointerException("serviceClass cannot be null");
        }
        if (serviceInstance == null) {
            throw new NullPointerException("serviceInstance cannot be null");
        }
        servicesByClass.put(serviceClass, serviceInstance);
    }

    /*
     * Register a service instance that can be retrieved by name and class.
     */
    public <T> void registerService(Class<T> serviceClass, String name, T serviceInstance) {
        if (serviceClass == null) {
            throw new NullPointerException("serviceClass cannot be null");
        }
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }
        if (serviceInstance == null) {
            throw new NullPointerException("serviceInstance cannot be null");
        }
        String compositeKey = serviceClass.getName() + ":" + name;
        servicesByName.put(compositeKey, serviceInstance);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getService(Class<T> serviceClass) {
        Object service = servicesByClass.get(serviceClass);
        return service != null ? Optional.of((T) service) : Optional.empty();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getServiceByName(Class<T> serviceClass, String name) {
        if (name == null) {
            throw new NullPointerException("name cannot be null");
        }
        String compositeKey = serviceClass.getName() + ":" + name;
        Object service = servicesByName.get(compositeKey);
        return service != null ? Optional.of((T) service) : Optional.empty();
    }
}