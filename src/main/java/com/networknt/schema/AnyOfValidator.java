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

        final Collection<CompletableFuture<Set<ValidationMessage>>> validateFutures = new ArrayList<>();
        final String typeValidatorName = "anyOf/type";
        final List<String> expectedTypeList = new ArrayList<String>();

        for (final JsonSchema schema : this.schemas) {
            if (schema.validators.containsKey(typeValidatorName)) {
                TypeValidator typeValidator = ((TypeValidator) schema.validators.get(typeValidatorName));
                
                // Checks to see if the schema type of the type validator is as expected.
                // If the validators all fail, then we will report that the validators' schema types 
                // don't match the given data. 
                if (typeValidator.getSchemaType() != JsonType.UNION && !typeValidator.equalsToSchemaType(node)) {
                    expectedTypeList.add(typeValidator.getSchemaType().toString());
                    continue;
                }
            }
            
            validateFutures.add(schema.validateNonblocking(node, rootNode, at));
        }
        return this.waitForValidateFutures(validateFutures)
                .thenApply(errorSets -> this.anySuccess(errorSets)
                        ? Collections.emptySet() 
                        // We'll report different errors for different situations.
                        : expectedTypeList.isEmpty()
                                ? this.combineErrors(errorSets) 
                                : this.createExpectedTypeErrors(expectedTypeList, at));
    }
    
    /**
     * Checks to see if any of the error sets are empty, which indicates
     * a success.
     * @param errorSets The error sets to check.
     * @return True if any of the error sets are empty.
     */
    private boolean anySuccess(final Collection<Set<ValidationMessage>> errorSets) {
        return errorSets.stream()
                .anyMatch(Set::isEmpty);
    }
    
    /**
     * Combines the given error sets into a single error set.
     * @param errorSets The error sets to combine.
     * @return The combined error set.
     */
    private Set<ValidationMessage> combineErrors(final Collection<Set<ValidationMessage>> errorSets) {
        return errorSets.stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }
    
    /**
     * Creates the errors for the expected schema types.
     * @param expectedTypeList The expected schema types.
     * @param at The current location in the data.
     * @return The created schema type errors.
     */
    private Set<ValidationMessage> createExpectedTypeErrors(final List<String> expectedTypeList, final String at) {
        return Collections.singleton(buildValidationMessage(
                at, 
                expectedTypeList.toArray(new String[expectedTypeList.size()])));
    }
}
