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
package org.eclipse.ditto.protocoladapter.adaptables;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonParseException;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.JsonifiableMapper;
import org.eclipse.ditto.signals.commands.messages.MessageCommand;
import org.eclipse.ditto.signals.commands.messages.SendClaimMessage;
import org.eclipse.ditto.signals.commands.messages.SendFeatureMessage;
import org.eclipse.ditto.signals.commands.messages.SendThingMessage;

final class MessageCommandMappingStrategies extends AbstractMessageMappingStrategies<MessageCommand<?, ?>> {

    private static final MessageCommandMappingStrategies INSTANCE = new MessageCommandMappingStrategies();

    private MessageCommandMappingStrategies() {
        super(initMappingStrategies());
    }

    static MessageCommandMappingStrategies getInstance() {
        return INSTANCE;
    }

    private static Map<String, JsonifiableMapper<MessageCommand<?, ?>>> initMappingStrategies() {
        final Map<String, JsonifiableMapper<MessageCommand<?, ?>>> mappingStrategies = new HashMap<>();
        mappingStrategies.put(SendClaimMessage.TYPE,
                adaptable -> SendClaimMessage.of(thingIdFrom(adaptable),
                        messageFrom(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(SendThingMessage.TYPE,
                adaptable -> SendThingMessage.of(thingIdFrom(adaptable),
                        messageFrom(adaptable), dittoHeadersFrom(adaptable)));
        mappingStrategies.put(SendFeatureMessage.TYPE,
                adaptable -> SendFeatureMessage.of(thingIdFrom(adaptable), featureIdForMessageFrom(adaptable),
                        messageFrom(adaptable), dittoHeadersFrom(adaptable)));
        return mappingStrategies;
    }

    private static String featureIdForMessageFrom(final Adaptable adaptable) {
        return adaptable.getPayload().getPath()
                .getFeatureId()
                .orElseThrow(() -> JsonParseException.newBuilder().build());
    }

}
