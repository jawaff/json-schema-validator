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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public class MaximumValidator extends BaseAsyncJsonValidator implements JsonValidator {
    private static final Logger logger = LoggerFactory.getLogger(MaximumValidator.class);
    private static final String PROPERTY_EXCLUSIVE_MAXIMUM = "exclusiveMaximum";

    private boolean excludeEqual = false;

    private final ThresholdMixin typedMaximum;


    public MaximumValidator(String schemaPath, final JsonNode schemaNode, JsonSchema parentSchema, ValidationContext validationContext) {
        super(schemaPath, schemaNode, parentSchema, ValidatorTypeCode.MAXIMUM, validationContext);

        if (!schemaNode.isNumber()) {
            throw new JsonSchemaException("maximum value is not a number");
        }

        JsonNode exclusiveMaximumNode = getParentSchema().getSchemaNode().get(PROPERTY_EXCLUSIVE_MAXIMUM);
        if (exclusiveMaximumNode != null && exclusiveMaximumNode.isBoolean()) {
            excludeEqual = exclusiveMaximumNode.booleanValue();
        }

        parseErrorCode(getValidatorType().getErrorCodeKey());

        if (( schemaNode.isLong() || schemaNode.isInt() ) && (JsonType.INTEGER.toString().equals(getNodeFieldType()))) {
            // "integer", and within long range
            final long lm = schemaNode.asLong();
            typedMaximum = new ThresholdMixin() {
                @Override
                public boolean crossesThreshold(JsonNode node) {
                    long val = node.asLong();
                    if (node.isBigInteger()) {
                        //node.isBigInteger is not trustable, the type BigInteger doesn't mean it is a big number.
                        if (node.bigIntegerValue().compareTo(new BigInteger(String.valueOf(Long.MAX_VALUE))) > 0) {
                            return true;
                        }
                    }
                    return lm < val || (excludeEqual && lm <= val);
                }

                @Override
                public String thresholdValue() {
                    return String.valueOf(lm);
                }
            };
        } else {
            typedMaximum = new ThresholdMixin() {
                @Override
                public boolean crossesThreshold(JsonNode node) {
                    if(schemaNode.doubleValue() == Double.POSITIVE_INFINITY) {
                        return false;
                    }
                    final BigDecimal max = new BigDecimal(schemaNode.asText());
                    if(node.doubleValue() == Double.POSITIVE_INFINITY) {return true;}
                    BigDecimal value = new BigDecimal(node.asText());
                    return value.compareTo(max) > 0 || (excludeEqual && value.compareTo(max) == 0);
                }

                @Override
                public String thresholdValue() {
                    return schemaNode.asText();
                }
            };
        }
    }

    @Override
    public Set<ValidationMessage> validateBlocking(JsonNode node, JsonNode rootNode, String at) {
        debug(logger, node, rootNode, at);

        if (!TypeValidator.isNumber(node, config.isTypeLoose())) {
            // maximum only applies to numbers
            return Collections.emptySet();
        }

        if (typedMaximum.crossesThreshold(node)) {
            return Collections.singleton(buildValidationMessage(at, typedMaximum.thresholdValue()));
        }
        return Collections.emptySet();
    }
}