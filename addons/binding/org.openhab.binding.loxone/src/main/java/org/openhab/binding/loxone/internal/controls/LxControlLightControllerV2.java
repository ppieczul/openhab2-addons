/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.loxone.internal.controls;

import static org.openhab.binding.loxone.LoxoneBindingConstants.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.UpDownType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.StateDescription;
import org.eclipse.smarthome.core.types.StateOption;
import org.eclipse.smarthome.core.types.UnDefType;
import org.openhab.binding.loxone.handler.LoxoneMiniserverHandlerApi;
import org.openhab.binding.loxone.internal.core.LxCategory;
import org.openhab.binding.loxone.internal.core.LxContainer;
import org.openhab.binding.loxone.internal.core.LxJsonApp3;
import org.openhab.binding.loxone.internal.core.LxJsonApp3.LxJsonControl;
import org.openhab.binding.loxone.internal.core.LxJsonMood;
import org.openhab.binding.loxone.internal.core.LxUuid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonSyntaxException;

/**
 * A Light Controller V2 type of control on Loxone Miniserver.
 * <p>
 * This control has been introduced in Loxone Config 9 in 2017 and it makes the {@link LxControlLightController}
 * obsolete. Both controls will exist for some time together.
 * <p>
 * Light controller V2 can have N outputs named AQ1...AQN that can function as Switch, Dimmer, RGB, Lumitech or Smart
 * Actuator functional blocks. Individual controls will be created for these outputs so they can be operated directly
 * and independently from the controller.
 * <p>
 * Controller can also have M moods configured. Each mood defines own subset of outputs and their settings, which will
 * be engaged when the mood is active. A dedicated switch control object will be created for each mood.
 * This effectively will allow for mixing various moods by individually enabling/disabling them.
 * <p>
 * It seems there is no imposed limitation for the number of outputs and moods.
 *
 * @author Pawel Pieczul - initial contribution
 *
 */
public class LxControlLightControllerV2 extends LxControlAbstractController {

    static class Factory extends LxControlInstance {
        @Override
        LxControl create(LoxoneMiniserverHandlerApi handlerApi, LxUuid uuid, LxJsonControl json, LxContainer room,
                LxCategory category) {
            return new LxControlLightControllerV2(handlerApi, uuid, json, room, category);
        }

        @Override
        String getType() {
            return TYPE_NAME;
        }
    }

    /**
     * A name by which Miniserver refers to light controller v2 controls
     */
    private static final String TYPE_NAME = "lightcontrollerv2";

    /**
     * State with list of active moods
     */
    public static final String STATE_ACTIVE_MOODS_LIST = "activemoods";
    /**
     * State with list of available moods
     */
    public static final String STATE_MOODS_LIST = "moodlist";

    /**
     * Command string used to set a given mood
     */
    private static final String CMD_CHANGE_TO_MOOD = "changeTo";
    /**
     * Command string used to change to the next mood
     */
    private static final String CMD_NEXT_MOOD = "plus";
    /**
     * Command string used to change to the previous mood
     */
    private static final String CMD_PREVIOUS_MOOD = "minus";
    /**
     * Command string used to add mood to the active moods (mix it in)
     */
    private static final String CMD_ADD_MOOD = "addMood";
    /**
     * Command string used to remove mood from the active moods (mix it out)
     */
    private static final String CMD_REMOVE_MOOD = "removeMood";

    private final Logger logger = LoggerFactory.getLogger(LxControlLightControllerV2.class);

    // Following commands are not supported:
    // moveFavoriteMood, moveAdditionalMood, moveMood, addToFavoriteMood, removeFromFavoriteMood, learn, delete

    private Map<LxUuid, LxControlMood> moodList = new HashMap<>();
    private List<Integer> activeMoods = new ArrayList<>();
    private Integer minMoodId;
    private Integer maxMoodId;

    /**
     * Create lighting controller v2 object.
     *
     * @param handlerApi
     *            thing handler object representing the Miniserver
     * @param uuid
     *            controller's UUID
     * @param json
     *            JSON describing the control as received from the Miniserver
     * @param room
     *            room to which controller belongs
     * @param category
     *            category to which controller belongs
     */
    LxControlLightControllerV2(LoxoneMiniserverHandlerApi handlerApi, LxUuid uuid, LxJsonControl json, LxContainer room,
            LxCategory category) {
        super(handlerApi, uuid, json, room, category);
        // add only channel, state description will be added later when a control state update message is received
        addChannel("Number", new ChannelTypeUID(BINDING_ID, MINISERVER_CHANNEL_TYPE_LIGHT_CTRL), defaultChannelId,
                defaultChannelLabel, "Light controller V2", tags);
        // sub-controls of this control have been created when update() method was called by the super class constructor
    }

    @Override
    public void handleCommand(ChannelUID channelId, Command command) throws IOException {
        if (command instanceof UpDownType) {
            if ((UpDownType) command == UpDownType.UP) {
                sendAction(CMD_NEXT_MOOD);
            } else {
                sendAction(CMD_PREVIOUS_MOOD);
            }
        } else if (command instanceof DecimalType) {
            int moodId = ((DecimalType) command).intValue();
            if (isMoodOk(moodId)) {
                sendAction(CMD_CHANGE_TO_MOOD + "/" + moodId);
            }
        }
    }

    @Override
    public State getChannelState(ChannelUID channelId) {
        if (defaultChannelId.equals(channelId)) {
            // update the single mood channel state
            if (activeMoods.size() == 1) {
                return new DecimalType(activeMoods.get(0));
            } else {
                return UnDefType.UNDEF;
            }
        }
        return null;
    }

    /**
     * Get configured and active moods from a new state value received from the Miniserver
     *
     * @param state
     *            state update from the Miniserver
     */
    @Override
    public void onStateChange(LxControlState state) {
        String stateName = state.getName();
        try {
            if (STATE_MOODS_LIST.equals(stateName)) {
                onMoodsListChange(state.getTextValue());
            } else if (STATE_ACTIVE_MOODS_LIST.equals(stateName)) {
                // this state can be received before list of moods, but it contains a valid list of IDs
                Integer[] array = handlerApi.getGson().fromJson(state.getTextValue(), Integer[].class);
                activeMoods = Arrays.asList(array);
                // update all moods states - this will force update of channels too
                moodList.values().forEach(mood -> mood.onStateChange(null));
                // finally we update controller's state based on the active moods list
                super.onStateChange(state);
            }
        } catch (JsonSyntaxException e) {
            logger.debug("Error parsing state {}: {}", stateName, e.getMessage());
        }
    }

    /**
     * Mix a mood into currently active moods.
     *
     * @param moodId
     *            ID of the mood to add
     * @throws IOException
     *             when something went wrong with communication
     */
    void addMood(Integer moodId) throws IOException {
        if (isMoodOk(moodId)) {
            sendAction(CMD_ADD_MOOD + "/" + moodId);
        }
    }

    /**
     * Check if mood is currently active.
     *
     * @param moodId
     *            mood ID to check
     * @return
     *         true if mood is currently active
     */
    boolean isMoodActive(Integer moodId) {
        return activeMoods.contains(moodId);
    }

    /**
     * Check if mood ID is within allowed range
     *
     * @param moodId
     *            mood ID to check
     * @return
     *         true if mood ID is within allowed range or range is not configured
     */
    boolean isMoodOk(Integer moodId) {
        if ((minMoodId != null && minMoodId > moodId) || (maxMoodId != null && maxMoodId < moodId)) {
            return false;
        }
        return true;
    }

    /**
     * Mix a mood out of currently active moods.
     *
     * @param moodId
     *            ID of the mood to remove
     * @throws IOException
     *             when something went wrong with communication
     */
    void removeMood(Integer moodId) throws IOException {
        if (isMoodOk(moodId)) {
            sendAction(CMD_REMOVE_MOOD + "/" + moodId);
        }
    }

    /**
     * Handles a change in the list of configured moods
     *
     * @param text
     *            json structure with new moods
     * @throws JsonSyntaxException
     *             error parsing json structure
     */
    private void onMoodsListChange(String text) throws JsonSyntaxException {
        LxJsonMood[] array = handlerApi.getGson().fromJson(text, LxJsonMood[].class);
        Map<LxUuid, LxControlMood> newMoodList = new HashMap<>();
        minMoodId = null;
        maxMoodId = null;
        LxJsonControl json = new LxJsonApp3().new LxJsonControl();
        for (LxJsonMood mood : array) {
            if (mood.id != null && mood.name != null) {
                logger.debug("Adding mood {} (name={}, isUsed={}, t5={}, static={}", mood.id, mood.name, mood.isUsed,
                        mood.isT5Controlled, mood.isStatic);
                json.name = mood.name;
                // mood-UUID = <controller-UUID>-M<mood-ID>
                LxUuid moodUuid = new LxUuid(uuid.toString() + "-M" + mood.id);
                LxControlMood control = new LxControlMood(handlerApi, moodUuid, json, getRoom(), getCategory(), mood.id,
                        mood.isStatic, this);
                newMoodList.put(moodUuid, control);
                if (minMoodId == null || minMoodId > mood.id) {
                    minMoodId = mood.id;
                }
                if (maxMoodId == null || maxMoodId < mood.id) {
                    maxMoodId = mood.id;
                }
            }
        }

        if (minMoodId != null && maxMoodId != null) {
            // convert all moods to options list for state description
            List<StateOption> optionsList = newMoodList.values().stream()
                    .map(mood -> new StateOption(mood.getId().toString(), mood.getName())).collect(Collectors.toList());
            addChannelStateDescription(defaultChannelId, new StateDescription(new BigDecimal(minMoodId),
                    new BigDecimal(maxMoodId), BigDecimal.ONE, null, false, optionsList));
        }

        moodList.entrySet().stream().filter(e -> !newMoodList.containsKey(e.getKey())).forEach(e -> {
            handlerApi.removeControl(e.getValue());
        });

        newMoodList.entrySet().stream().filter(e -> !moodList.containsKey(e.getKey())).forEach(e -> {
            handlerApi.addControl(e.getValue());
        });

        moodList = newMoodList;
    }
}
