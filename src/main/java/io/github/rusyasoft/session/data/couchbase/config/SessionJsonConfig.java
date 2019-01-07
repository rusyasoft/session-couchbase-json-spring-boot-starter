package io.github.rusyasoft.session.data.couchbase.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rusyasoft.session.data.couchbase.core.CouchbaseJsonSerializer;
import io.github.rusyasoft.session.data.couchbase.core.GenericJackson2JsonRedisSerializer;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.jackson2.SecurityJackson2Modules;

@Configuration
public class SessionJsonConfig implements BeanClassLoaderAware {
    private ClassLoader loader;

    @Bean
    public CouchbaseJsonSerializer<Object> springSessionDefaultRedisSerializer() {
        return new GenericJackson2JsonRedisSerializer(objectMapper());
    }

    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModules(SecurityJackson2Modules.getModules(this.loader));
        return mapper;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.loader = classLoader;
    }
}
