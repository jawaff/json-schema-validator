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

import java.util.Collections;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public class MaxLengthValidator extends BaseAsyncJsonValidator implements JsonValidator {
    private static final Logger logger = LoggerFactory.getLogger(MaxLengthValidator.class);

    private int maxLength;

    public MaxLengthValidator(String schemaPath, JsonNode schemaNode, JsonSchema parentSchema, ValidationContext validationContext) {
        super(schemaPath, schemaNode, parentSchema, ValidatorTypeCode.MAX_LENGTH, validationContext);
        maxLength = Integer.MAX_VALUE;
        if (schemaNode != null && schemaNode.isIntegralNumber()) {
            maxLength = schemaNode.intValue();
        }

        parseErrorCode(getValidatorType().getErrorCodeKey());
    }

    public Set<ValidationMessage> validateBlocking(JsonNode node, JsonNode rootNode, String at) {
        debug(logger, node, rootNode, at);

        JsonType nodeType = TypeFactory.getValueNodeType(node);
        if (nodeType != JsonType.STRING) {
            // ignore no-string typs
            return Collections.emptySet();
        }
        if (node.textValue().codePointCount(0, node.textValue().length()) > maxLength) {
            return Collections.singleton(buildValidationMessage(at, "" + maxLength));
        }
        return Collections.emptySet();
    }

}
