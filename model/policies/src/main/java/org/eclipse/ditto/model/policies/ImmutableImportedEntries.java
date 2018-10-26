/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.policies;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonArrayBuilder;
import org.eclipse.ditto.json.JsonFactory;

/**
 * Immutable implementation of {@link ImportedEntries}.
 */
@Immutable
final class ImmutableImportedEntries extends AbstractSet<String> implements ImportedEntries {

    private final Set<String> entryLabels;

    private ImmutableImportedEntries(final Set<String> entryLabels) {
        checkNotNull(entryLabels, "entry labels");
        this.entryLabels = Collections.unmodifiableSet(new HashSet<>(entryLabels));
    }

    /**
     * Returns a new empty set of permissions.
     *
     * @return a new empty set of permissions.
     */
    public static ImportedEntries none() {
        return new ImmutableImportedEntries(Collections.emptySet());
    }

    /**
     * Returns a new {@code ImportedEntries} object which is initialised with the given entry labels.
     *
     * @param entryLabel the mandatory entryLabel to initialise the result with.
     * @param furtherEntryLabels additional entryLabels to initialise the result with.
     * @return a new {@code ImportedEntries} object which is initialised with {@code entryLabel} and {@code
     * furtherEntryLabels}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ImportedEntries of(final String entryLabel, final String... furtherEntryLabels) {
        checkNotNull(entryLabel, "entryLabel");
        checkNotNull(furtherEntryLabels, "further entryLabels");

        final HashSet<String> permissions = new HashSet<>(1 + furtherEntryLabels.length);
        permissions.add(entryLabel);
        Collections.addAll(permissions, furtherEntryLabels);

        return new ImmutableImportedEntries(permissions);
    }

    /**
     * Returns a new {@code ImportedEntries} object which is initialised with the given entryLabels.
     *
     * @param entryLabels the entryLabels to initialise the result with.
     * @return a new {@code ImportedEntries} object which is initialised with {@code entryLabels}.
     * @throws NullPointerException if {@code entryLabels} is {@code null}.
     */
    public static ImportedEntries of(final Collection<String> entryLabels) {
        checkNotNull(entryLabels, "entryLabels");

        final HashSet<String> permissionSet = new HashSet<>();

        if (!entryLabels.isEmpty()) {
            permissionSet.addAll(entryLabels);
        }

        return new ImmutableImportedEntries(permissionSet);
    }

    @Override
    public boolean contains(final String entryLabel, final String... furtherEntryLabels) {
        checkNotNull(entryLabel, "permission whose presence is to be checked");
        checkNotNull(furtherEntryLabels, "further permissions whose presence are to be checked");

        final HashSet<String> permissionSet = new HashSet<>();
        permissionSet.add(entryLabel);
        permissionSet.addAll(Arrays.asList(furtherEntryLabels));

        return entryLabels.containsAll(permissionSet);
    }

    @Override
    public boolean contains(final ImportedEntries importedEntries) {
        checkNotNull(importedEntries, "permissions whose presence is to be checked");

        return this.entryLabels.containsAll(importedEntries);
    }

    @Override
    public int size() {
        return entryLabels.size();
    }

    @Override
    public JsonArray toJson() {
        final JsonArrayBuilder jsonArrayBuilder = JsonFactory.newArrayBuilder();
        entryLabels.forEach(jsonArrayBuilder::add);
        return jsonArrayBuilder.build();
    }

    // now all methods from Collection which should not be supported as we have an immutable data structure:

    @Nonnull
    @Override
    public Iterator<String> iterator() {
        return new Iterator<String>() {
            private final Iterator<String> i = entryLabels.iterator();

            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public String next() {
                return i.next();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void forEachRemaining(final Consumer<? super String> action) {
                // Use backing collection version
                i.forEachRemaining(action);
            }
        };
    }

    @Override
    public boolean add(final String e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(final Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(@Nonnull final Collection<? extends String> coll) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(@Nonnull final Collection<?> coll) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(@Nonnull final Collection<?> coll) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeIf(final Predicate<? super String> filter) {
        throw new UnsupportedOperationException();
    }

}
