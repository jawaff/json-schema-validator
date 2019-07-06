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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public class PropertiesValidator extends BaseJsonValidator implements JsonValidator {
    public static final String PROPERTY = "properties";
    private static final Logger logger = LoggerFactory.getLogger(PropertiesValidator.class);
    private Map<String, JsonSchema> schemas;

    public PropertiesValidator(String schemaPath, JsonNode schemaNode, JsonSchema parentSchema, ValidationContext validationContext) {
        super(schemaPath, schemaNode, parentSchema, ValidatorTypeCode.PROPERTIES, validationContext);
        schemas = new HashMap<String, JsonSchema>();
        for (Iterator<String> it = schemaNode.fieldNames(); it.hasNext(); ) {
            String pname = it.next();
            schemas.put(pname, new JsonSchema(validationContext, schemaPath + "/" + pname, parentSchema.getCurrentUri(), schemaNode.get(pname), parentSchema));
        }
    }

    public CompletableFuture<Set<ValidationMessage>> validateNonblocking(JsonNode node, JsonNode rootNode, String at) {
        debug(logger, node, rootNode, at);

        final Collection<CompletableFuture<Set<ValidationMessage>>> validateFutures = new ArrayList<>();

        for (Map.Entry<String, JsonSchema> entry : schemas.entrySet()) {
            JsonSchema propertySchema = entry.getValue();
            JsonNode propertyNode = node.get(entry.getKey());

            if (propertyNode != null) {
                validateFutures.add(propertySchema.validateNonblocking(propertyNode, rootNode, at + "." + entry.getKey())
                        .thenApply(errors -> {
                            // this was a regular validation error; mark it as such
                            if (!errors.isEmpty()) {
                                this.config.setElementValidationError(true);
                            }
                            return errors;
                        })); 
            } else {
            	// if a node could not be found, treat is as error/continue, depending on the SchemaValidatorsConfig
            	if(config.isMissingNodeAsError()) {
            		if(getParentSchema().hasRequiredValidator())
                		validateFutures.add(getParentSchema().getRequiredValidator().validateNonblocking(node,  rootNode, at));     
            		else 
                		validateFutures.add(CompletableFuture.completedFuture(
                		        Collections.singleton(buildValidationMessage(at, node.toString()))));
            	}
            }
        }

        return this.combineValidateFutures(validateFutures);
    }

}
