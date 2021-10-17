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
import java.util.List;

/**
 * Provides the configuration to connect to a Nats.io instance or cluster
 */
@Immutable
public interface NatsConfig {

    List<String> servers();

    /**
     * An enumeration of the known config path expressions and their associated default values for
     * {@code NatsConfig}.
     */
    enum NatsConfigValue implements KnownConfigValue {
        SERVERS("servers", List.of("nats://localhost:4222"));
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
