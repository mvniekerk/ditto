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

import akka.NotUsed;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.stream.javadsl.Sink;
import io.nats.client.Message;
import io.nats.client.impl.NatsJetStreamMetaData;
import org.eclipse.ditto.base.model.common.CharsetDeterminer;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.connectivity.api.EnforcementFactoryFactory;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.api.ExternalMessageBuilder;
import org.eclipse.ditto.connectivity.api.ExternalMessageFactory;
import org.eclipse.ditto.connectivity.model.*;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.LegacyBaseConsumerActor;
import org.eclipse.ditto.connectivity.service.messaging.internal.RetrieveAddressStatus;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.tracing.DittoTracing;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.PreparedTrace;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.StartedTrace;
import org.eclipse.ditto.internal.utils.tracing.instruments.trace.Traces;
import org.eclipse.ditto.placeholders.PlaceholderFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


/**
 * Actor which receives message from an Nats source and forwards them to a {@code MessageMappingProcessorActor}.
 */
public final class NatsConsumerActor extends LegacyBaseConsumerActor {

    private static final String MESSAGE_ID_HEADER = "messageId";
    private static final String CONTENT_TYPE_APPLICATION_OCTET_STREAM = "application/octet-stream";

    @Nullable
    private final EnforcementFilterFactory<Map<String, String>, Signal<?>> headerEnforcementFilterFactory;
    private final PayloadMapping payloadMapping;
    private final String channel;

    @SuppressWarnings("unused")
    private NatsConsumerActor(final Connection connection, final String sourceAddress,
                              final Sink<Object, NotUsed> inboundMappingSink, final Source source, final String channel,
                              final ConnectivityStatusResolver connectivityStatusResolver) {
        super(connection, sourceAddress, inboundMappingSink, source, connectivityStatusResolver);

        headerEnforcementFilterFactory =
                source.getEnforcement()
                        .map(value ->
                                EnforcementFactoryFactory.newEnforcementFilterFactory(value,
                                        PlaceholderFactory.newHeadersPlaceholder()))
                        .orElse(null);
        this.payloadMapping = source.getPayloadMapping();
        this.channel = channel;
    }

    @Override
    protected ThreadSafeDittoLoggingAdapter log() {
        return logger;
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code NatsConsumerActor}.
     *
     * @param sourceAddress the source address.
     * @param inboundMappingSink the mapping sink where received messages are forwarded to
     * @param source the configured connection source for the consumer actor.
     * @param subject the Nats subject.
     * @param connection the connection.
     * @param connectivityStatusResolver connectivity status resolver to resolve occurred exceptions to a connectivity
     * status.
     * @return the Akka configuration Props object.
     */
    static Props props(final String sourceAddress,
            final Sink<Object, NotUsed> inboundMappingSink,
            final Source source,
            final String subject,
            final Connection connection,
            final ConnectivityStatusResolver connectivityStatusResolver) {

        return Props.create(NatsConsumerActor.class, connection, sourceAddress, inboundMappingSink, source,
                subject, connectivityStatusResolver);
    }

    @Override
    public Receive createReceive() {

        return ReceiveBuilder.create()
                .match(Message.class, this::handleMessage)
                .match(ResourceStatus.class, this::handleAddressStatus)
                .match(RetrieveAddressStatus.class, ram -> getSender().tell(getCurrentSourceStatus(), getSelf()))
                .matchAny(m -> {
                    logger.warning("Unknown message: {}", m);
                    unhandled(m);
                }).build();
    }

    private void handleMessage(final Message message) {
        Map<String, String> headers = extractHeadersFromMessage(message);
        final byte[] body = message.getData();

        StartedTrace trace = Traces.emptyStartedTrace();
        long streamSequence = Optional.ofNullable(message.metaData()).map(NatsJetStreamMetaData::streamSequence).orElse(0L);
        long consumerSequence = Optional.ofNullable(message.metaData()).map(NatsJetStreamMetaData::streamSequence).orElse(0L);

        try {
            @Nullable final String correlationId = message.getSID();
            if (logger.isDebugEnabled()) {
                logger.withCorrelationId(correlationId)
                        .debug("Received message from Nats ({}//{}): {}", message, headers,
                                new String(body, StandardCharsets.UTF_8));
            }

            final PreparedTrace preparedTrace =
                    DittoTracing.trace(DittoTracing.extractTraceContext(headers), "nats.consume");
            if (null != correlationId) {
                trace = preparedTrace.correlationId(correlationId).start();
            } else {
                trace = preparedTrace.start();
            }

            final ExternalMessageBuilder externalMessageBuilder =
                    ExternalMessageFactory.newExternalMessageBuilder(extractHeadersFromMessage(message));
            final String contentType = Optional.ofNullable(message.getHeaders())
                    .map(h -> h.getFirst(ExternalMessage.CONTENT_TYPE_HEADER)).orElse(CONTENT_TYPE_APPLICATION_OCTET_STREAM);
            final String text = new String(body, CharsetDeterminer.getInstance().apply(contentType));
            if (shouldBeInterpretedAsBytes(contentType)) {
                externalMessageBuilder.withBytes(body);
            } else {
                externalMessageBuilder.withTextAndBytes(text, body);
            }
            externalMessageBuilder.withAuthorizationContext(source.getAuthorizationContext());
            if (headerEnforcementFilterFactory != null) {
                externalMessageBuilder.withEnforcement(headerEnforcementFilterFactory.getFilter(headers));
            }
            externalMessageBuilder.withHeaderMapping(source.getHeaderMapping());
            externalMessageBuilder.withSourceAddress(sourceAddress);
            externalMessageBuilder.withPayloadMapping(payloadMapping);
            final ExternalMessage externalMessage = externalMessageBuilder.build();
            inboundMonitor.success(externalMessage);

            forwardToMapping(externalMessage,
                    () -> {
                        try {
                            message.ack();
                            inboundAcknowledgedMonitor.success(externalMessage,
                                    "Sending success acknowledgement: basic.ack for streamSequence={0} consumerSequence={0}", streamSequence, consumerSequence);
                        } catch (final IOException e) {
                            logger.error("Acknowledging delivery {} {} failed: {}", streamSequence, consumerSequence,
                                    e.getMessage());
                            inboundAcknowledgedMonitor.exception(e);
                        }
                    },
                    requeue -> {
                        try {
                            message.nak();
                            inboundAcknowledgedMonitor.exception("Sending negative acknowledgement: " +
                                            "basic.nack for streamSequence={0} consumerSequence={0} requeue={0}",
                                    streamSequence, consumerSequence, requeue);
                        } catch (final IOException e) {
                            logger.error("Delivery of basic.nack for deliveryTag={} failed: {}", message.getSubject(),
                                    e.getMessage());
                            inboundAcknowledgedMonitor.exception(e);
                        }
                    });
        } catch (final DittoRuntimeException e) {
            logger.warning("Processing delivery {} {} failed: {}", streamSequence, consumerSequence,
                    e.getMessage());
            // send response if headers were extracted successfully
            forwardToMapping(e.setDittoHeaders(DittoHeaders.of(headers)));
            inboundMonitor.failure(headers, e);

            trace.fail(e);
        } catch (final Exception e) {
            logger.warning("Processing delivery {} {} failed: {}", streamSequence, consumerSequence,
                    e.getMessage());
            inboundMonitor.exception(headers, e);
            trace.fail(e);
        } finally {
            trace.finish();
        }
    }

    private static boolean shouldBeInterpretedAsBytes(@Nullable final String contentType) {
        return contentType != null && contentType.startsWith(CONTENT_TYPE_APPLICATION_OCTET_STREAM);
    }

    private static Map<String, String> extractHeadersFromMessage(final Message message) {
        final Map<String, String> headers = Optional.ofNullable(message.getHeaders()).map(h ->
                h.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                a -> a.getValue().stream().findFirst().get()
                        ))
        ).orElse(new HashMap<>());

        // set headers specific to rmq messages
        if (message.getReplyTo() != null) {
            headers.put(ExternalMessage.REPLY_TO_HEADER, message.getReplyTo());
        }
        if (message.getSubject() != null) {
            headers.put(DittoHeaderDefinition.CORRELATION_ID.getKey(), message.getSubject());
        }

        return headers;
    }
}
