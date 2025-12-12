package org.apache.pulsar.client.api;
import java.util.Optional;

/**
 * Context passed to authentication providers during initialization.
 * Allows authentication implementations to access shared resources like
 * Netty EventLoopGroup, DNS resolver, and Timer instances.
 */
public interface AuthenticationInitContext {

    /**
     * Get a shared service instance by its class type.
     * Useful for retrieving singleton resources like EventLoopGroup or DnsResolver.
     *
     * @param <T> the type of service to retrieve
     * @param serviceClass the class of the service to retrieve
     * @return an Optional containing the service if available, empty otherwise
     */
    <T> Optional<T> getService(Class<T> serviceClass);

    /**
     * Get a named shared service instance by its class type and name.
     * Useful when multiple instances of the same service type exist.
     *
     * @param <T> the type of service to retrieve
     * @param serviceClass the class of the service to retrieve
     * @param name the name identifying the specific service instance
     * @return an Optional containing the service if available, empty otherwise
     */
    <T> Optional<T> getServiceByName(Class<T> serviceClass, String name);
}