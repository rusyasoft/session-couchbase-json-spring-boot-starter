package io.github.rusyasoft.session.data.couchbase.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rusyasoft.session.data.couchbase.core.CouchbaseSessionRepository;
import io.github.rusyasoft.session.data.couchbase.core.JsonSerializer;
import io.github.rusyasoft.session.data.couchbase.data.SessionDao;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;

@Configuration
@EnableSpringHttpSession
@EnableConfigurationProperties(SessionCouchbaseProperties.class)
public class SessionCouchbaseAutoConfiguration {

    protected SessionCouchbaseProperties sessionCouchbase;

    public SessionCouchbaseAutoConfiguration(SessionCouchbaseProperties sessionCouchbase) {
        this.sessionCouchbase = sessionCouchbase;
    }

    @Bean
    @ConditionalOnMissingBean
    public JsonSerializer serializer() {
        return new JsonSerializer();
    }

    @Bean
    @ConditionalOnMissingBean
    public SessionRepository sessionRepository(SessionDao dao, ObjectMapper mapper, JsonSerializer serializer, ApplicationEventPublisher eventPublisher) {
        return new CouchbaseSessionRepository(sessionCouchbase, dao, mapper, serializer, eventPublisher);
    }
}