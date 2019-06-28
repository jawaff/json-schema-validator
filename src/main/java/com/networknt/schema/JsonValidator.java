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

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Standard json validator interface, implemented by all validators and JsonSchema.
 */
public interface JsonValidator {
    public static final String AT_ROOT = "$";    

    /**
     * Validate the given root JsonNode, starting at the root of the data path.
     *
     * @param rootNode The root node of the json being validated.
     * @return A list of ValidationMessage if there is any validation error, or an empty
     * list if there is no error.
     */
    Set<ValidationMessage> validate(JsonNode rootNode);

    /**
     * Validate the given root JsonNode, starting at the root of the data path.
     * 
     * Note: It is intended that the implementations delegate their processing to a thread
     * in the {@link ValidationContext}'s {@link ExecutorService}.
     *
     * @param rootNode The root node of the json being validated.
     * @return A list of ValidationMessage if there is any validation error, or an empty
     * list if there is no error.
     */
    CompletableFuture<Set<ValidationMessage>> validateNonblocking(JsonNode rootNode);

    /**
     * Validate the given JsonNode, the given node is the child node of the root node at given
     * data path.
     * 
     * Note: It is intended that the implementations delegate their processing to a thread
     * in the {@link ValidationContext}'s {@link ExecutorService}.
     *
     * @param node     The current node within the json being validated.
     * @param rootNode The root node of the json being validated.
     * @param at       The path of the current node within the json being validated.
     * @return A list of ValidationMessage if there is any validation error, or an empty
     * list if there is no error.
     */
    CompletableFuture<Set<ValidationMessage>> validateNonblocking(JsonNode node, JsonNode rootNode, String at);
}
