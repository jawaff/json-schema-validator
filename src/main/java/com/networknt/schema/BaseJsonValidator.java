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

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;

public abstract class BaseJsonValidator implements JsonValidator {
    private String schemaPath;
    private JsonNode schemaNode;
    private JsonSchema parentSchema;
    private boolean suppressSubSchemaRetrieval;
    private ValidatorTypeCode validatorType;
    protected ValidationContext validationContext;
    private ErrorMessageType errorMessageType;
    /**
     * SchemaValidatorsConfig can only get and set in validationContext
     */
    protected SchemaValidatorsConfig config;

    
    protected BaseJsonValidator(String schemaPath, JsonNode schemaNode, JsonSchema parentSchema,
                             ValidatorTypeCode validatorType, ValidationContext validationContext) {
    	this(schemaPath, schemaNode, parentSchema, validatorType, validationContext, false);
    	this.config = validationContext.getConfig() == null ? new SchemaValidatorsConfig() : validationContext.getConfig();
    }

    protected BaseJsonValidator(String schemaPath, JsonNode schemaNode, JsonSchema parentSchema,
                             ValidatorTypeCode validatorType, ValidationContext validationContext, boolean suppressSubSchemaRetrieval) {
        this.errorMessageType = validatorType;
        this.schemaPath = schemaPath;
        this.schemaNode = schemaNode;
        this.parentSchema = parentSchema;
        this.validatorType = validatorType;
        this.suppressSubSchemaRetrieval = suppressSubSchemaRetrieval;
        this.validationContext = validationContext;
    }

    protected String getSchemaPath() {
        return schemaPath;
    }

    public JsonNode getSchemaNode() {
        return schemaNode;
    }

    protected JsonSchema getParentSchema() {
        return parentSchema;
    }
    
    protected JsonSchema fetchSubSchemaNode(ValidationContext validationContext) {
        return suppressSubSchemaRetrieval ? null : obtainSubSchemaNode(schemaNode, validationContext);
    }

    
    private static JsonSchema obtainSubSchemaNode(final JsonNode schemaNode, final ValidationContext validationContext){
        final JsonNode node = schemaNode.get("id");
        if(node == null) return null;
    	if(node.equals(schemaNode.get("$schema"))) return null;

        final String text = node.textValue();
        if (text == null) {
            return null;
        }
        else {
        	final URI uri;
            try {
                uri = validationContext.getURIFactory().create(node.textValue());
            } catch (IllegalArgumentException e) {
                return null;
            }
            return validationContext.getJsonSchemaFactory().getSchema(uri, validationContext.getConfig());
        }
    }

    @Override
    public Set<ValidationMessage> validate(JsonNode node) {
        try {
            return validateNonblocking(node, node, AT_ROOT).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException();
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed validation", e);
        }
    }

    @Override
    public CompletableFuture<Set<ValidationMessage>> validateNonblocking(JsonNode node) {
        return validateNonblocking(node, node, AT_ROOT);
    }

    /**
     * The given futures will be waited on. After they all complete, the results will be combined and returned.
     * @param validateFutures The validate futures to wait for.
     * @return A future for the merged results of the given futures.
     */
    protected CompletableFuture<Set<ValidationMessage>> combineValidateFutures(
            final Collection<CompletableFuture<Set<ValidationMessage>>> validateFutures) {
        @SuppressWarnings("unchecked")
        final CompletableFuture<Set<ValidationMessage>>[] futures = (CompletableFuture<Set<ValidationMessage>>[]) validateFutures.stream()
                .toArray(CompletableFuture[]::new);
        
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
                        })
                        .flatMap(Set::stream)
                        .collect(Collectors.toSet()));
    }

    protected boolean equals(double n1, double n2) {
        return Math.abs(n1 - n2) < 1e-12;
    }

    protected boolean greaterThan(double n1, double n2) {
        return n1 - n2 > 1e-12;
    }

    protected boolean lessThan(double n1, double n2) {
        return n1 - n2 < -1e-12;
    }

    protected void parseErrorCode(String errorCodeKey) {
        JsonNode errorCodeNode = getParentSchema().getSchemaNode().get(errorCodeKey);
        if (errorCodeNode != null && errorCodeNode.isTextual()) {
            String errorCodeText = errorCodeNode.asText();
            if (StringUtils.isNotBlank(errorCodeText)) {
                errorMessageType = CustomErrorMessageType.of(errorCodeText);
            }
        }
    }


    protected ValidationMessage buildValidationMessage(String at, String... arguments) {
        return ValidationMessage.of(getValidatorType().getValue(), errorMessageType, at, arguments);
    }

    protected void debug(Logger logger, JsonNode node, JsonNode rootNode, String at) {
        if (logger.isDebugEnabled()) {
            logger.debug("validate( " + node + ", " + rootNode + ", " + at + ")");
        }
    }

    protected ValidatorTypeCode getValidatorType() {
        return validatorType;
    }

    protected String getNodeFieldType() {
        JsonNode typeField = this.getParentSchema().getSchemaNode().get("type");
        if (typeField != null) {
            return typeField.asText();
        }
        return null;
    }
}
