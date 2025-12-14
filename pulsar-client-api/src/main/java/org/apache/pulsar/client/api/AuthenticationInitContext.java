/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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