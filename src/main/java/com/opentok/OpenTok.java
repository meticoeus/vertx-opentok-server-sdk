/**
 * OpenTok Java SDK
 * Copyright (C) 2018 TokBox, Inc.
 * http://www.tokbox.com
 *
 * Licensed under The MIT License (MIT). See LICENSE file for more information.
 */
package com.opentok;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.opentok.exception.InvalidArgumentException;
import com.opentok.exception.OpenTokException;
import com.opentok.exception.RequestException;
import com.opentok.util.Crypto;
import com.opentok.util.OpenTokHttpClient;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
* Contains methods for creating OpenTok sessions, generating tokens, and working with archives.
* <p>
* To create a new OpenTok object, call the OpenTok constructor with your OpenTok API key
* and the API secret for your <a href="https://tokbox.com/account">TokBox account</a>. Do not publicly share
* your API secret. You will use it with the OpenTok constructor (only on your web
* server) to create OpenTok sessions.
* <p>
* Be sure to include the entire OpenTok server SDK on your web server.
*/
public class OpenTok {

    private int apiKey;
    private String apiSecret;
    protected OpenTokHttpClient client;
    protected Vertx vertx;
    static protected ObjectReader archiveReader = new ObjectMapper()
            .readerFor(Archive.class);
    static protected ObjectReader archiveListReader = new ObjectMapper()
            .readerFor(ArchiveList.class);
    static protected ObjectReader createdSessionReader = new ObjectMapper()
            .readerFor(CreatedSession[].class);

    /**
     * Creates an OpenTok object.
     *
     * @param apiKey Your OpenTok API key. (See your <a href="https://tokbox.com/account">TokBox account page</a>.)
     * @param apiSecret Your OpenTok API secret. (See your <a href="https://tokbox.com/account">TokBox account page</a>.)
     */
    public OpenTok(int apiKey, String apiSecret, Vertx vertx) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret.trim();
        this.vertx = vertx;
        this.client = new OpenTokHttpClient.Builder(apiKey, apiSecret, vertx).build();
    }

    private OpenTok(int apiKey, String apiSecret, Vertx vertx, OpenTokHttpClient httpClient) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret.trim();
        this.vertx = vertx;
        this.client = httpClient;
    }

    private <T> Handler<AsyncResult<String>> handleResponse(Handler<AsyncResult<T>> handler, ObjectReader reader) {
        return response -> {
            if (response.failed()) {
                handler.handle(Future.failedFuture(response.cause()));
            } else {
                String archives = response.result();
                try {
                    handler.handle(Future.succeededFuture(reader.readValue(archives)));
                } catch (Exception e) {
                    handler.handle(Future.failedFuture(new RequestException("Exception mapping json: " + e.getMessage(), e)));
                }
            }
        };
    }

    /**
     * Creates a token for connecting to an OpenTok session. In order to authenticate a user
     * connecting to an OpenTok session, the client passes a token when connecting to the session.
     * <p>
     * The following example shows how to obtain a token that has a role of "subscriber" and
     * that has a connection metadata string:
     * <p>
     * <pre>
     * import com.opentok.Role;
     * import com.opentok.TokenOptions;
     *
     * class Test {
     *     public static void main(String argv[]) throws OpenTokException {
     *         int API_KEY = 0; // Replace with your OpenTok API key (see https://tokbox.com/account).
     *         String API_SECRET = ""; // Replace with your OpenTok API secret.
     *         OpenTok sdk = new OpenTok(API_KEY, API_SECRET);
     *
     *         //Generate a basic session. Or you could use an existing session ID.
     *         String sessionId = System.out.println(sdk.createSession());
     *
     *         // Replace with meaningful metadata for the connection.
     *         String connectionMetadata = "username=Bob,userLevel=4";
     *
     *         // Use the Role value appropriate for the user.
     *         String role = Role.SUBSCRIBER;
     *
     *         // Generate a token:
     *         TokenOptions options = new TokenOptions.Buider().role(role).data(connectionMetadata).build();
     *         String token = sdk.generateToken(sessionId, options);
     *         System.out.println(token);
     *     }
     * }
     * </pre>
     * <p>
     * For testing, you can also generate tokens by logging in to your <a href="https://tokbox.com/account">TokBox account</a>.
     *
     * @param sessionId The session ID corresponding to the session to which the user will connect.
     *
     * @param tokenOptions This TokenOptions object defines options for the token.
     * These include the following:
     *
     * <ul>
     *    <li>The role of the token (subscriber, publisher, or moderator)</li>
     *    <li>The expiration time of the token</li>
     *    <li>Connection data describing the end-user</li>
     * </ul>
     *
     * @return The token string.
     */
    public String generateToken(String sessionId, TokenOptions tokenOptions) throws OpenTokException {
        List<String> sessionIdParts = null;
        if (sessionId == null || "".equals(sessionId)) {
            throw new InvalidArgumentException("Session not valid");
        }

        try {
            sessionIdParts = Crypto.decodeSessionId(sessionId);
        } catch (UnsupportedEncodingException e) {
            throw new InvalidArgumentException("Session ID was not valid");
        }
        if (!sessionIdParts.contains(Integer.toString(this.apiKey))) {
            throw new InvalidArgumentException("Session ID was not valid");
        }

        // NOTE: kind of wasteful of a Session instance
        Session session = new Session(sessionId, apiKey, apiSecret);
        return session.generateToken(tokenOptions);
    }

    /**
     * Creates a token for connecting to an OpenTok session, using the default settings. The default
     * settings are the following:
     *
     * <ul>
     *   <li>The token is assigned the role of publisher.</li>
     *   <li>The token expires 24 hours after it is created.</li>
     *   <li>The token includes no connection data.</li>
     * </ul>
     *
     * <p>
     * The following example shows how to generate a token that has the default settings:
     * <p>
     * <pre>
     * import com.opentok.OpenTok;
     *
     * class Test {
     *     public static void main(String argv[]) throws OpenTokException {
     *         int API_KEY = 0; // Replace with your OpenTok API key (see https://tokbox.com/account).
     *         String API_SECRET = ""; // Replace with your OpenTok API secret.
     *         OpenTok sdk = new OpenTok(API_KEY, API_SECRET);
     *
     *         //Generate a basic session. Or you could use an existing session ID.
     *         String sessionId = System.out.println(sdk.createSession().getSessionId());
     *
     *         String token = sdk.generateToken(sessionId);
     *         System.out.println(token);
     *     }
     * }
     * </pre>
     * @param sessionId The session ID corresponding to the session to which the user will connect.
     *
     * @return The token string.
     *
     * @see #generateToken(String, TokenOptions)
     */
    public String generateToken(String sessionId) throws OpenTokException {
        return generateToken(sessionId, new TokenOptions.Builder().build());
    }

    /**
     * Creates a new OpenTok session.
     * <p>
     * For example, when using the OpenTok.js library, use the session ID when calling the
     * <a href="http://tokbox.com/opentok/libraries/client/js/reference/OT.html#initSession">
     * OT.initSession()</a> method (to initialize an OpenTok session).
     * <p>
     * OpenTok sessions do not expire. However, authentication tokens do expire (see the
     * {@link #generateToken(String, TokenOptions)} method). Also note that sessions cannot
     * explicitly be destroyed.
     * <p>
     * A session ID string can be up to 255 characters long.
     * <p>
     * Calling this method results in an {@link com.opentok.exception.OpenTokException} in
     * the event of an error. Check the error message for details.
     * <p>
     * The following code creates a session that attempts to send streams directly between clients
     * (falling back to use the OpenTok TURN server to relay streams if the clients cannot connect):
     *
     * <pre>
     * import com.opentok.MediaMode;
     * import com.opentok.OpenTok;
     * import com.opentok.Session;
     * import com.opentok.SessionProperties;
     *
     * class Test {
     *     public static void main(String argv[]) throws OpenTokException {
     *         int API_KEY = 0; // Replace with your OpenTok API key.
     *         String API_SECRET = ""; // Replace with your OpenTok API secret.
     *         OpenTok sdk = new OpenTok(API_KEY, API_SECRET);
     *
     *         SessionProperties sp = new SessionProperties().Builder()
     *           .mediaMode(MediaMode.RELAYED).build();
     *
     *         Session session = sdk.createSession(sp);
     *         System.out.println(session.getSessionId());
     *     }
     * }
     * </pre>
     *
     * You can also create a session using the <a href="http://www.tokbox.com/opentok/api/#session_id_production">OpenTok
     * REST API</a> or or by logging in to your
     * <a href="https://tokbox.com/account">TokBox account</a>.
     *
     * @param properties This SessionProperties object defines options for the session.
     * These include the following:
     *
     * <ul>
     *    <li>Whether the session's streams will be transmitted directly between peers or
     *    using the OpenTok Media Router.</li>
     *
     *    <li>A location hint for the location of the OpenTok server to use for the session.</li>
     * </ul>
     *
     * Calls handler with: A Session object representing the new session. Call the <code>getSessionId()</code>
     * method of the Session object to get the session ID, which uniquely identifies the
     * session. You will use this session ID in the client SDKs to identify the session.
     */
    public void createSession(SessionProperties properties, Handler<AsyncResult<Session>> handler) {
        final SessionProperties _properties = properties != null ? properties : new SessionProperties.Builder().build();
        final Map<String, Collection<String>> params = _properties.toMap();
        this.client.createSession(params, response -> {
            if (response.failed()) {
                handler.handle(Future.failedFuture(response.cause()));
            } else {
                String result = response.result();
                try {
                    CreatedSession[] sessions = createdSessionReader.readValue(result);
                    // A bit ugly, but API response should include an array with one session
                    if (sessions.length != 1) {
                        handler.handle(Future.failedFuture(new OpenTokException(String.format("Unexpected number of sessions created %d", sessions.length))));
                    } else {
                        handler.handle(Future.succeededFuture(new Session(sessions[0].getId(), apiKey, apiSecret, _properties)));
                    }
                } catch (IOException e) {
                    handler.handle(Future.failedFuture(new OpenTokException("Cannot create session. Could not read the response: " + result, e)));
                }
            }
        });
    }

    /**
     * Creates an OpenTok session with the default settings:
     *
     * <p>
     * <ul>
     *     <li>The media mode is "relayed". The session will attempt to transmit streams
     *        directly between clients. If two clients cannot send and receive each others'
     *        streams, due to firewalls on the clients' networks, their streams will be
     *        relayed  using the OpenTok TURN Server.</li>
     *     <li>The session uses the first client connecting to determine the location of the
     *        OpenTok server to use.</li>
     * </ul>
     *
     * <p>
     * The following example creates a session that uses the default settings:
     *
     * <pre>
     * import com.opentok.OpenTok;
     * import com.opentok.SessionProperties;
     *
     * class Test {
     *     public static void main(String argv[]) throws OpenTokException {
     *         int API_KEY = 0; // Replace with your OpenTok API key.
     *         String API_SECRET = ""; // Replace with your OpenTok API secret.
     *         OpenTok sdk = new OpenTok(API_KEY, API_SECRET);
     *
     *         String sessionId = sdk.createSession();
     *         System.out.println(sessionId);
     *     }
     * }
     * </pre>
     *
     * Calls handler with: A Session object representing the new session. Call the <code>getSessionId()</code>
     * method of the Session object to get the session ID, which uniquely identifies the
     * session. You will use this session ID in the client SDKs to identify the session.
     *
     * @see #createSession(SessionProperties, Handler)
     */
    public void createSession(Handler<AsyncResult<Session>> handler) {
        createSession(null, handler);
    }

    /**
     * Gets an {@link Archive} object for the given archive ID.
     *
     * @param archiveId The archive ID.
     * Calls handler with: The {@link Archive} object.
     */
    public void getArchive(String archiveId, Handler<AsyncResult<Archive>> handler) {
        this.client.getArchive(archiveId, handleResponse(handler, archiveReader));
    }

    /**
     * Returns a List of {@link Archive} objects, representing archives that are both
     * both completed and in-progress, for your API key. This list is limited to 1000 archives
     * starting with the first archive recorded. For a specific range of archives, call
     * {@link #listArchives(int offset, int count, Handler handler)}.
     *
     * Calls handler with: A List of {@link Archive} objects.
     */
    public void listArchives(Handler<AsyncResult<ArchiveList>> handler) {
        listArchives(0, 1000, handler);
    }

    /**
     * Returns a List of {@link Archive} objects, representing archives that are both
     * both completed and in-progress, for your API key.
     *
     * @param offset The index offset of the first archive. 0 is offset of the most recently started
     * archive.
     * 1 is the offset of the archive that started prior to the most recent archive.
     * @param count The number of archives to be returned. The maximum number of archives returned
     * is 1000.
     * Calls handler with: A List of {@link Archive} objects.
     */
    public void listArchives(int offset, int count, Handler<AsyncResult<ArchiveList>> handler) {
        this.client.getArchives(offset, count, handleResponse(handler, archiveListReader));
    }

    /***
     * Returns a List of {@link Archive} objects, representing archives that are both both completed and in-progress,
     * for your API key.
     *
     * @param sessionId
     *            The sessionId for which archives should be retrieved.
     * Calls handler with: A List of {@link Archive} objects.
     */
    public void listArchives(String sessionId, Handler<AsyncResult<ArchiveList>> handler) {
        this.client.getArchives(sessionId, handleResponse(handler, archiveListReader));
    }

    /**
     * Starts archiving an OpenTok session. This version of the <code>startArchive()</code> method
     * lets you disable audio or video recording.
     * <p>
     * Clients must be actively connected to the OpenTok session for you to successfully start
     * recording an archive.
     * <p>
     * You can only record one archive at a time for a given session. You can only record archives
     * of sessions that use the OpenTok Media Router (sessions with the
     * <a href="http://tokbox.com/opentok/tutorials/create-session/#media-mode">media mode</a>
     * set to routed); you cannot archive sessions with the media mode set to relayed.
     * <p>
     * For more information on archiving, see the
     * <a href="https://tokbox.com/opentok/tutorials/archiving/">OpenTok archiving</a>
     * programming guide.
     *
     * @param sessionId The session ID of the OpenTok session to archive.
     *
     * @param properties This ArchiveProperties object defines options for the archive.
     *
     * Calls handler with: The Archive object. This object includes properties defining the archive, including the archive ID.
     */
    public void startArchive(String sessionId, ArchiveProperties properties, Handler<AsyncResult<Archive>> handler) {
        if (sessionId == null || "".equals(sessionId)) {
            handler.handle(Future.failedFuture(new InvalidArgumentException("Session not valid")));
        } else {
            // TODO: do validation on sessionId and name
            this.client.startArchive(sessionId, properties, handleResponse(handler, archiveReader));
        }
    }

    public void startArchive(String sessionId, Handler<AsyncResult<Archive>> handler) {
        startArchive(sessionId, new ArchiveProperties.Builder().build(), handler);
    }

    public void startArchive(String sessionId, String name, Handler<AsyncResult<Archive>> handler) {
        ArchiveProperties properties = new ArchiveProperties.Builder().name(name).build();
        startArchive(sessionId, properties, handler);
    }

    /**
     * Stops an OpenTok archive that is being recorded.
     * <p>
     * Archives automatically stop recording after 120 minutes or when all clients have disconnected
     * from the session being archived.
     *
     * @param archiveId The archive ID of the archive you want to stop recording.
     * Calls handler with: The Archive object corresponding to the archive being stopped.
     */
    public void stopArchive(String archiveId, Handler<AsyncResult<Archive>> handler) {
        this.client.stopArchive(archiveId, handleResponse(handler, archiveReader));
    }

    /**
     * Deletes an OpenTok archive.
     * <p>
     * You can only delete an archive which has a status of "available" or "uploaded". Deleting an
     * archive removes its record from the list of archives. For an "available" archive, it also
     * removes the archive file, making it unavailable for download.
     *
     * @param archiveId The archive ID of the archive you want to delete.
     */
    public void deleteArchive(String archiveId, Handler<AsyncResult<Void>> handler) {
        this.client.deleteArchive(archiveId, response -> {
            if (response.failed()) {
                handler.handle(Future.failedFuture(response.cause()));
            } else {
                try {
                    handler.handle(Future.succeededFuture());
                } catch (Exception e) {
                    handler.handle(Future.failedFuture(new RequestException("Exception mapping json: " + e.getMessage(), e)));
                }
            }
        });
    }

    public static class Builder {
        private int apiKey;
        private String apiSecret;
        private String apiUrl;
        private Vertx vertx;
        private HttpClientOptions httpClientOptions;

        public Builder(int apiKey, String apiSecret, Vertx vertx) {
            this.apiKey = apiKey;
            this.apiSecret = apiSecret;
            this.vertx = vertx;
        }

        public Builder apiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
            return this;
        }

        public Builder httpClientOptions(HttpClientOptions httpClientOptions) {
            this.httpClientOptions = httpClientOptions;
            return this;
        }

        public OpenTok build() {
            OpenTokHttpClient.Builder clientBuilder = new OpenTokHttpClient.Builder(apiKey, apiSecret, this.vertx);

            if (this.apiUrl != null) {
                clientBuilder.apiUrl(this.apiUrl);
            }

            if (this.httpClientOptions == null) {
                return new OpenTok(this.apiKey, this.apiSecret, this.vertx, clientBuilder.build());
            } else {
                clientBuilder.httpClientOptions(this.httpClientOptions);
                return new OpenTok(this.apiKey, this.apiSecret, this.vertx, clientBuilder.build());
            }
        }
    }

    public void close() {
        this.client.close();
    }
}
