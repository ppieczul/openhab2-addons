/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.loxone.internal.controls;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.StateDescription;
import org.openhab.binding.loxone.internal.LxServerHandlerApi;
import org.openhab.binding.loxone.internal.core.LxCategory;
import org.openhab.binding.loxone.internal.core.LxContainer;
import org.openhab.binding.loxone.internal.core.LxUuid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

/**
 * A control of Loxone Miniserver.
 * <p>
 * It represents a control object on the Miniserver. Controls can represent an input, functional block or an output of
 * the Miniserver, that is marked as visible in the Loxone UI. Controls can belong to a {@link LxContainer} room and a
 * {@link LxCategory} category.
 *
 * @author Pawel Pieczul - initial contribution
 *
 */
public class LxControl {

    /**
     * This class is used to instantiate a particular control object by the {@link LxControlFactory}
     *
     * @author Pawel Pieczul - initial contribution
     *
     */
    abstract static class LxControlInstance {
        /**
         * Creates an instance of a particular control class.
         * 
         * @param uuid UUID of the control object to be created
         * @return a newly created control object
         */
        abstract LxControl create(LxUuid uuid);

        /**
         * Return a type name for this control.
         *
         * @return type name (as used on the Miniserver)
         */
        abstract String getType();
    }

    /**
     * This class describes additional parameters of a control received from the Miniserver
     *
     * @author Pawel Pieczul - initial contribution
     *
     */
    class LxControlDetails {
        Double min;
        Double max;
        Double step;
        String format;
        String allOff;
        Map<String, String> outputs;
    }

    /*
     * Parameters parsed from the JSON configuration file during deserialization
     */
    LxUuid uuid;
    LxControlDetails details;
    private String name;
    private LxUuid roomUuid;
    private LxUuid categoryUuid;
    private Map<LxUuid, LxControl> subControls;
    private final Map<String, LxControlState> states;

    /*
     * Parameters set when finalizing {@link LxConfig} object setup. They will be null right after constructing object.
     */
    String defaultChannelLabel;
    ChannelUID defaultChannelId;
    LxServerHandlerApi handlerApi;
    private LxContainer room;
    private LxCategory category;
    private ThingUID thingId;

    /*
     * Parameters set when object is connected to the openHAB by the binding handler
     */
    final Set<String> tags = new HashSet<>();
    final List<Channel> channels = new ArrayList<>();

    private final transient Logger logger;
    static final Gson DEFAULT_GSON = new Gson();

    /*
     * JSON deserialization routine, called during parsing configuration by the GSON library
     */
    public static final JsonDeserializer<LxControl> DESERIALIZER = new JsonDeserializer<LxControl>() {
        @Override
        public LxControl deserialize(JsonElement json, Type type, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject parent = json.getAsJsonObject();
            String controlName = parent.get("name").getAsString();
            String controlType = parent.get("type").getAsString();
            LxUuid uuid = deserializeObject(parent, "uuidAction", LxUuid.class, context);
            if (controlName == null || controlType == null || uuid == null) {
                throw new JsonParseException("Control name/type/uuid is null.");
            }
            LxControl control = LxControlFactory.createControl(uuid, controlType);
            if (control == null) {
                return null;
            }
            control.name = controlName;
            control.roomUuid = deserializeObject(parent, "room", LxUuid.class, context);
            control.categoryUuid = deserializeObject(parent, "cat", LxUuid.class, context);
            control.details = deserializeObject(parent, "details", LxControlDetails.class, context);
            control.subControls = deserializeObject(parent, "subControls", new TypeToken<Map<LxUuid, LxControl>>() {
            }.getType(), context);

            JsonObject states = parent.getAsJsonObject("states");
            if (states != null) {
                states.entrySet().forEach(entry -> {
                    // temperature state of intelligent home controller object is the only
                    // one that has state represented as an array, as this is not implemented
                    // yet, we will skip this state
                    JsonElement element = entry.getValue();
                    if (element != null && !(element instanceof JsonArray)) {
                        String value = element.getAsString();
                        if (value != null) {
                            String name = entry.getKey().toLowerCase();
                            control.states.put(name, new LxControlState(new LxUuid(value), name, control));
                        }
                    }
                });
            }
            return control;
        }
    };

    private static <T> T deserializeObject(JsonObject parent, String name, Type type,
            JsonDeserializationContext context) {
        JsonElement element = parent.get(name);
        if (element != null) {
            return context.deserialize(element, type);
        }
        return null;
    }

    LxControl(LxUuid uuid) {
        logger = LoggerFactory.getLogger(LxControl.class);
        this.uuid = uuid;
        states = new HashMap<>();
    }

    /**
     * A method that executes commands by the control. To be overridden by child classes.
     *
     * @param channelId channel Id for the command
     * @param command   value of the command to perform
     * @throws IOException in case of communication error with the Miniserver
     */
    public void handleCommand(ChannelUID channelId, Command command) throws IOException {
    }

    /**
     * Provides actual state value for the specified channel. To be overridden by child classes.
     *
     * @param channelId channel ID to get state for
     * @return state if the channel value or null if no value available
     */
    public State getChannelState(ChannelUID channelId) {
        return null;
    }

    /**
     * Call when control is no more needed - unlink it from containers
     */
    public void dispose() {
        if (room != null) {
            room.removeControl(this);
        }
        if (category != null) {
            category.removeControl(this);
        }
        subControls.values().forEach(control -> control.dispose());
    }

    /**
     * Obtain control's name
     *
     * @return Human readable name of control
     */
    public String getName() {
        return name;
    }

    /**
     * Get control's UUID as defined on the Miniserver
     *
     * @return UUID of the control
     */
    public LxUuid getUuid() {
        return uuid;
    }

    /**
     * Get subcontrols of this control
     *
     * @return subcontrols of the control
     */
    public Map<LxUuid, LxControl> getSubControls() {
        return subControls;
    }

    /**
     * Get control's channels
     *
     * @return channels
     */
    public List<Channel> getChannels() {
        return channels;
    }

    /**
     * Get control's Miniserver states
     *
     * @return control's Miniserver states
     */
    public Map<String, LxControlState> getStates() {
        return states;
    }

    /**
     * Compare UUID's of two controls -
     *
     * @param object Object to compare with
     * @return true if UUID of two objects are equal
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null) {
            return false;
        }
        if (object.getClass() != getClass()) {
            return false;
        }
        LxControl c = (LxControl) object;
        return Objects.equals(c.uuid, uuid);
    }

    /**
     * Hash code of the control is equal to its UUID's hash code
     */
    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    /**
     * Initialize Miniserver's control in runtime. Each class that implements {@link LxControl} should override this
     * method and call it as a first step in the overridden implementation. Then it should add all runtime data, like
     * channels and any fields that derive their value from the parsed JSON configuration.
     * Before this method is called during configuration parsing, the control object must not be used.
     *
     * @param api      object to communicate with thing handler
     * @param room     A room that this control and its subcontrols belong to
     * @param category A category that this control and its subcontrols belong to
     */
    public void initialize(LxServerHandlerApi api, LxContainer room, LxCategory category) {
        logger.debug("Initializing LxControl: {}", uuid);

        if (handlerApi != null) {
            logger.error("Error, attempt to initialize control that is already initialized: {}", uuid);
            return;
        }
        handlerApi = api;
        thingId = handlerApi.getThingId();
        defaultChannelId = getChannelId(0);

        if (subControls == null) {
            subControls = new HashMap<>();
        }

        if (room != null) {
            this.room = room;
            room.addControl(this);
        }

        if (category != null) {
            this.category = category;
            category.addControl(this);
        }

        String label = getLabel();
        if (label == null) {
            // Each control on a Miniserver must have a name defined, but in case this is a subject
            // of some malicious data attack, we'll prevent null pointer exception
            label = "Undefined name";
        }
        String roomName = room != null ? room.getName() : null;
        if (roomName != null) {
            label = roomName + " / " + label;
        }
        defaultChannelLabel = label;

        // Propagate to all subcontrols of this control object
        subControls.values().forEach(c -> c.initialize(api, room, category));
    }

    /**
     * Get control's room.
     *
     * @return control's room object
     */
    LxContainer getRoom() {
        return room;
    }

    /**
     * Get control's category.
     *
     * @return control's category object
     */
    LxCategory getCategory() {
        return category;
    }

    public LxUuid getRoomUuid() {
        return roomUuid;
    }

    public LxUuid getCategoryUuid() {
        return categoryUuid;
    }

    /**
     * This method will be called from {@link LxControlState}, when Miniserver state value is updated.
     * By default it will query all channels of the control and update their state accordingly.
     * This method will not handle channel state descriptions, as they must be prepared individually.
     * It can be overridden in child class to handle particular states differently.
     *
     * @param state changed Miniserver state or null if not specified (any/all)
     */
    void onStateChange(LxControlState state) {
        channels.forEach(channel -> {
            ChannelUID channelId = channel.getUID();
            State channelState = getChannelState(channelId);
            if (channelState != null) {
                handlerApi.setChannelState(channelId, channelState);
            }
        });
    }

    /**
     * Returns control label that will be used for building channel name. This allows for customizing the label per
     * control.
     *
     * @return control channel label
     */
    String getLabel() {
        return name;
    }

    /**
     * Gets value of a state object of given name, if exists
     *
     * @param name name of state object
     * @return state object's value
     */
    Double getStateDoubleValue(String name) {
        LxControlState state = states.get(name);
        if (state != null) {
            Object value = state.getStateValue();
            if (value instanceof Double) {
                return (Double) value;
            }
        }
        return null;
    }

    /**
     * Gets text value of a state object of given name, if exists
     *
     * @param name name of state object
     * @return state object's text value
     */
    String getStateTextValue(String name) {
        LxControlState state = states.get(name);
        if (state != null) {
            Object value = state.getStateValue();
            if (value instanceof String) {
                return (String) value;
            }
        }
        return null;
    }

    /**
     * Build channel ID for the control, based on control's UUID, thing's UUID and index of the channel for the control
     *
     * @param index index of a channel within control (0 for primary channel) all indexes greater than 0 will have
     *                  -index added to the channel ID
     * @return channel ID for the control and index
     */
    ChannelUID getChannelId(int index) {
        if (thingId == null) {
            logger.error("Attempt to get control's channel ID with not finalized configuration!: {}", index);
            return null;
        }
        String controlId = uuid.toString();
        if (index > 0) {
            controlId += "-" + index;
        }
        return new ChannelUID(thingId, controlId);
    }

    /**
     * Create a new channel and add it to the control.
     *
     * @param itemType           item type for the channel
     * @param typeId             channel type ID for the channel
     * @param channelId          channel ID
     * @param channelLabel       channel label
     * @param channelDescription channel description
     * @param tags               tags for the channel or null if no tags needed
     */
    void addChannel(String itemType, ChannelTypeUID typeId, ChannelUID channelId, String channelLabel,
            String channelDescription, Set<String> tags) {
        if (channelLabel == null || channelDescription == null) {
            logger.error("Attempt to add channel with not finalized configuration!: {}", channelId);
            return;
        }
        ChannelBuilder builder = ChannelBuilder.create(channelId, itemType).withType(typeId).withLabel(channelLabel)
                .withDescription(channelDescription + " : " + channelLabel);
        if (tags != null) {
            builder.withDefaultTags(tags);
        }
        channels.add(builder.build());
    }

    /**
     * Adds a new {@link StateDescription} for a channel that has multiple options to select from or a custom format
     * string.
     *
     * @param channelId   channel ID to add the description for
     * @param description channel state description
     */
    void addChannelStateDescription(ChannelUID channelId, StateDescription description) {
        if (handlerApi == null) {
            logger.error("Attempt to set channel state description with not finalized configuration!: {}", channelId);
        } else {
            handlerApi.setChannelStateDescription(channelId, description);
        }
    }

    /**
     * Sends an action command to the Miniserver using active socket connection
     *
     * @param action string with action command
     * @throws IOException when communication error with Miniserver occurs
     */
    void sendAction(String action) throws IOException {
        if (handlerApi == null) {
            logger.error("Attempt to send command with not finalized configuration!: {}", action);
        } else {
            handlerApi.sendAction(uuid, action);
        }
    }
}
