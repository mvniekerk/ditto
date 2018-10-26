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

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.base.json.Jsonifiable;

/**
 *
 */
public interface PolicyImport extends Jsonifiable.WithFieldSelectorAndPredicate<JsonField> {

    /**
     * Returns a new {@code PolicyImport} with the specified {@code resourceKey} and {@code effectedPermissions}.
     *
     * @param effectedImportedEntries the EffectedImportedEntries of the new PolicyImport to create.
     * @return the new {@code PolicyImport}.
     * @throws NullPointerException if any argument is {@code null}.
     */
    static PolicyImport newInstance(final String importedPolicyId,
            final EffectedImportedEntries effectedImportedEntries) {
        return PoliciesModelFactory.newPolicyImport(importedPolicyId, effectedImportedEntries);
    }

    /**
     * Subject is only available in JsonSchemaVersion V_2.
     *
     * @return the supported JsonSchemaVersions of Subject.
     */
    @Override
    default JsonSchemaVersion[] getSupportedSchemaVersions() {
        return new JsonSchemaVersion[]{JsonSchemaVersion.V_2};
    }

    /**
     * Returns the Policy ID of the Policy to import.
     *
     * @return the Policy ID of the Policy to import.
     */
    String getImportedPolicyId();

    /**
     * Returns the {@link EffectedImportedEntries} (containing included and excluded ones) for this PolicyImport.
     *
     * @return the effected imported entries.
     */
    EffectedImportedEntries getEffectedImportedEntries();

    /**
     * Returns all non hidden marked fields of this PolicyImport.
     *
     * @return a JSON object representation of this PolicyImport including only non hidden marked fields.
     */
    @Override
    default JsonObject toJson() {
        return toJson(FieldType.notHidden());
    }

    @Override
    default JsonObject toJson(final JsonSchemaVersion schemaVersion, final JsonFieldSelector fieldSelector) {
        return toJson(schemaVersion, FieldType.regularOrSpecial()).get(fieldSelector);
    }

    /**
     * An enumeration of the known {@link JsonField}s of a PolicyImport.
     */
    @Immutable
    final class JsonFields {

        /**
         * JSON field containing the {@link JsonSchemaVersion} of a PolicyImport.
         */
        public static final JsonFieldDefinition<Integer> SCHEMA_VERSION =
                JsonFactory.newIntFieldDefinition(JsonSchemaVersion.getJsonKey(), FieldType.SPECIAL, FieldType.HIDDEN,
                        JsonSchemaVersion.V_2);

        private JsonFields() {
            throw new AssertionError();
        }

    }

}
