/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.config;

import org.eclipse.ditto.internal.utils.config.KnownConfigValue;

import javax.annotation.concurrent.Immutable;
import java.time.Duration;
import java.util.List;
import static io.nats.client.Options.*;

/**
 * Provides the configuration to connect to a Nats.io instance or cluster
 */
@Immutable
public interface NatsConfig {

    List<String> getServers();
    boolean isNoRandomize();
    String getConnectionName();
    boolean isVerbose();
    boolean isPedantic();
    int getMaxControlLine();
    int getMaxReconnect();
    Duration getReconnectWait();
    Duration getReconnectJitter();
    Duration getReconnectJitterTls();
    Duration getConnectionTimeout();
    Duration getPingInterval();
    Duration getRequestCleanupInterval();
    int getMaxPingsOut();
    int getReconnectBufferSize();
    boolean isUseOldRequestStyle();
    int getBufferSize();
    boolean isTraceAdvancedStats();
    boolean isTraceConnection();
    boolean isNoEcho();
    boolean isNoHeaders();
    boolean isNoNoResponders();
    boolean isUtf8Response();
    String getInboxPrefix();
    int getMaxMessagesInOutgoingQueue();
    boolean isDiscardMessagesWhenOutgoingQueueFull();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code NatsConfig}.
     */
    enum NatsConfigValue implements KnownConfigValue {
        SERVERS("servers", List.of(DEFAULT_URL)),
        NO_RANDOMIZE("no-randomize", false),
        CONNECTION_NAME("connection-name", ""),
        VERBOSE("verbose", false),
        PEDANTIC("pedantic", false),
        MAX_CONTROL_LINE("max-control-line", DEFAULT_MAX_CONTROL_LINE),
        MAX_RECONNECT("max-reconnect", DEFAULT_MAX_RECONNECT),
        RECONNECT_WAIT("reconnect-wait", DEFAULT_RECONNECT_WAIT),
        RECONNECT_JITTER("reconnect-jitter", DEFAULT_RECONNECT_JITTER),
        RECONNECT_JITTER_TLS("reconnect-jitter-tls", DEFAULT_RECONNECT_JITTER_TLS),
        CONNECTION_TIMEOUT("connection-timeout", DEFAULT_CONNECTION_TIMEOUT),
        PING_INTERVAL("ping-interval", DEFAULT_PING_INTERVAL),
        REQUEST_CLEANUP_INTERVAL("request-cleanup-interval", DEFAULT_REQUEST_CLEANUP_INTERVAL),
        MAX_PINGS_OUT("max-pings-out", DEFAULT_MAX_PINGS_OUT),
        RECONNECT_BUFFER_SIZE("reconnect-buffer-size", DEFAULT_RECONNECT_BUF_SIZE),
        USE_OLD_REQUEST_STYLE("use-old-request-style", false),
        BUFFER_SIZE("buffer-size", DEFAULT_BUFFER_SIZE),
        TRACK_ADVANCED_STATS("track-advanced-stats", false),
        TRACE_CONNECTION("trace-connection", false),
        NO_ECHO("no-echo", false),
        NO_HEADERS("no-headers", false),
        NO_NO_RESPONDERS("no-no-responders", false),
        UTF8_SUPPORT("utf8-support", false),
        INBOX_PREFIX("inbox-prefix", DEFAULT_INBOX_PREFIX),
        MAX_MESSAGES_IN_OUTGOING_QUEUE("max-messages-in-outgoing-queue", DEFAULT_MAX_MESSAGES_IN_OUTGOING_QUEUE),
        DISCARD_MESSAGES_WHEN_OUTGOING_QUEUE_FULL("discard-messages-when-outgoing-queue-full", DEFAULT_DISCARD_MESSAGES_WHEN_OUTGOING_QUEUE_FULL);


        private final String path;
        private final Object defaultValue;

        NatsConfigValue(String path, Object defaultValue) {
            this.path = path;
            this.defaultValue = defaultValue;
        }

        @Override
        public Object getDefaultValue() {
            return defaultValue;
        }

        public String getConfigPath() {
            return path;
        }
    }
}
