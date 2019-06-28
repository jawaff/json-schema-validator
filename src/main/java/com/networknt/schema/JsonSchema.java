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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * This is the core of json constraint implementation. It parses json constraint
 * file and generates JsonValidators. The class is thread safe, once it is
 * constructed, it can be used to validate multiple json data concurrently.
 */
public class JsonSchema extends BaseJsonValidator {
    private static final Pattern intPattern = Pattern.compile("^[0-9]+$");
    protected final Map<String, JsonValidator> validators;
    
    /**
     * This is the current uri of this schema. This uri could refer to the uri of this schema's file
     * or it could potentially be a uri that has been altered by an id. An 'id' is able to completely overwrite
     * the current uri or add onto it. This is necessary so that '$ref's are able to be relative to a
     * combination of the current schema file's uri and 'id' uris visible to this schema.
     * 
     * This can be null. If it is null, then the creation of relative uris will fail. However, an absolute
     * 'id' would still be able to specify an absolute uri.
     */
    private final URI currentUri;
    
    private JsonValidator requiredValidator = null;

    public JsonSchema(ValidationContext validationContext, URI baseUri, JsonNode schemaNode) {
        this(validationContext, "#", baseUri, schemaNode, null);
    }

    public JsonSchema(ValidationContext validationContext, String schemaPath, URI currentUri, JsonNode schemaNode,
               JsonSchema parent) {
        this(validationContext,  schemaPath, currentUri, schemaNode, parent, false);
    }

    public JsonSchema(ValidationContext validationContext, URI baseUri, JsonNode schemaNode, boolean suppressSubSchemaRetrieval) {
        this(validationContext, "#", baseUri, schemaNode, null, suppressSubSchemaRetrieval);
    }

    private JsonSchema(ValidationContext validationContext,  String schemaPath, URI currentUri, JsonNode schemaNode,
               JsonSchema parent, boolean suppressSubSchemaRetrieval) {
        super(schemaPath, schemaNode, parent, null, validationContext, suppressSubSchemaRetrieval);
        this.currentUri = this.combineCurrentUriWithIds(currentUri, schemaNode);
        this.validators = Collections.unmodifiableMap(this.read(schemaNode));
    }
    
    private URI combineCurrentUriWithIds(URI currentUri, JsonNode schemaNode) {
      final JsonNode idNode = schemaNode.get("id");
      if (idNode == null) {
        return currentUri;
      } else {
        try {
          return this.validationContext.getURIFactory().create(currentUri, idNode.asText());
        } catch (IllegalArgumentException e) {
          throw new JsonSchemaException(ValidationMessage.of(ValidatorTypeCode.ID.getValue(), ValidatorTypeCode.ID, idNode.asText(), currentUri.toString()));
        }
      }
    }
    
    public URI getCurrentUri()
    {
      return this.currentUri;
    }

    /**
     * Find the schema node for $ref attribute.
     *
     * @param ref String
     * @return JsonNode
     */
    public JsonNode getRefSchemaNode(String ref) {
        JsonSchema schema = findAncestor();
        JsonNode node = schema.getSchemaNode();

        if (ref.startsWith("#/")) {
            // handle local ref
            String[] keys = ref.substring(2).split("/");
            for (String key : keys) {
                try {
                    key = URLDecoder.decode(key, "utf-8");
                } catch (UnsupportedEncodingException e) {
                }
                Matcher matcher = intPattern.matcher(key);
                if (matcher.matches()) {
                    node = node.get(Integer.parseInt(key));
                } else {
                    node = node.get(key);
                }
                if (node == null){
                    JsonSchema subSchema = schema.fetchSubSchemaNode(validationContext);
                    if (subSchema != null) {
                        node = subSchema.getRefSchemaNode(ref);
                    }
                }
                if (node == null){
                    break;
                }
            }
        }
        return node;
    }

    public JsonSchema findAncestor() {
        JsonSchema ancestor = this;
        if (this.getParentSchema() != null) {
            ancestor = this.getParentSchema().findAncestor();
        }
        return ancestor;
    }

    private Map<String, JsonValidator> read(JsonNode schemaNode) {
        Map<String, JsonValidator> validators = new HashMap<String, JsonValidator>();
        Iterator<String> pnames = schemaNode.fieldNames();
        while (pnames.hasNext()) {
            String pname = pnames.next();
            JsonNode n = schemaNode.get(pname);

            JsonValidator validator = validationContext.newValidator(getSchemaPath(), pname, n, this);
            if (validator != null) {
                validators.put(getSchemaPath() + "/" + pname, validator);
                
                if(pname.equals("required"))
                	requiredValidator = validator;
            }

        }
        return validators;
    }

    @Override
    public CompletableFuture<Set<ValidationMessage>> validateNonblocking(JsonNode jsonNode, JsonNode rootNode, String at) {
        @SuppressWarnings("unchecked")
        final CompletableFuture<Set<ValidationMessage>>[] futures = (CompletableFuture<Set<ValidationMessage>>[]) this.validators.values()
                .stream()
                .map(validator -> validator.validateNonblocking(jsonNode, rootNode, at))
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

    @Override
    public String toString() {
        return "\"" + getSchemaPath() + "\" : " + getSchemaNode().toString();
    }

    public boolean hasRequiredValidator() {
    	return requiredValidator != null ? true : false;
    }
    
	public JsonValidator getRequiredValidator() {
		return requiredValidator;
	}

    @Override
    public CompletableFuture<Set<ValidationMessage>> validateNonblocking(JsonNode node, JsonNode rootNode, String at) {
        // TODO Auto-generated method stub
        return null;
    }
}
