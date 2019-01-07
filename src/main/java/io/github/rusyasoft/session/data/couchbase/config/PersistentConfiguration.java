package io.github.rusyasoft.session.data.couchbase.config;

import com.couchbase.client.java.Bucket;
import io.github.rusyasoft.session.data.couchbase.data.PersistentDao;
import io.github.rusyasoft.session.data.couchbase.data.RetryLoggingListener;
import io.github.rusyasoft.session.data.couchbase.data.SessionDao;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.couchbase.config.AbstractCouchbaseConfiguration;
import org.springframework.data.couchbase.core.CouchbaseTemplate;
import org.springframework.data.couchbase.repository.config.EnableCouchbaseRepositories;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableCouchbaseRepositories
@EnableConfigurationProperties(SessionCouchbaseProperties.class)
@ConditionalOnProperty(name = "session-couchbase.in-memory.enabled", havingValue = "false", matchIfMissing = true)
public class PersistentConfiguration extends AbstractCouchbaseConfiguration {

    protected SessionCouchbaseProperties sessionCouchbase;

    protected String serverNodes;
    protected String bucketName;
    protected String bucketPassword;

    public PersistentConfiguration(SessionCouchbaseProperties sessionCouchbase) {
        this.sessionCouchbase = sessionCouchbase;
        this.serverNodes = sessionCouchbase.getServerNodes();
        this.bucketName = sessionCouchbase.getBucketName();
        this.bucketPassword = sessionCouchbase.getBucketPassword();
    }

    @Bean
    @ConditionalOnMissingBean
    public RetryLoggingListener retryLoggingListener() {
        return new RetryLoggingListener();
    }

    @Bean
    @ConditionalOnMissingBean
    public RetryTemplate sessionCouchbaseRetryTemplate(RetryLoggingListener listener) {
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>(1);
        retryableExceptions.put(Exception.class, true);
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(sessionCouchbase.getPersistent().getRetry().getMaxAttempts(), retryableExceptions);
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.registerListener(listener);
        return retryTemplate;
    }


    @Bean
    public Bucket sessionBucket() throws Exception {
        return couchbaseCluster().openBucket(this.bucketName, this.bucketPassword);
    }


    public CouchbaseTemplate sessionBucketTemplate() throws Exception {
        CouchbaseTemplate template = new CouchbaseTemplate(couchbaseClusterInfo(), sessionBucket(),
                mappingCouchbaseConverter(), translationService()
        );
        template.setDefaultConsistency(getDefaultConsistency());
        return template;
    }


    @Bean
    @ConditionalOnMissingBean
    public SessionDao sessionDao(@Qualifier("sessionCouchbaseRetryTemplate") RetryTemplate retryTemplate) {
        try {
            return new PersistentDao(sessionCouchbase, sessionBucketTemplate(), retryTemplate);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected List<String> getBootstrapHosts() {
        return Arrays.asList(this.serverNodes);
    }

    @Override
    protected String getBucketName() {
        return this.bucketName;
    }

    @Override
    protected String getBucketPassword() {
        return this.bucketPassword;
    }
}