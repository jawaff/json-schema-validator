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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class handles immutable and thread safe configurations in order to support the asynchronous execution of the validators.
 */
public class SchemaValidatorsConfig {
    /**
     * when validate type, if TYPE_LOOSE = true, will try to convert string to different types to match the type defined in schema.
     */
    private final AtomicBoolean typeLoose = new AtomicBoolean(false);
    
    /**
     * if IS_MISSING_NODE_AS_ERROR = true, the validator will ignore the missing node.
     * if set to false, then the validator will report an error
     */
    private final AtomicBoolean missingNodeAsError = new AtomicBoolean(false);
    
    /**
     * if HAS_ELEMENT_VALIDATION_ERROR = true, the caller can decide, in conjunction with a missing node flag
     * on how to treat the error
     */
    private final AtomicBoolean elementValidationError = new AtomicBoolean(false);
    
    /**
     * Map of public, normally internet accessible schema URLs to alternate locations; this allows for offline
     * validation of schemas that refer to public URLs. This is merged with any mappings the {@link JsonSchemaFactory} 
     * may have been built with.
     */
    private final AtomicReference<Map<String, String>> uriMappings = new AtomicReference<>(Collections.emptyMap());
    
    public boolean isTypeLoose() {
        return typeLoose.get();
    }

    public void setTypeLoose(boolean typeLoose) {
        this.typeLoose.set(typeLoose);
    }

    public Map<String, String> getUriMappings() {
        return this.uriMappings.get();
    }

    public void setUriMappings(Map<String, String> uriMappings) {
        this.uriMappings.set(new HashMap<>(uriMappings));
    }
    
    public boolean isMissingNodeAsError() {
    	return missingNodeAsError.get();
    }
    
    public void setMissingNodeAsError(boolean missingNodeAsError) {
    	this.missingNodeAsError.set(missingNodeAsError);
    }
    
    public boolean hasElementValidationError() {
    	return elementValidationError.get();
    }
    
    public void setElementValidationError(boolean elementValidationError) {
    	this.elementValidationError.set(elementValidationError);
    }
    
    public SchemaValidatorsConfig() {
        loadDefaultConfig();
    }

    private void loadDefaultConfig() {
        this.typeLoose.set(true);
        this.uriMappings.set(Collections.emptyMap());
    }
}
