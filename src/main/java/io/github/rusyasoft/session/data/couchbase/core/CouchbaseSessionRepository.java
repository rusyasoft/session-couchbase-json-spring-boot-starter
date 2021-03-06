package io.github.rusyasoft.session.data.couchbase.core;

import com.couchbase.client.java.document.json.JsonObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rusyasoft.session.data.couchbase.config.SessionCouchbaseProperties;
import io.github.rusyasoft.session.data.couchbase.data.PrincipalSessionsDocument;
import io.github.rusyasoft.session.data.couchbase.data.SessionDao;
import io.github.rusyasoft.session.data.couchbase.data.SessionDocument;
import org.slf4j.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.toIntExact;
import static java.time.Instant.now;
import static java.util.Collections.emptyMap;
import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.isTrue;
import static org.springframework.util.Assert.notNull;

public class CouchbaseSessionRepository implements FindByIndexNameSessionRepository<CouchbaseSession> {

    protected static final String GLOBAL_NAMESPACE = "global";
    protected static final int SESSION_DOCUMENT_EXPIRATION_DELAY_IN_SECONDS = 60;

    private static final Logger log = getLogger(CouchbaseSessionRepository.class);

    protected final SessionCouchbaseProperties sessionCouchbase;
    protected final SessionDao dao;
    protected final ObjectMapper mapper;
    //protected final Serializer serializer;
    protected final JsonSerializer serializer;

    protected final ApplicationEventPublisher eventPublisher;

    public CouchbaseSessionRepository(
            SessionCouchbaseProperties sessionCouchbase,
            SessionDao dao,
            ObjectMapper mapper,
            //Serializer serializer,
            JsonSerializer serializer,
            ApplicationEventPublisher eventPublisher
    ) {
        notNull(sessionCouchbase, "Missing session couchbase properties");
        notNull(dao, "Missing couchbase data access object");
        notNull(mapper, "Missing JSON object mapper");
        String namespace = sessionCouchbase.getApplicationNamespace();
        hasText(namespace, "Empty HTTP session namespace");
        isTrue(!namespace.equals(GLOBAL_NAMESPACE), "Forbidden HTTP session namespace '" + namespace + "'");
        notNull(serializer, "Missing object serializer");
        notNull(eventPublisher, "Missing application event publisher");
        this.sessionCouchbase = sessionCouchbase;
        this.dao = dao;
        this.mapper = mapper;
        this.serializer = serializer;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public CouchbaseSession createSession() {
        CouchbaseSession session = new CouchbaseSession(sessionCouchbase.getTimeout());
        SessionDocument sessionDocument = convertToDocument(session);
        dao.save(sessionDocument);
        dao.updateExpirationTime(session.getId(), getSessionDocumentExpiration());
        eventPublisher.publishEvent(new SessionCreatedEvent(this, session));

        log.debug("HTTP session with ID {} has been created", session.getId());

        return session;
    }

    @Override
    public void save(CouchbaseSession session) {
        if (session.isIdChanged()) {
            changeSessionId(session);
        }

        if (session.isGlobalPersistenceRequired()) {
            Map<String, Object> serializedGlobal = serializer.serializeSessionAttributes(session.getGlobalAttributesToUpdate());
            dao.updateSession(serializedGlobal, session.getGlobalAttributesToRemove(), GLOBAL_NAMESPACE, session.getId());
            log.debug("Global attributes of HTTP session with ID {} has been saved", session.getId());
            session.clearChangedGlobalAttributes();
        }

        if (session.isNamespacePersistenceRequired()) {
            Map<String, Object> serializedNamespace = serializer.serializeSessionAttributes(session.getNamespaceAttributesToUpdate());
            dao.updateSession(serializedNamespace, session.getNamespaceAttributesToRemove(), sessionCouchbase.getApplicationNamespace(), session.getId());
            log.debug("Application namespace attributes of HTTP session with ID {} has been saved", session.getId());
            session.clearChangedNamespaceAttributes();
        }

        if (isOperationOnPrincipalSessionsRequired(session)) {
            savePrincipalSession(session);
        }
        dao.updateExpirationTime(session.getId(), getSessionDocumentExpiration());
    }

    @Override
    public CouchbaseSession findById(String id) {

        //rustamchange// before code experiment
        JsonObject globalAttributesJsonObject = (JsonObject) dao.getObjectFromCouchbase(id, GLOBAL_NAMESPACE);
        JsonObject namespaceAttributesJsonObject = (JsonObject) dao.getObjectFromCouchbase(id, sessionCouchbase.getApplicationNamespace());
        ///////////////////////////////////////


        if (globalAttributesJsonObject == null && namespaceAttributesJsonObject == null) {
            log.debug("HTTP session with ID {} not found", id);
            return null;
        }

        notNull(globalAttributesJsonObject, "Invalid state of HTTP session persisted in couchbase. Missing global attributes.");

        if (namespaceAttributesJsonObject == null) {
            dao.insertNamespace(sessionCouchbase.getApplicationNamespace(), id);
        }

        Map<String, Object> deserializedGlobal = serializer.deserializeSessionAttributes(globalAttributesJsonObject);
        Map<String, Object> deserializedNamespace = serializer.deserializeSessionAttributes(namespaceAttributesJsonObject);

        CouchbaseSession session = new CouchbaseSession(id, deserializedGlobal, deserializedNamespace);
        if (session.isExpired()) {
            log.debug("HTTP session with ID {} has expired", id);
            deleteSession(session);
            eventPublisher.publishEvent(new SessionExpiredEvent(this, session));
            return null;
        }
        session.setLastAccessedTime(now());

        log.debug("HTTP session with ID {} has been found", id);

        return session;
    }

    @Override
    public void deleteById(String id) {
        CouchbaseSession session = findById(id);
        if (session == null) {
            return;
        }
        deleteSession(session);
        eventPublisher.publishEvent(new SessionDeletedEvent(this, session));
    }

    @Override
    public Map<String, CouchbaseSession> findByIndexNameAndIndexValue(String indexName, String indexValue) {
        if (!sessionCouchbase.getPrincipalSessions().isEnabled()) {
            throw new IllegalStateException("Cannot get principal HTTP sessions. Enable getting principal HTTP sessions using 'session-couchbase.principal-sessions.enabled' configuration property.");
        }
        if (!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
            return emptyMap();
        }
        PrincipalSessionsDocument sessionsDocument = dao.findByPrincipal(indexValue);
        if (sessionsDocument == null) {
            log.debug("Principals {} sessions not found", indexValue);
            return emptyMap();
        }
        Map<String, CouchbaseSession> sessionsById = new HashMap<>(sessionsDocument.getSessionIds().size());
        sessionsDocument.getSessionIds().forEach(sessionId -> {
            CouchbaseSession session = findById(sessionId);
            if (session != null) {
                sessionsById.put(sessionId, session);
            }
        });
        if (sessionsById.isEmpty()) {
            dao.delete(indexValue);
        }

        log.debug("Principals {} sessions with IDs {} have been found", indexValue, sessionsById.keySet());

        return sessionsById;
    }

    protected SessionDocument convertToDocument(CouchbaseSession session) {
        Map<String, Map<String, Object>> sessionData = new HashMap<>(2);
        sessionData.put(GLOBAL_NAMESPACE, session.getGlobalAttributes());
        sessionData.put(sessionCouchbase.getApplicationNamespace(), session.getNamespaceAttributes());
        return new SessionDocument(session.getId(), sessionData);
    }

    protected void changeSessionId(CouchbaseSession session) {
        SessionDocument oldDocument = dao.findById(session.getOldId());
        notNull(oldDocument, "Cannot change HTTP session ID, because session document with ID '" + session.getOldId() + "' does not exist in data storage");

        //rustamchange// removing the following line didn't help
        dao.delete(session.getOldId());

        log.debug("Old HTTP session with ID {} has been deleted after changing HTTP session ID", session.getOldId());
        SessionDocument newDocument = new SessionDocument(session.getId(), oldDocument.getData());
        dao.save(newDocument);
        log.debug("New HTTP session with ID {} has been saved after changing HTTP session ID", session.getId());

        //rustamchange// try to add changed information by trueing
        session.setIdChanged(false);
    }

    protected int getSessionDocumentExpiration() {
        return toIntExact(sessionCouchbase.getTimeout().plusSeconds(SESSION_DOCUMENT_EXPIRATION_DELAY_IN_SECONDS).getSeconds());
    }

    protected void savePrincipalSession(CouchbaseSession session) {
        String principal = session.getPrincipalAttribute();
        if (dao.exists(principal)) {
            dao.updatePutPrincipalSession(principal, session.getId());
        } else {
            List<String> sessionIds = new ArrayList<>(1);
            sessionIds.add(session.getId());
            PrincipalSessionsDocument sessionsDocument = new PrincipalSessionsDocument(principal, sessionIds);
            dao.save(sessionsDocument);
        }
        log.debug("Principals {} session with ID {} has been added", principal, session.getId());
        session.unsetPrincipalSessionsUpdateRequired();
    }

    protected void deleteSession(CouchbaseSession session) {
        if (isOperationOnPrincipalSessionsRequired(session)) {
            dao.updateRemovePrincipalSession(session.getPrincipalAttribute(), session.getId());
            log.debug("Principals {} session with ID {} has been removed", session.getPrincipalAttribute(), session.getId());
        }
        dao.delete(session.getId());
        log.debug("HTTP session with ID {} has been deleted", session.getId());
    }

    protected boolean isOperationOnPrincipalSessionsRequired(CouchbaseSession session) {
        return sessionCouchbase.getPrincipalSessions().isEnabled() && session.isPrincipalSessionsUpdateRequired();
    }
}
