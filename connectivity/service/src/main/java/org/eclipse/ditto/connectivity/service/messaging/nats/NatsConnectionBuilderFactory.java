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
import io.nats.client.Options.Builder;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.messaging.monitoring.logs.ConnectionLogger;

/**
 * Creates a new Nats {@link Builder}
 */
public interface NatsConnectionBuilderFactory {
    /**
     * Creates a new Nats {@link Builder}
     * @param connection        The connection settings to build from
     * @param connectionLogger  The connection logger
     * @return                  A built {@link Builder}
     * @throws NullPointerException if any of these parameters are null
     */
    Builder createConnectionFactory(Connection connection, ConnectionLogger connectionLogger);
}
