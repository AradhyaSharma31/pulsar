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

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import io.netty.channel.EventLoopGroup;
import io.netty.util.Timer;
import java.util.Optional;
import org.apache.pulsar.client.impl.AuthenticationInitContextImpl;
import org.testng.annotations.Test;

public class AuthenticationInitContextTest {

    @Test
    public void testGetServiceReturnsRegisteredInstance() {
        AuthenticationInitContextImpl context = new AuthenticationInitContextImpl();
        EventLoopGroup mockEventLoop = mock(EventLoopGroup.class);

        context.registerService(EventLoopGroup.class, mockEventLoop);
        Optional<EventLoopGroup> result = context.getService(EventLoopGroup.class);

        assertTrue(result.isPresent(), "Service should be present after registration");
        assertSame(mockEventLoop, result.get(), "Should return the same instance that was registered");
    }

    @Test
    public void testGetServiceReturnsEmptyForUnregisteredService() {
        AuthenticationInitContextImpl context = new AuthenticationInitContextImpl();

        Optional<String> result = context.getService(String.class);

        assertFalse(result.isPresent(), "Should return empty for unregistered service");
    }

    @Test
    public void testGetServiceByNameReturnsRegisteredNamedInstance() {
        AuthenticationInitContextImpl context = new AuthenticationInitContextImpl();
        Timer fastTimer = mock(Timer.class);
        Timer slowTimer = mock(Timer.class);

        context.registerServiceByName("fast-timer", fastTimer);
        context.registerServiceByName("slow-timer", slowTimer);

        Optional<Timer> result1 = context.getServiceByName(Timer.class, "fast-timer");
        Optional<Timer> result2 = context.getServiceByName(Timer.class, "slow-timer");

        assertTrue(result1.isPresent(), "Named service 'fast-timer' should be present");
        assertTrue(result2.isPresent(), "Named service 'slow-timer' should be present");
        assertSame(fastTimer, result1.get(), "Should return correct named instance");
        assertSame(slowTimer, result2.get(), "Should return correct named instance");
    }

    @Test
    public void testGetServiceByNameReturnsEmptyForUnregisteredName() {
        AuthenticationInitContextImpl context = new AuthenticationInitContextImpl();
        Timer timer = mock(Timer.class);
        context.registerServiceByName("existing-timer", timer);

        Optional<Timer> result = context.getServiceByName(Timer.class, "non-existent-timer");

        assertFalse(result.isPresent(), "Should return empty for unregistered name");
    }

    @Test
    public void testServiceRegistrationOverwritesPrevious() {
        AuthenticationInitContextImpl context = new AuthenticationInitContextImpl();
        EventLoopGroup oldEventLoop = mock(EventLoopGroup.class);
        EventLoopGroup newEventLoop = mock(EventLoopGroup.class);

        context.registerService(EventLoopGroup.class, oldEventLoop);
        context.registerService(EventLoopGroup.class, newEventLoop);

        Optional<EventLoopGroup> result = context.getService(EventLoopGroup.class);

        assertTrue(result.isPresent());
        assertSame(newEventLoop, result.get(), "Should return the most recently registered instance");
        assertNotSame(oldEventLoop, result.get(), "Should not return the old instance");
    }

    @Test
    public void testGetServiceWithWrongClassTypeReturnsEmpty() {
        AuthenticationInitContextImpl context = new AuthenticationInitContextImpl();
        EventLoopGroup mockEventLoop = mock(EventLoopGroup.class);
        context.registerService(EventLoopGroup.class, mockEventLoop);

        Optional<Timer> result = context.getService(Timer.class);

        assertFalse(result.isPresent(), "Should return empty when requesting wrong class type");
    }
}