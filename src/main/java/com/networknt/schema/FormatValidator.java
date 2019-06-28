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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public class FormatValidator extends BaseAsyncJsonValidator implements JsonValidator {
    private static final Logger logger = LoggerFactory.getLogger(FormatValidator.class);

    private final Format format;

    public FormatValidator(String schemaPath, JsonNode schemaNode, JsonSchema parentSchema, ValidationContext validationContext, Format format) {
        super(schemaPath, schemaNode, parentSchema, ValidatorTypeCode.FORMAT, validationContext);
        this.format = format;
        parseErrorCode(getValidatorType().getErrorCodeKey());
    }
    
    @Override
    public Set<ValidationMessage> validateBlocking(JsonNode node, JsonNode rootNode, String at) {
        debug(logger, node, rootNode, at);

        JsonType nodeType = TypeFactory.getValueNodeType(node);
        if (nodeType != JsonType.STRING) {
            return Collections.emptySet();
        }

        Set<ValidationMessage> errors = new LinkedHashSet<ValidationMessage>();
        if (format != null) {
            try {
                if (!format.matches(node.textValue())) {
                    errors.add(buildValidationMessage(at, format.getName(), format.getErrorMessageDescription()));
                }
            } catch (PatternSyntaxException pse) {
                // String is considered valid if pattern is invalid
                logger.error("Failed to apply pattern on " + at + ": Invalid RE syntax [" + format.getName() + "]", pse);
            }
        }

        return Collections.unmodifiableSet(errors);
    }
}
