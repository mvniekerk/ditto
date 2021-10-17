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
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import com.typesafe.config.Config;
import org.eclipse.ditto.base.model.common.CharsetDeterminer;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.WithEntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.connectivity.api.ExternalMessage;
import org.eclipse.ditto.connectivity.model.*;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.NatsConfig;
import org.eclipse.ditto.connectivity.service.messaging.BasePublisherActor;
import org.eclipse.ditto.connectivity.service.messaging.ConnectivityStatusResolver;
import org.eclipse.ditto.connectivity.service.messaging.SendResult;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import org.eclipse.ditto.internal.utils.config.InstanceIdentifierSupplier;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.placeholders.ExpressionResolver;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.stream.Collectors;

import static org.eclipse.ditto.connectivity.service.messaging.validation.ConnectionValidator.resolveConnectionIdPlaceholder;

/**
 * Responsible for publishing {@link ExternalMessage}s into Nats
 * <p>
 * To receive responses the {@code replyTo} header must be set. Responses are sent to the default exchange with the
 * {@code replyTo} header as routing key.
 * </p>
 * The {@code address} of the {@code targets} from the {@link Connection} are interpreted as follows:
 * <ul>
 * <li>no {@code targets} defined: signals are not published at all</li>
 * <li>{@code address="target/routingKey"}: signals are published to exchange {@code target} with routing key {@code
 * routingKey}</li>
 * </ul>
 */
public final class NatsPublisherActor extends BasePublisherActor<NatsTarget> {

    /**
     * The name of this Actor in the ActorSystem.
     */
    static final String ACTOR_NAME = "natsPublisherActor";

    private final ConcurrentSkipListMap<Long, OutstandingResponse> outstandingAcks = new ConcurrentSkipListMap<>();
    private final ConcurrentHashMap<NatsTarget, Queue<OutstandingResponse>> outstandingAcksByTarget =
            new ConcurrentHashMap<>();
    private ConfirmMode confirmMode = ConfirmMode.UNKNOWN;
    private final NatsConfig natsConfig;

    @SuppressWarnings("unused")
    private NatsPublisherActor(final Connection connection,
                               final String clientId,
                               final ConnectivityStatusResolver connectivityStatusResolver) {
        super(connection, clientId, connectivityStatusResolver);
        final Config config = getContext().getSystem().settings().config();
        this.natsConfig = DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(config))
                .getConnectionConfig()
                .getNatsConfig();
    }

    /**
     * Creates Akka configuration object {@link Props} for this {@code NatsPublisherActor}.
     *
     * @param connection the connection this publisher belongs to
     * @param clientId identifier of the client actor.
     * @param connectivityStatusResolver connectivity status resolver to resolve occurred exceptions to a connectivity
     * status.
     * @return the Akka configuration Props object.
     */
    static Props props(final Connection connection, final String clientId,
            final ConnectivityStatusResolver connectivityStatusResolver) {

        return Props.create(NatsPublisherActor.class, connection, clientId, connectivityStatusResolver);
    }

    @Override
    protected void preEnhancement(final ReceiveBuilder receiveBuilder) {
        receiveBuilder.build();
    }

    @Override
    protected void postEnhancement(final ReceiveBuilder receiveBuilder) {
        // noop
    }

    @Override
    protected NatsTarget toPublishTarget(final GenericTarget target) {
        return NatsTarget.of(target.getAddress());
    }

    @Override
    protected CompletionStage<SendResult> publishMessage(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final NatsTarget publishTarget,
            final ExternalMessage message,
            final int maxTotalMessageSize,
            final int ackSizeQuota) {

        if (channelActor == null) {
            return sendFailedFuture(signal, "No channel available, dropping response.");
        }

        final Map<String, String> messageHeaders = message.getHeaders();
        final String contentType = messageHeaders.get(ExternalMessage.CONTENT_TYPE_HEADER);
        final String correlationId = messageHeaders.get(DittoHeaderDefinition.CORRELATION_ID.getKey());

        final Map<String, Object> stringObjectMap = messageHeaders.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        final AMQP.BasicProperties basicProperties = new AMQP.BasicProperties.Builder()
                .contentType(contentType)
                .correlationId(correlationId)
                .headers(stringObjectMap)
                .build();

        final byte[] body;
        if (message.isTextMessage()) {
            body = message.getTextPayload()
                    .map(text -> text.getBytes(CharsetDeterminer.getInstance().apply(contentType)))
                    .orElseThrow(() -> new IllegalArgumentException("Failed to convert text to bytes."));
        } else {
            body = message.getBytePayload()
                    .map(ByteBuffer::array)
                    .orElse(new byte[]{});
        }

        final CompletableFuture<SendResult> resultFuture = new CompletableFuture<>();
        // create consumer outside channel message: need to check actor state and decide whether to handle acks.
        final LongConsumer nextPublishSeqNoConsumer =
                computeNextPublishSeqNoConsumer(signal, autoAckTarget, publishTarget, resultFuture);
        final ChannelMessage channelMessage = ChannelMessage.apply(channel -> {
            try {
                logger.withCorrelationId(message.getInternalHeaders())
                        .debug("Publishing to exchange <{}> and routing key <{}>: {}", publishTarget.getSubject(),
                                publishTarget.getRoutingKey(), basicProperties);
                nextPublishSeqNoConsumer.accept(channel.getNextPublishSeqNo());
                channel.basicPublish(publishTarget.getSubject(), publishTarget.getRoutingKey(), true, basicProperties,
                        body);
            } catch (final Exception e) {
                final String errorMessage = String.format("Failed to publish message to RabbitMQ: %s", e.getMessage());
                resultFuture.completeExceptionally(sendFailed(signal, errorMessage, e));
            }
            return null;
        }, false);

        channelActor.tell(channelMessage, getSelf());
        return resultFuture;
    }

    // This method is NOT thread-safe, but its returned consumer MUST be thread-safe.
    private LongConsumer computeNextPublishSeqNoConsumer(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final NatsTarget publishTarget,
            final CompletableFuture<SendResult> resultFuture) {

        if (confirmMode == ConfirmMode.ACTIVE) {
            return seqNo -> addOutstandingAck(seqNo, signal, resultFuture, autoAckTarget, publishTarget, pendingAckTTL);
        } else {
            final SendResult unsupportedAck = buildUnsupportedResponse(signal, autoAckTarget, connectionIdResolver);
            return seqNo -> resultFuture.complete(unsupportedAck);
        }
    }

    // Thread-safe
    private void addOutstandingAck(final Long seqNo,
            final Signal<?> signal,
            final CompletableFuture<SendResult> resultFuture,
            @Nullable final Target autoAckTarget,
            final NatsTarget publishTarget,
            final Duration timeoutDuration) {

        final OutstandingResponse outstandingAck = new OutstandingResponse(signal, autoAckTarget, resultFuture,
                connectionIdResolver);
        final SendResult timeoutResponse = buildResponseWithTimeout(signal, autoAckTarget, connectionIdResolver);

        // index the outstanding ack by delivery tag
        outstandingAcks.put(seqNo, outstandingAck);
        resultFuture.completeOnTimeout(timeoutResponse,
                timeoutDuration.toMillis(), TimeUnit.MILLISECONDS)
                // Only remove future from cache. Actual logging/reporting done elsewhere.
                .whenComplete((ack, error) -> outstandingAcks.remove(seqNo));

        // index the outstanding ack by publish target in order to generate negative acks on basic.return messages
        outstandingAcksByTarget.compute(publishTarget, (key, queue) -> {
            final Queue<OutstandingResponse> result = queue != null ? queue : new ConcurrentLinkedQueue<>();
            result.offer(outstandingAck);
            return result;
        });
        // maintain outstanding-acks-by-target. It need not be accurate because outstandingAcksByTarget is only used
        // on basic.return, which affects all messages published to 1 target but is not precise.
        resultFuture.whenComplete(
                (ignoredAck, ignoredError) -> outstandingAcksByTarget.computeIfPresent(publishTarget, (key, queue) -> {
                    queue.poll();
                    return queue.isEmpty() ? null : queue;
                }));
    }

    private void handleChannelStatus(final ChannelStatus channelStatus) {
        if (channelStatus.confirmationException != null) {
            logger.error(channelStatus.confirmationException, "Failed to enter confirm mode.");
            confirmMode = ConfirmMode.INACTIVE;
        } else {
            confirmMode = ConfirmMode.ACTIVE;
        }
        resourceStatusMap.putAll(channelStatus.targetStatus);
    }

    private static SendResult buildResponseWithTimeout(final Signal<?> signal, @Nullable final Target autoAckTarget,
            final ExpressionResolver connectionIdResolver) {
        return buildResponse(signal, autoAckTarget, HttpStatus.REQUEST_TIMEOUT,
                "No publisher confirm arrived.", connectionIdResolver);
    }

    private static SendResult buildUnsupportedResponse(final Signal<?> signal, @Nullable final Target autoAckTarget,
            final ExpressionResolver connectionIdResolver) {
        if (autoAckTarget != null && autoAckTarget.getIssuedAcknowledgementLabel().isPresent()) {
            // Not possible to recover without broker upgrade. Use status 400 to prevent redelivery at the source.
            return buildResponse(signal, autoAckTarget, HttpStatus.BAD_REQUEST,
                    "The external broker does not support RabbitMQ publisher confirms. " +
                            "Acknowledgement is not possible.", connectionIdResolver);
        } else {
            return buildSuccessResponse(signal, autoAckTarget, connectionIdResolver);
        }
    }

    private static SendResult buildSuccessResponse(final Signal<?> signal, @Nullable final Target autoAckTarget,
            final ExpressionResolver connectionIdResolver) {
        return buildResponse(signal, autoAckTarget, HttpStatus.OK, null, connectionIdResolver);
    }

    private static SendResult buildResponse(final Signal<?> signal,
            @Nullable final Target autoAckTarget,
            final HttpStatus httpStatus,
            @Nullable final String message,
            final ExpressionResolver connectionIdResolver) {

        final var autoAckLabel = Optional.ofNullable(autoAckTarget)
                .flatMap(Target::getIssuedAcknowledgementLabel)
                .flatMap(ackLabel -> resolveConnectionIdPlaceholder(connectionIdResolver, ackLabel));
        final Optional<EntityId> entityIdOptional =
                WithEntityId.getEntityIdOfType(EntityId.class, signal);
        final Acknowledgement issuedAck;
        if (autoAckLabel.isPresent() && entityIdOptional.isPresent()) {
            issuedAck = Acknowledgement.of(autoAckLabel.get(),
                    entityIdOptional.get(),
                    httpStatus,
                    signal.getDittoHeaders(),
                    message == null ? null : JsonValue.of(message));
        } else {
            issuedAck = null;
        }
        return new SendResult(issuedAck, signal.getDittoHeaders());
    }

    private static <T> CompletionStage<T> sendFailedFuture(final Signal<?> signal, final String errorMessage) {
        return CompletableFuture.failedFuture(sendFailed(signal, errorMessage, null));
    }

    private static MessageSendingFailedException sendFailed(final Signal<?> signal, final String errorMessage,
            @Nullable final Throwable cause) {
        return MessageSendingFailedException.newBuilder()
                .message(errorMessage)
                .dittoHeaders(signal.getDittoHeaders())
                .cause(cause)
                .build();
    }

    private static final class ChannelStatus {

        @Nullable private final IOException confirmationException;
        private final Map<Target, ResourceStatus> targetStatus;

        private ChannelStatus(@Nullable final IOException confirmationException,
                final Map<Target, ResourceStatus> targetStatus) {
            this.confirmationException = confirmationException;
            this.targetStatus = targetStatus;
        }
    }

    private static final class OutstandingResponse {

        private final Signal<?> signal;
        @Nullable private final Target autoAckTarget;
        private final CompletableFuture<SendResult> future;
        private final ExpressionResolver connectionIdResolver;

        private OutstandingResponse(final Signal<?> signal, @Nullable final Target autoAckTarget,
                final CompletableFuture<SendResult> future, final ExpressionResolver connectionIdResolver) {

            this.signal = signal;
            this.autoAckTarget = autoAckTarget;
            this.future = future;
            this.connectionIdResolver = connectionIdResolver;
        }
    }

    private enum ConfirmMode {
        UNKNOWN,
        ACTIVE,
        INACTIVE
    }

}
