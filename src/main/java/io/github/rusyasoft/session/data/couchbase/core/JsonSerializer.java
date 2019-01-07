package io.github.rusyasoft.session.data.couchbase.core;

import com.couchbase.client.java.document.json.JsonObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.util.ClassUtils.isPrimitiveOrWrapper;

public class JsonSerializer {

    protected static final String SERIALIZED_OBJECT_PREFIX = "_$object=";

    private static final Logger LOGGER = getLogger(JsonSerializer.class);

    @Autowired
    public CouchbaseJsonSerializer<Object> rustamSerializer;

    public Map<String, Object> serializeSessionAttributes(Map<String, Object> attributes) {
        if (attributes == null) {
            return null;
        }

        Map<String, Object> serialized = new HashMap<>(attributes.size());

        attributes.forEach((name, value) -> {
            Object attributeValue = null;
            try {
                attributeValue = rustamSerializer.getObjectMapper().writeValueAsString(value);
            } catch (JsonProcessingException e) {
                LOGGER.error("json serializeSessionAttributes: " + e.getMessage() + " e: " + e.toString()) ;;
            }
            serialized.put(name, attributeValue);
        });

        return serialized;
    }

    public Map<String, Object> deserializeSessionAttributes(JsonObject attributes) {
        if (attributes == null) {
            return null;
        }

        Map<String, Object> deserialized = new HashMap<>(attributes.size());

        try {
            Object [] nameObjects = attributes.getNames().toArray();
            if (nameObjects != null) {
                for (int i = 0; i < nameObjects.length; i++) {
                    Object obj = rustamSerializer.getObjectMapper().readValue(attributes.get(nameObjects[i].toString()).toString(), Object.class);
                    deserialized.put(nameObjects[i].toString(), obj);
                }
            }
        } catch (IOException e) {
            LOGGER.error("deserializeSessionAttributes, json serializeSessionAttributes: " + e.getMessage() + " e: " + e.toString());
        }



        return deserialized;
    }

    protected boolean isDeserializedObject(Object attributeValue) {
        return attributeValue != null && !isPrimitiveOrWrapper(attributeValue.getClass()) && !(attributeValue instanceof String);
    }

    protected boolean isSerializedObject(Object attributeValue) {
        return attributeValue instanceof String && startsWith(attributeValue.toString(), SERIALIZED_OBJECT_PREFIX);
    }
}