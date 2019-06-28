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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public class AnyOfValidator extends BaseJsonValidator implements JsonValidator {
    private static final Logger logger = LoggerFactory.getLogger(RequiredValidator.class);

    private List<JsonSchema> schemas = new ArrayList<JsonSchema>();

    public AnyOfValidator(String schemaPath, JsonNode schemaNode, JsonSchema parentSchema, ValidationContext validationContext) {
        super(schemaPath, schemaNode, parentSchema, ValidatorTypeCode.ANY_OF, validationContext);
        int size = schemaNode.size();
        for (int i = 0; i < size; i++) {
            schemas.add(new JsonSchema(validationContext, getValidatorType().getValue(), parentSchema.getCurrentUri(), schemaNode.get(i), parentSchema));
        }
    }

    @Override
    public CompletableFuture<Set<ValidationMessage>> validateNonblocking(JsonNode node, JsonNode rootNode, String at) {
        debug(logger, node, rootNode, at);

        @SuppressWarnings("unchecked")
        final CompletableFuture<Set<ValidationMessage>>[] futures = (CompletableFuture<Set<ValidationMessage>>[]) 
                new CompletableFuture[this.schemas.size()];
        final String typeValidatorName = "anyOf/type";
        final List<String> expectedTypeList = new ArrayList<String>();

        for (int i = 0; i < this.schemas.size(); i++) {
            final JsonSchema schema = this.schemas.get(i);
            if (schema.validators.containsKey(typeValidatorName)) {
                TypeValidator typeValidator = ((TypeValidator) schema.validators.get(typeValidatorName));
                //If schema has type validator and node type doesn't match with schemaType then ignore it
                //For union type, it is must to call TypeValidator
                if (typeValidator.getSchemaType() != JsonType.UNION && !typeValidator.equalsToSchemaType(node)) {
                    expectedTypeList.add(typeValidator.getSchemaType().toString());
                    continue;
                }
            }
            
            // There's no use in validating if we're returning an error based on this type list.
            if (!expectedTypeList.isEmpty()) {
                futures[i] = schema.validateNonblocking(node, rootNode, at);
            }
        }
        if (!expectedTypeList.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.singleton(
                    buildValidationMessage(at, expectedTypeList.toArray(new String[expectedTypeList.size()]))));
        }
        
        return CompletableFuture.allOf(futures)
                .thenApply(nothing -> Arrays.stream(futures)
                        .map(future -> {
                            try {
                                return future.get();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException();
                            } catch (ExecutionException e) {
                                throw new IllegalStateException("Validation failed.", e);
                            }
                        }))
                .thenApply(errorSets -> errorSets.anyMatch(Set::isEmpty) 
                        ? Collections.emptySet() 
                        : errorSets.flatMap(Set::stream)
                                .collect(Collectors.toSet()));
    }
}
