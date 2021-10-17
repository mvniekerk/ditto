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

import org.eclipse.ditto.base.model.common.ConditionChecker;
    import org.eclipse.ditto.connectivity.service.messaging.PublishTarget;

import java.util.Objects;

/**
 * A Nats target has a subject and an optional routing key.
 */
final class NatsTarget implements PublishTarget {

    private final String subject;

    static NatsTarget of(final String subject) {
        return new NatsTarget(subject);
    }

    private NatsTarget(final String subject) {
        this.subject = ConditionChecker.checkNotNull(subject, "subject");
    }

    static NatsTarget fromTargetAddress(final String targetAddress) {
        return new NatsTarget(targetAddress);
    }

    String getSubject() {
        return subject;
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final NatsTarget that = (NatsTarget) o;
        return Objects.equals(subject, that.subject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [" +
                "subject=" + subject +
                "]";
    }
}
