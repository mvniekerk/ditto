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

import akka.actor.ActorRef;
import akka.actor.Status;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.service.messaging.BaseClientActor;

import javax.annotation.Nullable;
import java.util.concurrent.CompletionStage;

public class NatsClientActor extends BaseClientActor {

    /*
     * This constructor is called via reflection by the static method propsForTest.
     */
    @SuppressWarnings("unused")
    private NatsClientActor(final Connection connection,
                                @Nullable final ActorRef proxyActor,
                                final ActorRef connectionActor, final DittoHeaders dittoHeaders) {

        super(connection, proxyActor, connectionActor, dittoHeaders);
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(TestConnection testConnectionCommand) {
        return null;
    }

    @Override
    protected void cleanupResourcesForConnection() {

    }

    @Override
    protected void doConnectClient(Connection connection, @Nullable ActorRef origin) {

    }

    @Override
    protected void doDisconnectClient(Connection connection, @Nullable ActorRef origin, boolean shutdownAfterDisconnect) {

    }

    @Nullable
    @Override
    protected ActorRef getPublisherActor() {
        return null;
    }

    @Override
    protected CompletionStage<Status.Status> startPublisherActor() {
        return null;
    }
}
