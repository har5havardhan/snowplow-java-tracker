/*
 * Copyright (c) 2015 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.snowplow.tracker;

// Java
import java.util.*;

// Google
import com.google.common.base.Preconditions;

// This library
import com.snowplowanalytics.snowplow.tracker.constants.Constants;
import com.snowplowanalytics.snowplow.tracker.constants.Parameter;
import com.snowplowanalytics.snowplow.tracker.emitter.Emitter;
import com.snowplowanalytics.snowplow.tracker.events.EcommerceTransaction;
import com.snowplowanalytics.snowplow.tracker.events.EcommerceTransactionItem;
import com.snowplowanalytics.snowplow.tracker.events.PageView;
import com.snowplowanalytics.snowplow.tracker.events.ScreenView;
import com.snowplowanalytics.snowplow.tracker.events.Structured;
import com.snowplowanalytics.snowplow.tracker.events.Timing;
import com.snowplowanalytics.snowplow.tracker.events.Unstructured;
import com.snowplowanalytics.snowplow.tracker.payload.SelfDescribingJson;
import com.snowplowanalytics.snowplow.tracker.payload.TrackerPayload;

public class Tracker {

    private final String trackerVersion = Version.TRACKER;
    private Emitter emitter;
    private Subject subject;
    private String appId;
    private String namespace;
    private DevicePlatform platform;
    private boolean base64Encoded;

    /**
     * Creates a new Snowplow Tracker.
     *
     * @param builder The builder that constructs a tracker
     */
    private Tracker(TrackerBuilder builder) {

        // Precondition checks
        Preconditions.checkNotNull(builder.emitter);
        Preconditions.checkNotNull(builder.namespace);
        Preconditions.checkNotNull(builder.appId);
        Preconditions.checkArgument(!builder.namespace.isEmpty(), "namespace cannot be empty");
        Preconditions.checkArgument(!builder.appId.isEmpty(), "appId cannot be empty");

        this.emitter = builder.emitter;
        this.namespace = builder.namespace;
        this.appId = builder.appId;
        this.subject = builder.subject;
        this.platform = builder.platform;
        this.base64Encoded = builder.base64Encoded;
    }

    /**
     * Builder for the Tracker
     */
    public static class TrackerBuilder {

        private final Emitter emitter; // Required
        private final String namespace; // Required
        private final String appId; // Required
        private Subject subject = null; // Optional
        private DevicePlatform platform = DevicePlatform.ServerSideApp; // Optional
        private boolean base64Encoded = true; // Optional

        /**
         * @param emitter AbstractEmitter to which events will be sent
         * @param namespace Identifier for the Tracker instance
         * @param appId Application ID
         */
        public TrackerBuilder(Emitter emitter, String namespace, String appId) {
            this.emitter = emitter;
            this.namespace = namespace;
            this.appId = appId;
        }

        /**
         * @param subject Subject to be tracked
         * @return itself
         */
        public TrackerBuilder subject(Subject subject) {
            this.subject = subject;
            return this;
        }

        /**
         * @param platform The device platform the tracker is running on
         * @return itself
         */
        public TrackerBuilder platform(DevicePlatform platform) {
            this.platform = platform;
            return this;
        }

        /**
         * @param base64 Whether JSONs in the payload should be base-64 encoded
         * @return itself
         */
        public TrackerBuilder base64(Boolean base64) {
            this.base64Encoded = base64;
            return this;
        }

        /**
         * Creates a new Tracker
         *
         * @return a new Tracker object
         */
        public Tracker build() {
            return new Tracker(this);
        }
    }

    // --- Helpers

    /**
     * Builds and Adds a finalised payload which is ready for sending.
     *
     * @param payload The raw event Payload
     * @param contexts Custom context for the event
     * @param eventSubject An optional event specific Subject
     */
    private void addTrackerPayload(TrackerPayload payload, List<SelfDescribingJson> contexts, Subject eventSubject) {

        // Add default parameters to the payload
        payload.add(Parameter.PLATFORM, platform.toString());
        payload.add(Parameter.APP_ID, this.appId);
        payload.add(Parameter.NAMESPACE, this.namespace);
        payload.add(Parameter.TRACKER_VERSION, this.trackerVersion);

        // Build the final context and add it to the payload
        if (contexts != null && contexts.size() > 0) {
            SelfDescribingJson envelope = getFinalContext(contexts);
            payload.addMap(envelope.getMap(), this.base64Encoded, Parameter.CONTEXT_ENCODED, Parameter.CONTEXT);
        }

        // Add subject if available
        if (eventSubject != null) {
            payload.addMap(new HashMap<String, Object>(eventSubject.getSubject()));
        } else if (this.subject != null) {
            payload.addMap(new HashMap<String, Object>(this.subject.getSubject()));
        }

        // Send the event!
        this.emitter.emit(payload);
    }

    /**
     * Builds the final event context.
     *
     * @param contexts the base event context
     * @return the final event context json with
     * many contexts inside
     */
    private SelfDescribingJson getFinalContext(List<SelfDescribingJson> contexts) {
        List<Map> contextMaps = new LinkedList<Map>();
        for (SelfDescribingJson selfDescribingJson : contexts) {
            contextMaps.add(selfDescribingJson.getMap());
        }
        return new SelfDescribingJson(Constants.SCHEMA_CONTEXTS, contextMaps);
    }

    // --- Setters

    /**
     * @param emitter a new emitter
     */
    public void setEmitter(Emitter emitter) {
        this.emitter = emitter;
    }

    /**
     * Sets a new Subject object which will get attached to
     * each event payload.
     *
     * @param subject the new Subject
     */
    public void setSubject(Subject subject) {
        this.subject = subject;
    }

    /**
     * Sets the Trackers platform, defaults to a
     * Server Side Application.
     *
     * @param platform the DevicePlatform
     */
    public void setPlatform(DevicePlatform platform) {
        this.platform = platform;
    }

    /**
     * Sets whether to base64 Encode custom contexts
     * and unstructured events
     *
     * @param base64Encoded a boolean truth
     */
    public void setBase64Encoded(boolean base64Encoded) {
        this.base64Encoded = base64Encoded;
    }

    /**
     * Sets a new Application ID
     *
     * @param appId the new application id
     */
    public void setAppId(String appId) {
        this.appId = appId;
    }

    /**
     * Sets a new Tracker Namespace
     *
     * @param namespace the new tracker namespace
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    // --- Getters

    /**
     * @return the emitter associated with the tracker
     */
    public Emitter getEmitter() {
        return this.emitter;
    }

    /**
     * @return the Tracker Subject
     */
    public Subject getSubject() {
        return this.subject;
    }

    /**
     * @return the tracker version that was set
     */
    public String getTrackerVersion() {
        return this.trackerVersion;
    }

    /**
     * @return the trackers namespace
     */
    public String getNamespace() {
        return this.namespace;
    }

    /**
     * @return the trackers set Application ID
     */
    public String getAppId() {
        return this.appId;
    }

    /**
     * @return the base64 setting of the tracker
     */
    public boolean getBase64Encoded() {
        return this.base64Encoded;
    }

    /**
     * @return the Tracker platform
     */
    public DevicePlatform getPlatform() {
        return this.platform;
    }

    // --- Event Tracking Functions

    /**
     * Used for either Tracking a custom TrackerPayload or
     * for re-sending a failed event.
     *
     * @param payload the payload to track
     */
    public void track(TrackerPayload payload) {
        this.emitter.emit(payload);
    }

    /**
     * Tracks a PageView event
     *
     * @param event the PageView event.
     */
    public void track(PageView event) {
        List<SelfDescribingJson> context = event.getContext();
        TrackerPayload payload = event.getPayload();
        Subject subject = event.getSubject();
        addTrackerPayload(payload, context, subject);
    }

    /**
     * Tracks a Structured Event.
     *
     * @param event the Structured event.
     */
    public void track(Structured event) {
        List<SelfDescribingJson> context = event.getContext();
        TrackerPayload payload = event.getPayload();
        Subject subject = event.getSubject();
        addTrackerPayload(payload, context, subject);
    }

    /**
     * Tracks an Ecommerce Transaction Event.
     * - Will also track any Items in separate
     *   payloads.
     *
     * @param event the Ecommerce Transaction event.
     */
    public void track(EcommerceTransaction event) {
        List<SelfDescribingJson> context = event.getContext();
        TrackerPayload payload = event.getPayload();
        Subject subject = event.getSubject();
        addTrackerPayload(payload, context, subject);

        // Track each TransactionItem individually
        long timestamp = event.getTimestamp();
        for(EcommerceTransactionItem item : event.getItems()) {
            track(item, timestamp);
        }
    }

    /**
     * Tracks an Ecommerce Transaction Item event.
     *
     * @param event the Ecommerce Transaction Item event
     * @param timestamp the Timestamp of the Transaction
     */
    private void track(EcommerceTransactionItem event, long timestamp) {
        List<SelfDescribingJson> context = event.getContext();
        TrackerPayload payload = event.getPayload(timestamp);
        Subject subject = event.getSubject();
        addTrackerPayload(payload, context, subject);
    }

    /**
     * Tracks an Unstructured Event.
     *
     * @param event the Structured event.
     */
    public void track(Unstructured event) {
        List<SelfDescribingJson> context = event.getContext();
        TrackerPayload payload = event.getPayload(base64Encoded);
        Subject subject = event.getSubject();
        addTrackerPayload(payload, context, subject);
    }

    /**
     * Tracks a ScreenView Event.
     *
     * @param event the ScreenView event.
     */
    public void track(ScreenView event) {
        this.track(Unstructured.builder()
                .eventData(event.getSelfDescribingJson())
                .customContext(event.getContext())
                .timestamp(event.getTimestamp())
                .eventId(event.getEventId())
                .subject(event.getSubject())
                .build());
    }

    /**
     * Tracks a Timing Event.
     *
     * @param event the Timing event.
     */
    public void track(Timing event) {
        this.track(Unstructured.builder()
                .eventData(event.getSelfDescribingJson())
                .customContext(event.getContext())
                .timestamp(event.getTimestamp())
                .eventId(event.getEventId())
                .subject(event.getSubject())
                .build());
    }
}
