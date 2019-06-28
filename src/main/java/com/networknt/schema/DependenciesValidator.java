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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public class DependenciesValidator extends BaseJsonValidator implements JsonValidator {
    private static final Logger logger = LoggerFactory.getLogger(DependenciesValidator.class);
    private final Map<String, List<String>> propertyDeps = new HashMap<String, List<String>>();
    private Map<String, JsonSchema> schemaDeps = new HashMap<String, JsonSchema>();

    public DependenciesValidator(String schemaPath, JsonNode schemaNode, JsonSchema parentSchema, ValidationContext validationContext) {

        super(schemaPath, schemaNode, parentSchema, ValidatorTypeCode.DEPENDENCIES, validationContext);

        for (Iterator<String> it = schemaNode.fieldNames(); it.hasNext(); ) {
            String pname = it.next();
            JsonNode pvalue = schemaNode.get(pname);
            if (pvalue.isArray()) {
                List<String> depsProps = propertyDeps.get(pname);
                if (depsProps == null) {
                    depsProps = new ArrayList<String>();
                    propertyDeps.put(pname, depsProps);
                }
                for (int i = 0; i < pvalue.size(); i++) {
                    depsProps.add(pvalue.get(i).asText());
                }
            } else if (pvalue.isObject()) {
                schemaDeps.put(pname, new JsonSchema(validationContext, pname, parentSchema.getCurrentUri(), pvalue, parentSchema));
            }
        }

        parseErrorCode(getValidatorType().getErrorCodeKey());
    }
    
    @Override
    public CompletableFuture<Set<ValidationMessage>> validateNonblocking(JsonNode node, JsonNode rootNode, String at) {
        debug(logger, node, rootNode, at);

        final Collection<CompletableFuture<Set<ValidationMessage>>> validateFutures = new ArrayList<>();

        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            String pname = it.next();
            List<String> deps = propertyDeps.get(pname);
            if (deps != null && !deps.isEmpty()) {
                for (String field : deps) {
                    if (node.get(field) == null) {
                        validateFutures.add(CompletableFuture.completedFuture(Collections.singleton(
                                buildValidationMessage(at, propertyDeps.toString()))));
                    }
                }
            }
            JsonSchema schema = schemaDeps.get(pname);
            if (schema != null) {
                validateFutures.add(schema.validateNonblocking(node, rootNode, at));
            }
        }

        return this.combineValidateFutures(validateFutures);
    }

}
