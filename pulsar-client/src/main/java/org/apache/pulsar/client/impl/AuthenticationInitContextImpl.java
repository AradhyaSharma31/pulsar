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
