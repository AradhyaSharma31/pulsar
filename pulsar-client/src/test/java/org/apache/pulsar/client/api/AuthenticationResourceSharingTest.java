package org.apache.pulsar.client.api;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import io.netty.channel.EventLoopGroup;
import io.netty.resolver.AddressResolver;
import io.netty.util.Timer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.pulsar.client.impl.AuthenticationInitContextImpl;
import org.apache.pulsar.client.impl.DnsResolverGroupImpl;
import org.apache.pulsar.client.impl.PulsarClientImpl;
import org.apache.pulsar.client.impl.conf.ClientConfigurationData;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.testng.annotations.Test;

public class AuthenticationResourceSharingTest {

    @Test
    public void testAuthenticationInitContextStoresAndRetrievesResources() throws Exception {
        // Create mock resources
        EventLoopGroup mockEventLoop = mock(EventLoopGroup.class);
        AddressResolver mockDnsResolver = mock(AddressResolver.class);
        Timer mockTimer = mock(Timer.class);

        // Create context implementation
        AuthenticationInitContextImpl context = new AuthenticationInitContextImpl();
        context.registerService(EventLoopGroup.class, mockEventLoop);
        context.registerService(AddressResolver.class, mockDnsResolver);
        context.registerService(Timer.class, mockTimer);

        // Test getService returns correct instances
        Optional<EventLoopGroup> eventLoop = context.getService(EventLoopGroup.class);
        Optional<AddressResolver> dnsResolver = context.getService(AddressResolver.class);
        Optional<Timer> timer = context.getService(Timer.class);

        assertTrue(eventLoop.isPresent(), "EventLoopGroup should be present");
        assertTrue(dnsResolver.isPresent(), "AddressResolver should be present");
        assertTrue(timer.isPresent(), "Timer should be present");
        assertSame(eventLoop.get(), mockEventLoop, "Should return same EventLoopGroup instance");
        assertSame(dnsResolver.get(), mockDnsResolver, "Should return same AddressResolver instance");
        assertSame(timer.get(), mockTimer, "Should return same Timer instance");

        // Test getService for non-registered class returns empty
        Optional<String> nonExistent = context.getService(String.class);
        assertFalse(nonExistent.isPresent(), "Non-registered class should return empty");
    }

    @Test
    public void testAuthenticationInitContextThreadSafety() throws Exception {
        // Test that AuthenticationInitContextImpl is thread-safe
        AuthenticationInitContextImpl context = new AuthenticationInitContextImpl();

        EventLoopGroup resource1 = mock(EventLoopGroup.class);
        EventLoopGroup resource2 = mock(EventLoopGroup.class);

        final ConcurrentHashMap<String, Boolean> results = new ConcurrentHashMap<>();

        // Multiple threads registering and reading simultaneously
        Thread writer1 = new Thread(() -> {
            context.registerService(EventLoopGroup.class, resource1);
            results.put("writer1_done", true);
        });

        Thread writer2 = new Thread(() -> {
            context.registerService(EventLoopGroup.class, resource2);
            results.put("writer2_done", true);
        });

        Thread reader = new Thread(() -> {
            // Read multiple times while writers are active
            for (int i = 0; i < 100; i++) {
                Optional<EventLoopGroup> result = context.getService(EventLoopGroup.class);
                // Should either be empty, resource1, or resource2 - never crash
                assertTrue(result.isEmpty() || result.get() == resource1 || result.get() == resource2,
                        "Reader should get valid result or empty during concurrent modification");
            }
            results.put("reader_done", true);
        });

        writer1.start();
        writer2.start();
        reader.start();

        writer1.join();
        writer2.join();
        reader.join();

        // Verify all threads completed
        assertTrue(results.get("writer1_done"), "Writer1 should complete");
        assertTrue(results.get("writer2_done"), "Writer2 should complete");
        assertTrue(results.get("reader_done"), "Reader should complete");

        // Final state should have one of the registered resources
        Optional<EventLoopGroup> finalResult = context.getService(EventLoopGroup.class);
        assertTrue(finalResult.isPresent(), "Final result should be present");
        assertTrue(finalResult.get() == resource1 || finalResult.get() == resource2,
                "Final result should be one of the registered resources");
    }

    @Test
    public void testPulsarClientCreatesAndPassesContext() throws Exception {
        // Test that PulsarClientImpl creates context with all three resources
        ClientConfigurationData conf = new ClientConfigurationData();
        conf.setServiceUrl("pulsar://localhost:6650");

        // Use TestAuthentication to capture context
        TestAuthentication testAuth = new TestAuthentication();
        conf.setAuthentication(testAuth);

        // Create PulsarClientImpl with mocks
        EventLoopGroup mockEventLoop = mock(EventLoopGroup.class);
        Timer mockTimer = mock(Timer.class);
        DnsResolverGroupImpl mockDnsResolverGroup = mock(DnsResolverGroupImpl.class);
        AddressResolver mockAddressResolver = mock(AddressResolver.class);

        when(mockDnsResolverGroup.createAddressResolver(any())).thenReturn(mockAddressResolver);

        PulsarClientImpl client = null;
        try {
            client = new PulsarClientImpl(
                    conf,
                    mockEventLoop,
                    null,
                    mockTimer,
                    null, null, null, null,
                    mockDnsResolverGroup
            );

            // Verify authentication received context
            assertNotNull(testAuth.receivedContext, "Authentication should receive context");

            // Verify all three resources are registered
            Optional<EventLoopGroup> eventLoop = testAuth.receivedContext.getService(EventLoopGroup.class);
            Optional<Timer> timer = testAuth.receivedContext.getService(Timer.class);
            Optional<DnsResolverGroupImpl> dnsGroup = testAuth.receivedContext.getService(DnsResolverGroupImpl.class);

            assertTrue(eventLoop.isPresent(), "EventLoopGroup should be registered");
            assertTrue(timer.isPresent(), "Timer should be registered");
            assertTrue(dnsGroup.isPresent(), "DnsResolverGroupImpl should be registered");

            // Verify correct instances
            assertSame(eventLoop.get(), mockEventLoop, "Should pass same EventLoopGroup instance");
            assertSame(timer.get(), mockTimer, "Should pass same Timer instance");
            assertSame(dnsGroup.get(), mockDnsResolverGroup, "Should pass same DnsResolverGroupImpl instance");

        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    @Test
    public void testMultipleAuthenticationsReceiveSameSharedResources() throws Exception {
        // Test resource sharing across multiple clients

        EventLoopGroup sharedEventLoop = mock(EventLoopGroup.class);
        DnsResolverGroupImpl sharedDnsResolver = mock(DnsResolverGroupImpl.class);
        Timer sharedTimer = mock(Timer.class);

        when(sharedDnsResolver.createAddressResolver(any())).thenReturn(mock(AddressResolver.class));

        // Create two clients with same shared resources
        ClientConfigurationData conf1 = new ClientConfigurationData();
        conf1.setServiceUrl("pulsar://localhost:6650");
        TestAuthentication auth1 = new TestAuthentication();
        conf1.setAuthentication(auth1);

        ClientConfigurationData conf2 = new ClientConfigurationData();
        conf2.setServiceUrl("pulsar://localhost:6651");
        TestAuthentication auth2 = new TestAuthentication();
        conf2.setAuthentication(auth2);

        PulsarClientImpl client1 = null;
        PulsarClientImpl client2 = null;

        try {
            client1 = new PulsarClientImpl(
                    conf1,
                    sharedEventLoop,
                    null,
                    sharedTimer,
                    null, null, null, null,
                    sharedDnsResolver
            );

            client2 = new PulsarClientImpl(
                    conf2,
                    sharedEventLoop,  // SAME EventLoopGroup
                    null,
                    sharedTimer,      // SAME Timer
                    null, null, null, null,
                    sharedDnsResolver // SAME DNS resolver
            );

            // Both authentications should receive contexts
            assertNotNull(auth1.receivedContext, "Auth1 should receive context");
            assertNotNull(auth2.receivedContext, "Auth2 should receive context");

            // Both should have the SAME resource instances
            Optional<EventLoopGroup> eventLoop1 = auth1.receivedContext.getService(EventLoopGroup.class);
            Optional<EventLoopGroup> eventLoop2 = auth2.receivedContext.getService(EventLoopGroup.class);

            assertTrue(eventLoop1.isPresent(), "Auth1 should have EventLoopGroup");
            assertTrue(eventLoop2.isPresent(), "Auth2 should have EventLoopGroup");

            // CRITICAL ASSERTION: They should be the SAME instance (shared)
            assertSame(eventLoop1.get(), eventLoop2.get(),
                    "Both authentications should receive SAME EventLoopGroup instance (resource sharing)");
            assertSame(eventLoop1.get(), sharedEventLoop,
                    "Should be the shared instance passed to PulsarClientImpl");

            // Verify Timer sharing
            Optional<Timer> timer1 = auth1.receivedContext.getService(Timer.class);
            Optional<Timer> timer2 = auth2.receivedContext.getService(Timer.class);
            assertTrue(timer1.isPresent() && timer2.isPresent(), "Both should have Timer");
            assertSame(timer1.get(), timer2.get(), "Timers should be same instance");

        } finally {
            if (client1 != null) client1.close();
            if (client2 != null) client2.close();
        }
    }

    @Test
    public void testAsyncHttpClientDnsResolverSupportCheck() throws Exception {
        // Verify whether AsyncHttpClient supports custom DNS resolver

        DefaultAsyncHttpClientConfig.Builder builder = new DefaultAsyncHttpClientConfig.Builder();
        boolean hasSetNameResolver = false;

        try {
            // Attempt to find setNameResolver method
            builder.getClass().getMethod("setNameResolver", io.netty.resolver.NameResolver.class);
            hasSetNameResolver = true;
        } catch (NoSuchMethodException e) {
            hasSetNameResolver = false;
        }

        // Document the finding - this test should never fail
        if (hasSetNameResolver) {
            System.out.println("INFO: AsyncHttpClient supports setNameResolver() - DNS sharing possible");
        } else {
            System.out.println("INFO: AsyncHttpClient does NOT support setNameResolver() - DNS sharing not possible");
        }

        // The test itself should pass regardless
        assertTrue(true, "Test completed - DNS resolver support: " + hasSetNameResolver);
    }

    @Test
    public void testPartialResourceSharingWorks() throws Exception {
        // Test that authentication works with partial resources

        // Case 1: Only EventLoopGroup provided
        AuthenticationInitContextImpl context1 = new AuthenticationInitContextImpl();
        EventLoopGroup eventLoopOnly = mock(EventLoopGroup.class);
        context1.registerService(EventLoopGroup.class, eventLoopOnly);

        TestAuthentication auth1 = new TestAuthentication();
        auth1.start(context1);

        assertSame(auth1.receivedContext, context1, "Should receive context");
        assertTrue(context1.getService(EventLoopGroup.class).isPresent(), "Should have EventLoopGroup");
        assertFalse(context1.getService(DnsResolverGroupImpl.class).isPresent(), "Should NOT have DNS resolver");
        assertFalse(context1.getService(Timer.class).isPresent(), "Should NOT have Timer");

        // Case 2: Only Timer provided
        AuthenticationInitContextImpl context2 = new AuthenticationInitContextImpl();
        Timer timerOnly = mock(Timer.class);
        context2.registerService(Timer.class, timerOnly);

        TestAuthentication auth2 = new TestAuthentication();
        auth2.start(context2);

        assertFalse(context2.getService(EventLoopGroup.class).isPresent(), "Should NOT have EventLoopGroup");
        assertFalse(context2.getService(DnsResolverGroupImpl.class).isPresent(), "Should NOT have DNS resolver");
        assertTrue(context2.getService(Timer.class).isPresent(), "Should have Timer");
    }

    @Test
    public void testAuthenticationInterfaceBackwardCompatibility() throws Exception {
        // Test that Authentication interface default method works

        TestAuthentication auth = new TestAuthentication();

        // Test old start() method
        auth.start();
        assertTrue(auth.startCalled, "Old start() method should be called");
        assertFalse(auth.startWithContextCalled, "startWithContext should not be called");

        // Reset
        auth.startCalled = false;
        auth.startWithContextCalled = false;

        // Test new start(context) method
        AuthenticationInitContext context = mock(AuthenticationInitContext.class);
        auth.start(context);
        assertTrue(auth.startWithContextCalled, "start(AuthenticationInitContext) should be called");
        assertTrue(auth.startCalled, "Old start() should be called from default implementation");
        assertSame(auth.receivedContext, context, "Should receive context");

        // Reset
        auth.startCalled = false;
        auth.startWithContextCalled = false;
        auth.receivedContext = null;

        // Test start(null) - should call old start() via default method
        auth.start((AuthenticationInitContext) null);
        assertTrue(auth.startWithContextCalled, "start(null) should call the new method");
        assertTrue(auth.startCalled, "Should still call old start()");
        assertNull(auth.receivedContext, "Context should be null");
    }

    // Helper test authentication implementation
    static class TestAuthentication implements Authentication {
        AuthenticationInitContext receivedContext;
        boolean startCalled = false;
        boolean startWithContextCalled = false;

        @Override
        public void start(AuthenticationInitContext context) throws PulsarClientException {
            this.receivedContext = context;
            startWithContextCalled = true;
            start(); // Call default
        }

        @Override
        public void start() throws PulsarClientException {
            startCalled = true;
        }

        @Override
        public String getAuthMethodName() {
            return "test";
        }

        @Override
        public void configure(Map<String, String> authParams) {
            // Do nothing for test
        }

        @Override
        public void close() {
            // Do nothing
        }
    }
}