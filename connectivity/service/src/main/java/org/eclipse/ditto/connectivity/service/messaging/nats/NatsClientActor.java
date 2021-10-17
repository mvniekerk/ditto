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
import akka.actor.Status;
import akka.japi.pf.FSMStateFunctionBuilder;
import akka.pattern.Patterns;
import com.typesafe.config.Config;
import io.nats.client.Options;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.api.BaseClientState;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.TestConnection;
import org.eclipse.ditto.connectivity.service.config.ClientConfig;
import org.eclipse.ditto.connectivity.service.config.DittoConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.NatsConfig;
import org.eclipse.ditto.connectivity.service.messaging.BaseClientActor;
import org.eclipse.ditto.connectivity.service.messaging.BaseClientData;
import org.eclipse.ditto.connectivity.service.messaging.internal.ClientConnected;
import org.eclipse.ditto.connectivity.service.messaging.internal.ClientDisconnected;
import org.eclipse.ditto.connectivity.service.util.ConnectivityMdcEntryKey;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.config.DefaultScopedConfig;
import scala.Option;
import scala.concurrent.duration.FiniteDuration;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Actor which handles connection to Nats server.
 */
public final class NatsClientActor extends BaseClientActor {

    private static final String NATS_CONNECTION_ACTOR_NAME = "nats-connection";
    private static final String CONSUMER_CHANNEL = "consumer-channel";
    private static final String PUBLISHER_CHANNEL = "publisher-channel";
    private static final String CONSUMER_ACTOR_PREFIX = "consumer-";

    private final NatsConnectionBuilderFactory natsConnectionBuilderFactory;
    private final Map<String, String> consumedTagsToAddresses;
    private final Map<String, ActorRef> consumerByAddressWithIndex;

    private io.nats.client.Connection natsConnection;
    private ActorRef natsPublisherActor;

    private NatsConfig natsConfig;

    /*
     * This constructor is called via reflection by the static method propsForTest.
     */
    @SuppressWarnings("unused")
    private NatsClientActor(final Connection connection,
                            @Nullable final ActorRef proxyActor,
                            final ActorRef connectionActor, final DittoHeaders dittoHeaders) {

        super(connection, proxyActor, connectionActor, dittoHeaders);

        natsConnectionBuilderFactory =
                ConnectionBasedNatsConnectionBuilderFactory.getInstance(this::getSshTunnelState);
        consumedTagsToAddresses = new HashMap<>();
        consumerByAddressWithIndex = new HashMap<>();

        final Config config = getContext().getSystem().settings().config();
        this.natsConfig = DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(config))
                .getConnectionConfig()
                .getNatsConfig();
    }

    /*
     * This constructor is called via reflection by the static method props(Connection, ActorRef).
     */
    @SuppressWarnings("unused")
    private NatsClientActor(final Connection connection, @Nullable final ActorRef proxyActor,
                            final ActorRef connectionActor, final NatsConnectionBuilderFactory natsConnectionBuilderFactory,
                            final DittoHeaders dittoHeaders) {

        super(connection, proxyActor, connectionActor, dittoHeaders);
        this.natsConnectionBuilderFactory = natsConnectionBuilderFactory;
        consumedTagsToAddresses = new HashMap<>();
        consumerByAddressWithIndex = new HashMap<>();

        final Config config = getContext().getSystem().settings().config();
        this.natsConfig = DittoConnectivityConfig.of(DefaultScopedConfig.dittoScoped(config))
                .getConnectionConfig()
                .getNatsConfig();
    }

    /**
     * Creates Akka configuration object for this actor.
     *
     * @param connection the connection.
     * @param proxyActor the actor used to send signals into the ditto cluster.
     * @param connectionActor the connectionPersistenceActor which created this client.
     * @param dittoHeaders headers of the command that caused this actor to be created.
     * @return the Akka configuration Props object.
     */
    public static Props props(final Connection connection, @Nullable final ActorRef proxyActor,
            final ActorRef connectionActor, final DittoHeaders dittoHeaders) {

        return Props.create(NatsClientActor.class, validateConnection(connection), proxyActor,
                connectionActor, dittoHeaders);
    }

    @Override
    protected Set<Pattern> getExcludedAddressReportingChildNamePatterns() {
        final Set<Pattern> excludedChildNamePatterns = new HashSet<>(super.getExcludedAddressReportingChildNamePatterns());
        excludedChildNamePatterns.add(Pattern.compile(NATS_CONNECTION_ACTOR_NAME));
        return excludedChildNamePatterns;
    }


    private static Connection validateConnection(final Connection connection) {
        connection.getTargets()
                .stream()
                .map(Target::getAddress)
                .forEach(NatsTarget::fromTargetAddress);
        return connection;
    }

    @Override
    protected FSMStateFunctionBuilder<BaseClientState, BaseClientData> inConnectedState() {
        return super.inConnectedState().event(ClientConnected.class, BaseClientData.class, (event, data) -> {
            // when connection is lost, the library (ChannelActor) will automatically reconnect
            // without the state of this actor changing. But we will receive a new ClientConnected message
            // that we can use to bind our consumers to the channels.
            startConsumerActors(event);
            return stay();
        });
    }

    @Override
    protected CompletionStage<Status.Status> doTestConnection(final TestConnection testConnectionCommand) {
        final ClientConfig clientConfig = connectionContext.getConnectivityConfig().getClientConfig();

        // should be smaller than the global testing timeout to be able to send a response
        final Duration createChannelTimeout = clientConfig.getTestingTimeout().dividedBy(10L).multipliedBy(8L);
        final Duration internalReconnectTimeout = clientConfig.getTestingTimeout();

        // does explicitly not test the consumer so we won't consume any messages by accident.
        final DittoHeaders dittoHeaders = testConnectionCommand.getDittoHeaders();
        final String correlationId = dittoHeaders.getCorrelationId().orElse(null);
        return connect(testConnectionCommand.getConnection(), correlationId, createChannelTimeout,
                internalReconnectTimeout);
    }

    @Override
    protected void doConnectClient(final Connection connection, @Nullable final ActorRef origin) {
        final boolean consuming = isConsuming();
        final ActorRef self = getSelf();
        final ClientConfig clientConfig = connectionContext.getConnectivityConfig().getClientConfig();

        // #connect() will only create the channel for the producer, but not the consumer. We need to split the
        // connecting timeout to work for both channels before the global connecting timeout happens.
        // We choose about 45% of the global connecting timeout for this
        final Duration splitDuration = clientConfig.getConnectingMinTimeout().dividedBy(100L).multipliedBy(45L);
        final Duration internalReconnectTimeout = clientConfig.getConnectingMinTimeout();
        final CompletionStage<Status.Status> connect =
                connect(connection, null, splitDuration, internalReconnectTimeout);
        connect.thenAccept(status -> createConsumerChannelAndNotifySelf(status, consuming, self, splitDuration));
    }

    @Override
    protected void doDisconnectClient(final Connection connection, @Nullable final ActorRef origin,
            final boolean shutdownAfterDisconnect) {
        getSelf().tell(ClientDisconnected.of(origin, shutdownAfterDisconnect), origin);
    }

    @Override
    protected void allocateResourcesOnConnection(final ClientConnected clientConnected) {
        // nothing to do here
    }

    @Override
    protected CompletionStage<Status.Status> startConsumerActors(@Nullable final ClientConnected clientConnected) {
        if (clientConnected instanceof NatsConsumerChannelCreated) {
            final NatsConsumerChannelCreated natsConsumerChannelCreated = (NatsConsumerChannelCreated) clientConnected;
            startCommandConsumers(natsConsumerChannelCreated.getChannel());
        }
        return super.startConsumerActors(clientConnected);
    }

    @Override
    protected void cleanupResourcesForConnection() {
        logger.debug("cleaning up");
        stopCommandConsumers();
        stopChildActor(natsPublisherActor);
        try {
            natsConnection.close();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected ActorRef getPublisherActor() {
        return natsPublisherActor;
    }

    private Optional<Options.Builder> tryToCreateConnectionFactory(
            final NatsConnectionBuilderFactory builderFactory,
            final Connection connection) {

        try {
            return Optional.of(
                    builderFactory.createConnectionFactory(connection, connectionLogger)
            );
        } catch (final Throwable throwable) {
            // error creating factory; return early.)
            return Optional.empty();
        }
    }

    private CompletionStage<Status.Status> connect(final Connection connection,
            @Nullable final CharSequence correlationId,
            final Duration createChannelTimeout,
            final Duration internalReconnectTimeout) {

        final ThreadSafeDittoLoggingAdapter l = logger.withCorrelationId(correlationId)
                .withMdcEntry(ConnectivityMdcEntryKey.CONNECTION_ID, connection.getId());
        final CompletableFuture<Status.Status> future = new CompletableFuture<>();
        if (natsConnection == null) {

            final Optional<Options.Builder> connectionFactoryOpt =
                    tryToCreateConnectionFactory(natsConnectionBuilderFactory, connection);

            if (connectionFactoryOpt.isPresent()) {
                final Options.Builder connectionFactory = connectionFactoryOpt.get();

                final Props props = com.newmotion.akka.rabbitmq.ConnectionActor.props(connectionFactory,
                        FiniteDuration.apply(internalReconnectTimeout.getSeconds(), TimeUnit.SECONDS),
                        (rmqConnection, connectionActorRef) -> {
                            l.info("Established Nats connection: {}", rmqConnection);
                            return null;
                        });

                natsConnectionActor = startChildActorConflictFree(NATS_CONNECTION_ACTOR_NAME, props);
                final ActorRef currentPublisherActor = startNatsPublisherActor();
                natsPublisherActor = currentPublisherActor;

                // create publisher channel
                final CreateChannel createChannel = CreateChannel.apply(
                        ChannelActor.props((channel, channelActor) -> {
                            l.info("Did set up publisher channel: {}. Telling the publisher actor the new channel",
                                    channel);
                            // provide the new channel to the publisher after the channel was connected (also includes reconnects)
                            final ChannelCreated channelCreated = new ChannelCreated(channelActor);
                            currentPublisherActor.tell(channelCreated, channelActor);
                            return null;
                        }),
                        Option.apply(PUBLISHER_CHANNEL));

                Patterns.ask(natsConnectionActor, createChannel, createChannelTimeout).handle((reply, throwable) -> {
                    if (throwable != null) {
                        future.complete(new Status.Failure(throwable));
                    } else {
                        // waiting for "final RabbitMQExceptionHandler rabbitMQExceptionHandler" to get its chance to
                        // complete the future with an Exception before we report Status.Success right now
                        // so delay this by 1 second --
                        future.completeOnTimeout(new Status.Success("channel created"), 1, TimeUnit.SECONDS);
                    }
                    return null;
                });
            }
        } else {
            l.debug("Connection <{}> is already open.", connection.getId());
            future.complete(new Status.Success("already connected"));
        }
        return future;

    }


    private ActorRef startNatsPublisherActor() {
        stopChildActor(natsPublisherActor);
        final Props publisherProps = NatsPublisherActor.props(connection(), getDefaultClientId(),
                connectivityStatusResolver);
        return startChildActorConflictFree(NatsPublisherActor.ACTOR_NAME, publisherProps);
    }

    @Override
    protected CompletionStage<Status.Status> startPublisherActor() {
        return CompletableFuture.completedFuture(DONE);
    }

    private void stopCommandConsumers() {
        consumedTagsToAddresses.clear();
        consumerByAddressWithIndex.forEach((addressWithIndex, child) -> stopChildActor(child));
        consumerByAddressWithIndex.clear();
    }

    private void startCommandConsumers(final String channel) {
        logger.info("Starting to consume queues...");
        stopCommandConsumers();
        startConsumers(channel);
    }

    private void startConsumers(final String channel) {
        getSourcesOrEmptyList().forEach(source ->
                source.getAddresses().forEach(sourceAddress -> {
                    for (int i = 0; i < source.getConsumerCount(); i++) {
                        final String addressWithIndex = sourceAddress + "-" + i;
                        final ActorRef consumer = startChildActorConflictFree(
                                CONSUMER_ACTOR_PREFIX + addressWithIndex,
                                NatsConsumerActor.props(sourceAddress, getInboundMappingSink(), source,
                                        channel, connection(), connectivityStatusResolver));
                        consumerByAddressWithIndex.put(addressWithIndex, consumer);
                        try {
                            final String consumerTag = channel.basicConsume(sourceAddress, false,
                                    new NatsMessageConsumer(consumer, channel, sourceAddress));
                            logger.debug("Consuming queue <{}>, consumer tag is <{}>.", addressWithIndex, consumerTag);
                            consumedTagsToAddresses.put(consumerTag, addressWithIndex);
                        } catch (final IOException e) {
                            connectionLogger.failure("Failed to consume queue {0}: {1}", addressWithIndex,
                                    e.getMessage());
                            logger.warning("Failed to consume queue <{}>: <{}>", addressWithIndex, e.getMessage());
                        }
                    }
                })
        );
    }

    private static final class NatsConsumerChannelCreated implements ClientConnected {

        private final String channel;

        private NatsConsumerChannelCreated(final String channel) {
            this.channel = channel;
        }

        private String getChannel() {
            return channel;
        }

        @Override
        public Optional<ActorRef> getOrigin() {
            return Optional.empty();
        }

    }
}
