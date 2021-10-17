/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import com.typesafe.config.Config;
import org.eclipse.ditto.internal.utils.config.ConfigWithFallback;
import org.eclipse.ditto.internal.utils.config.ScopedConfig;

import javax.annotation.concurrent.Immutable;
import java.time.Duration;
import java.util.List;

/**
 * This class is the default implementation of {@link NatsConfig}.
 */
@Immutable
public class DefaultNatsConfig implements NatsConfig {

    private static final String CONFIG_PATH = "nats";

    private final List<String> servers;
    private final boolean noRandomize;
    private final String connectionName;
    private final boolean verbose;
    private final boolean pedantic;
    private final int maxControlLine;
    private final int maxReconnect;
    private final Duration reconnectWait;
    private final Duration reconnectJitter;
    private final Duration reconnectJitterTls;
    private final Duration connectionTimeout;
    private final Duration pingInterval;
    private final Duration requestCleanupInterval;
    private final int maxPingsOut;
    private final int reconnectBufferSize;
    private final boolean useOldRequestStyle;
    private final int bufferSize;
    private final boolean traceAdvancedStats;
    private final boolean traceConnection;
    private final boolean noEcho;
    private final boolean noHeaders;
    private final boolean noNoResponders;
    private final boolean utf8Response;
    private final String inboxPrefix;
    private final int maxMessagesInOutgoingQueue;
    private final boolean discardMessagesWhenOutgoingQueueFull;

    private DefaultNatsConfig(final ScopedConfig config) {
        this.servers = config.getStringList(NatsConfigValue.SERVERS.getConfigPath());
        this.noRandomize = config.getBoolean(NatsConfigValue.NO_RANDOMIZE.getConfigPath());
        this.connectionName = config.getString(NatsConfigValue.CONNECTION_NAME.getConfigPath());
        this.verbose = config.getBoolean(NatsConfigValue.VERBOSE.getConfigPath());
        this.pedantic = config.getBoolean(NatsConfigValue.PEDANTIC.getConfigPath());
        this.maxControlLine = config.getInt(NatsConfigValue.MAX_CONTROL_LINE.getConfigPath());
        this.maxReconnect = config.getInt(NatsConfigValue.MAX_RECONNECT.getConfigPath());
        this.reconnectWait = config.getDuration(NatsConfigValue.RECONNECT_WAIT.getConfigPath());
        this.reconnectJitter = config.getDuration(NatsConfigValue.RECONNECT_JITTER_TLS.getConfigPath());
        this.reconnectJitterTls = config.getDuration(NatsConfigValue.RECONNECT_JITTER_TLS.getConfigPath());
        this.connectionTimeout = config.getDuration(NatsConfigValue.CONNECTION_TIMEOUT.getConfigPath());
        this.pingInterval = config.getDuration(NatsConfigValue.PING_INTERVAL.getConfigPath());
        this.requestCleanupInterval = config.getDuration(NatsConfigValue.REQUEST_CLEANUP_INTERVAL.getConfigPath());
        this.maxPingsOut = config.getInt(NatsConfigValue.MAX_PINGS_OUT.getConfigPath());
        this.reconnectBufferSize = config.getInt(NatsConfigValue.RECONNECT_BUFFER_SIZE.getConfigPath());
        this.useOldRequestStyle = config.getBoolean(NatsConfigValue.USE_OLD_REQUEST_STYLE.getConfigPath());
        this.bufferSize = config.getInt(NatsConfigValue.BUFFER_SIZE.getConfigPath());
        this.traceAdvancedStats = config.getBoolean(NatsConfigValue.TRACK_ADVANCED_STATS.getConfigPath());
        this.traceConnection = config.getBoolean(NatsConfigValue.TRACE_CONNECTION.getConfigPath());
        this.noEcho = config.getBoolean(NatsConfigValue.NO_ECHO.getConfigPath());
        this.noHeaders = config.getBoolean(NatsConfigValue.NO_HEADERS.getConfigPath());
        this.noNoResponders = config.getBoolean(NatsConfigValue.NO_NO_RESPONDERS.getConfigPath());
        this.utf8Response = config.getBoolean(NatsConfigValue.UTF8_SUPPORT.getConfigPath());
        this.inboxPrefix = config.getString(NatsConfigValue.INBOX_PREFIX.getConfigPath());
        this.maxMessagesInOutgoingQueue = config.getInt(NatsConfigValue.MAX_MESSAGES_IN_OUTGOING_QUEUE.getConfigPath());
        this.discardMessagesWhenOutgoingQueueFull = config.getBoolean(NatsConfigValue.DISCARD_MESSAGES_WHEN_OUTGOING_QUEUE_FULL.getConfigPath());
    }

    /**
     * Returns an instance of {@code DefaultNats} based on the settings of the specified Config.
     *
     * @param config is supposed to provide the settings of the JavaScript mapping config at {@value #CONFIG_PATH}.
     * @return the instance.
     * @throws org.eclipse.ditto.internal.utils.config.DittoConfigError if {@code config} is invalid.
     */
    public static DefaultNatsConfig of(final Config config) {
        return new DefaultNatsConfig(ConfigWithFallback.newInstance(config, CONFIG_PATH, MqttConfig.MqttConfigValue.values()));
    }

    @Override
    public List<String> getServers() {
        return this.servers;
    }

    @Override
    public boolean isNoRandomize() {
        return this.noRandomize;
    }

    @Override
    public String getConnectionName() {
        return this.connectionName;
    }

    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    @Override
    public boolean isPedantic() {
        return this.pedantic;
    }

    @Override
    public int getMaxControlLine() {
        return this.maxControlLine;
    }

    @Override
    public int getMaxReconnect() {
        return this.maxReconnect;
    }

    @Override
    public Duration getReconnectWait() {
        return this.reconnectWait;
    }

    @Override
    public Duration getReconnectJitter() {
        return this.reconnectJitter;
    }

    @Override
    public Duration getReconnectJitterTls() {
        return this.reconnectJitterTls;
    }

    @Override
    public Duration getConnectionTimeout() {
        return this.connectionTimeout;
    }

    @Override
    public Duration getPingInterval() {
        return this.pingInterval;
    }

    @Override
    public Duration getRequestCleanupInterval() {
        return this.requestCleanupInterval;
    }

    @Override
    public int getMaxPingsOut() {
        return this.maxPingsOut;
    }

    @Override
    public int getReconnectBufferSize() {
        return this.reconnectBufferSize;
    }

    @Override
    public boolean isUseOldRequestStyle() {
        return this.useOldRequestStyle;
    }

    @Override
    public int getBufferSize() {
        return this.bufferSize;
    }

    @Override
    public boolean isTraceAdvancedStats() {
        return this.traceAdvancedStats;
    }

    @Override
    public boolean isTraceConnection() {
        return this.traceConnection;
    }

    @Override
    public boolean isNoEcho() {
        return this.noEcho;
    }

    @Override
    public boolean isNoHeaders() {
        return this.noHeaders;
    }

    @Override
    public boolean isNoNoResponders() {
        return this.noNoResponders;
    }

    @Override
    public boolean isUtf8Response() {
        return this.utf8Response;
    }

    @Override
    public String getInboxPrefix() {
        return this.inboxPrefix;
    }

    @Override
    public int getMaxMessagesInOutgoingQueue() {
        return this.maxMessagesInOutgoingQueue;
    }

    @Override
    public boolean isDiscardMessagesWhenOutgoingQueueFull() {
        return this.discardMessagesWhenOutgoingQueueFull;
    }
}
