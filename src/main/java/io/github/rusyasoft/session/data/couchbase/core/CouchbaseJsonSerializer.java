package io.github.rusyasoft.session.data.couchbase.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.SerializationException;
import org.springframework.lang.Nullable;

public interface CouchbaseJsonSerializer<T> {
    /**
     * Serialize the given object to binary data.
     *
     * @param t object to serialize. Can be {@literal null}.
     * @return the equivalent binary data. Can be {@literal null}.
     */
    @Nullable
    byte[] serialize(@Nullable T t) throws SerializationException;


    @Nullable
    String serializeToString(@Nullable T t) throws SerializationException;

    /**
     * Deserialize an object from the given binary data.
     *
     * @param bytes object binary representation. Can be {@literal null}.
     * @return the equivalent object instance. Can be {@literal null}.
     */
    @Nullable
    T deserialize(@Nullable byte[] bytes) throws SerializationException;


    @Nullable
    T deserializeFromString(@Nullable String string) throws SerializationException;

    public ObjectMapper getObjectMapper();
}
