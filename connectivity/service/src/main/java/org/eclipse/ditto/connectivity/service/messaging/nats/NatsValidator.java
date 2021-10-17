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

import akka.actor.ActorSystem;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.Resolvers;
import org.eclipse.ditto.connectivity.service.messaging.validation.AbstractProtocolValidator;
import org.eclipse.ditto.placeholders.PlaceholderFactory;

import javax.annotation.concurrent.Immutable;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import static org.eclipse.ditto.connectivity.api.placeholders.ConnectivityPlaceholders.*;
import static org.eclipse.ditto.connectivity.api.placeholders.ConnectivityPlaceholders.newFeaturePlaceholder;

@Immutable
public final class NatsValidator extends AbstractProtocolValidator {

    private static final Collection<String> ACCEPTED_SCHEMES = List.of("nats", "opentls", "tls");
    private static final Collection<String> SECURE_SCHEMES = List.of("opentls", "tls");

    /**
     * Creates a new instance
     * @return  A new instance
     */
    public static NatsValidator newInstance() {
        return new NatsValidator();
    }

    @Override
    protected void validateSource(final Source source, final DittoHeaders dittoHeaders,
                                  final Supplier<String> sourceDescription) {
        source.getEnforcement().ifPresent(enforcement -> {
            validateTemplate(enforcement.getInput(), dittoHeaders, PlaceholderFactory.newHeadersPlaceholder());
            enforcement.getFilters().forEach(filterTemplate ->
                    validateTemplate(filterTemplate, dittoHeaders, newThingPlaceholder(),
                            newPolicyPlaceholder(), newEntityPlaceholder(), newFeaturePlaceholder()));
        });
        validateHeaderMapping(source.getHeaderMapping(), dittoHeaders);
    }

    @Override
    protected void validateTarget(final Target target, final DittoHeaders dittoHeaders,
                                  final Supplier<String> targetDescription) {
        validateHeaderMapping(target.getHeaderMapping(), dittoHeaders);
        validateTemplate(target.getAddress(), dittoHeaders, Resolvers.getPlaceholders());
    }

    @Override
    public ConnectionType type() {
        return ConnectionType.NATS;
    }

    @Override
    public void validate(final Connection connection, final DittoHeaders dittoHeaders, final ActorSystem actorSystem,
                         final ConnectivityConfig connectivityConfig) {
        validateUriScheme(connection, dittoHeaders, ACCEPTED_SCHEMES, SECURE_SCHEMES, "NATS");
        validateSourceConfigs(connection, dittoHeaders);
        validateTargetConfigs(connection, dittoHeaders);
        validatePayloadMappings(connection, actorSystem, connectivityConfig, dittoHeaders);
    }

}
