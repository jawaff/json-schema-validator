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

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public abstract class BaseAsyncJsonValidator extends BaseJsonValidator implements JsonValidator {
    private static final Logger logger = LoggerFactory.getLogger(BaseAsyncJsonValidator.class);
    
    private final ValidationContext validationContext;
    
    protected BaseAsyncJsonValidator(
            final String schemaPath, 
            final JsonNode schemaNode, 
            final JsonSchema parentSchema, 
            final ValidatorTypeCode validatorType, 
            final ValidationContext validationContext) {
        super(schemaPath, schemaNode, parentSchema, validatorType, validationContext);
        this.validationContext = validationContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompletableFuture<Set<ValidationMessage>> validateNonblocking(
            final JsonNode node, final JsonNode rootNode, final String at) {
        return CompletableFuture.supplyAsync(
                () -> validateBlocking(node, rootNode, at), 
                this.validationContext.getExecutor());
    }
    

    /**
     * Validate the given JsonNode, the given node is the child node of the root node at given
     * data path. This validate overload is asynchronous. The {@link ValidationContext}s {@link ExecutorService}
     * handles the execution of this validate.
     *
     * @param node     The current node within the json being validated.
     * @param rootNode The root node of the json being validated.
     * @param at       The path of the current node within the json being validated.
     * @return A list of ValidationMessage if there is any validation error, or an empty
     * list if there is no error.
     */
    public abstract Set<ValidationMessage> validateBlocking(
            final JsonNode node, final JsonNode rootNode, final String at);
}
