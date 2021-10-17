/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.connectivity.service.messaging.nats;

import io.nats.client.Options;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.messaging.internal.ssl.SSLContextCreator;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;
import org.eclipse.ditto.connectivity.service.messaging.tunnel.SshTunnelState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

/**
 * Factory for creating a Nats {@link Options.Builder} based on a {@link Connection}.
 */
public final class ConnectionBasedNatsConnectionBuilderFactory implements NatsConnectionBuilderFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionBasedNatsConnectionBuilderFactory.class);

    private static final String SECURE_TLS = "tls";
    private static final String SECURE_OPENTLS = "opentls";

    private final Supplier<SshTunnelState> tunnelConfigSupplier;

    private ConnectionBasedNatsConnectionBuilderFactory(final Supplier<SshTunnelState> tunnelConfigSupplier) {
        this.tunnelConfigSupplier = tunnelConfigSupplier;
    }

    /**
     * Returns an instance of {@code ConnectionBasedNatsConnectionBuilderFactory}.
     *
     * @return the instance.
     */
    public static ConnectionBasedNatsConnectionBuilderFactory getInstance(final Supplier<SshTunnelState> tunnelConfigSupplier) {
        return new ConnectionBasedNatsConnectionBuilderFactory(tunnelConfigSupplier);
    }

    @Override
    public Options.Builder createConnectionFactory(final Connection connection, final ConnectionLogger connectionLogger) {
        checkNotNull(connection, "Connection");

        try {
            final Options.Builder builder = new Options.Builder();
            if (SECURE_TLS.equalsIgnoreCase(connection.getProtocol()) ||
                SECURE_OPENTLS.equalsIgnoreCase(connection.getProtocol())) {
                if (connection.isValidateCertificates()) {
                    final SSLContextCreator sslContextCreator =
                            SSLContextCreator.fromConnection(connection, null, connectionLogger);
                    builder.sslContext(sslContextCreator.withoutClientCertificate())
                            .secure();
                } else {
                    LOGGER.warn("Not setting SSL context for Nats");
                }
            }

            final URI uri = tunnelConfigSupplier.get().getURI(connection);
            builder.server(uri.toString());

            configureConnectionFactory(builder, connection.getSpecificConfig());
            return builder;
        } catch (final NoSuchAlgorithmException e) {
            LOGGER.warn(e.getMessage());
            throw new IllegalStateException("Failed to create Nats connection factory.", e);
        }
    }

    private void configureConnectionFactory(final Options.Builder builder,
            final Map<String, String> specificConfig) {
        Optional.ofNullable(specificConfig.get("reconnectWait"))
                .map(Long::parseLong)
                .map(Duration::ofMillis)
                .ifPresent(builder::reconnectWait);
        Optional.ofNullable(specificConfig.get("reconnectJitter"))
                .map(Long::parseLong)
                .map(Duration::ofMillis)
                .ifPresent(builder::reconnectJitter);
        Optional.ofNullable(specificConfig.get("reconnectJitterTls"))
                .map(Long::parseLong)
                .map(Duration::ofMillis)
                .ifPresent(builder::reconnectJitterTls);
        Optional.ofNullable(specificConfig.get("connectionTimeout"))
                .map(Long::parseLong)
                .map(Duration::ofMillis)
                .ifPresent(builder::connectionTimeout);
        Optional.ofNullable(specificConfig.get("pingInterval"))
                .map(Long::parseLong)
                .map(Duration::ofMillis)
                .ifPresent(builder::pingInterval);
        Optional.ofNullable(specificConfig.get("requestCleanupInterval"))
                .map(Long::parseLong)
                .map(Duration::ofMillis)
                .ifPresent(builder::requestCleanupInterval);
        Optional.ofNullable(specificConfig.get("maxPingsOut"))
                .map(Integer::parseInt)
                .ifPresent(builder::maxPingsOut);
        Optional.ofNullable(specificConfig.get("reconnectBufferSize"))
                .map(Long::parseLong)
                .ifPresent(builder::reconnectBufferSize);
        Optional.ofNullable(specificConfig.get("bufferSize"))
                .map(Integer::parseInt)
                .ifPresent(builder::bufferSize);
        Optional.ofNullable(specificConfig.get("maxMessagesInOutgoingQueue"))
                .map(Integer::parseInt)
                .ifPresent(builder::maxMessagesInOutgoingQueue);
    }
}
