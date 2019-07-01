/*
 * Copyright (c) 2016 Network New Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.networknt.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public class UnionTypeValidator extends BaseJsonValidator implements JsonValidator {
    private static final Logger logger = LoggerFactory.getLogger(UnionTypeValidator.class);

    private List<JsonValidator> schemas;
    private String error;

    public UnionTypeValidator(String schemaPath, JsonNode schemaNode, JsonSchema parentSchema, ValidationContext validationContext) {
        super(schemaPath, schemaNode, parentSchema, ValidatorTypeCode.UNION_TYPE, validationContext);
        schemas = new ArrayList<JsonValidator>();

        StringBuilder errorBuilder = new StringBuilder();

        String sep = "";
        errorBuilder.append('[');

        if (!schemaNode.isArray())
            throw new JsonSchemaException("Expected array for type property on Union Type Definition.");

        int i = 0;
        for (JsonNode n : schemaNode) {
            JsonType t = TypeFactory.getSchemaNodeType(n);
            errorBuilder.append(sep).append(t);
            sep = ", ";

            if (n.isObject())
                schemas.add(new JsonSchema(validationContext, ValidatorTypeCode.TYPE.getValue(), parentSchema.getCurrentUri(), n, parentSchema));
            else
                schemas.add(new TypeValidator(schemaPath + "/" + i, n, parentSchema, validationContext));

            i++;
        }

        errorBuilder.append(']');

        error = errorBuilder.toString();
    }

    public CompletableFuture<Set<ValidationMessage>> validateNonblocking(JsonNode node, JsonNode rootNode, String at) {
        debug(logger, node, rootNode, at);

        JsonType nodeType = TypeFactory.getValueNodeType(node);

        final Collection<CompletableFuture<Set<ValidationMessage>>> validateFutures = new ArrayList<>();
        for (final JsonValidator schema : this.schemas) {
            validateFutures.add(schema.validateNonblocking(node, rootNode, at));
        }
        
        return this.waitForValidateFutures(validateFutures)
                .thenApply(validateResults -> validateResults.stream()
                        .anyMatch(errors -> errors.isEmpty())
                                ? Collections.emptySet()
                                : Collections.singleton(buildValidationMessage(at, nodeType.toString(), error)));
    }

}
