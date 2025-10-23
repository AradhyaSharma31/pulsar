package org.apache.pulsar.client.api;

import java.util.Optional;

/**
 * Context provided to Authentication implementations to access shared services
 * like Netty event loops, DNS resolvers, and timers.
 */
public interface AuthenticationInitContext {

    /**
     * Get a shared service instance by its class.
     *
     * @param <T> the type of service
     * @param serviceClass the class of the service to retrieve
     * @return an Optional containing the service instance, or empty if not available
     */
    <T> Optional<T> getService(Class<T> serviceClass);

    /**
     * Get a shared service instance by its class and name.
     * This allows for multiple instances of the same service type.
     *
     * @param <T> the type of service
     * @param serviceClass the class of the service to retrieve
     * @param name the name identifying the specific service instance
     * @return an Optional containing the service instance, or empty if not available
     */
    <T> Optional<T> getServiceByName(Class<T> serviceClass, String name);
}
