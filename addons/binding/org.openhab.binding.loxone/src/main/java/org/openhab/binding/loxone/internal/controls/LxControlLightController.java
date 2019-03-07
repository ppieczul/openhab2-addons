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

import static org.openhab.binding.loxone.internal.LxBindingConstants.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.UpDownType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.StateDescription;
import org.eclipse.smarthome.core.types.StateOption;
import org.openhab.binding.loxone.internal.LxServerHandlerApi;
import org.openhab.binding.loxone.internal.core.LxCategory;
import org.openhab.binding.loxone.internal.core.LxContainer;
import org.openhab.binding.loxone.internal.core.LxUuid;

/**
 * A Light Controller type of control on Loxone Miniserver.
 * <p>
 * According to Loxone API documentation, a light controller is one of following functional blocks:
 * <ul>
 * <li>Lighting Controller
 * <li>Hotel Lighting Controller
 * </ul>
 *
 * @author Pawel Pieczul - initial contribution
 *
 */
public class LxControlLightController extends LxControl {

    static class Factory extends LxControlInstance {
        @Override
        LxControl create(LxUuid uuid) {
            return new LxControlLightController(uuid);
        }

        @Override
        String getType() {
            return TYPE_NAME;
        }
    }

    /**
     * Number of scenes supported by the Miniserver. Indexing starts with 0 to NUM_OF_SCENES-1.
     */
    private static final int NUM_OF_SCENES = 10;

    /**
     * A name by which Miniserver refers to light controller controls
     */
    private static final String TYPE_NAME = "lightcontroller";

    /**
     * Current active scene number (0-9)
     */
    private static final String STATE_ACTIVE_SCENE = "activescene";
    /**
     * List of available scenes (public state, so user can monitor scene list updates)
     */
    public static final String STATE_SCENE_LIST = "scenelist";
    /**
     * Command string used to set control's state to ON
     */
    private static final String CMD_ON = "On";
    /**
     * Command string used to set control's state to OFF
     */
    private static final String CMD_OFF = "Off";
    /**
     * Command string used to go to the next scene
     */
    private static final String CMD_NEXT_SCENE = "plus";
    /**
     * Command string used to go to the previous scene
     */
    private static final String CMD_PREVIOUS_SCENE = "minus";
    private static final int SCENE_ALL_ON = 9;

    private List<StateOption> sceneNames = new ArrayList<>();

    LxControlLightController(LxUuid uuid) {
        super(uuid);
    }

    @Override
    public void initialize(LxServerHandlerApi api, LxContainer room, LxCategory category) {
        super.initialize(api, room, category);
        // add only channel, state description will be added later when a control state update message is received
        addChannel("Number", new ChannelTypeUID(BINDING_ID, MINISERVER_CHANNEL_TYPE_LIGHT_CTRL), defaultChannelId,
                defaultChannelLabel, "Light controller", tags);
        // sub-controls of this control have been created when update() method was called by super class
    }

    @Override
    public void handleCommand(ChannelUID channelId, Command command) throws IOException {
        if (command instanceof OnOffType) {
            if ((OnOffType) command == OnOffType.ON) {
                sendAction(CMD_ON);
            } else {
                sendAction(CMD_OFF);
            }
        } else if (command instanceof UpDownType) {
            if ((UpDownType) command == UpDownType.UP) {
                sendAction(CMD_NEXT_SCENE);
            } else {
                sendAction(CMD_PREVIOUS_SCENE);
            }
        } else if (command instanceof DecimalType) {
            int scene = ((DecimalType) command).intValue();
            if (scene == SCENE_ALL_ON) {
                sendAction(CMD_ON);
            } else if (scene >= 0 && scene < NUM_OF_SCENES) {
                sendAction(Long.toString(scene));
            }
        }
    }

    @Override
    public State getChannelState(ChannelUID channelId) {
        if (defaultChannelId.equals(channelId)) {
            Double value = getStateDoubleValue(STATE_ACTIVE_SCENE);
            if (value != null && value >= 0 && value < NUM_OF_SCENES) {
                return new DecimalType(value);
            }
        }
        return null;
    }

    /**
     * Get scene names from new state value received from the Miniserver
     */
    @Override
    public void onStateChange(LxControlState state) {
        if (STATE_SCENE_LIST.equals(state.getName())) {
            Object value = state.getStateValue();
            if (value instanceof String) {
                sceneNames.clear();
                String[] scenes = ((String) value).split(",");
                for (String line : scenes) {
                    line = line.replaceAll("\"", "");
                    String[] params = line.split("=");
                    if (params.length == 2) {
                        sceneNames.add(new StateOption(params[0], params[1]));
                    }
                }
                addChannelStateDescription(defaultChannelId, new StateDescription(BigDecimal.ZERO,
                        new BigDecimal(NUM_OF_SCENES - 1), BigDecimal.ONE, null, false, sceneNames));
            }
        } else {
            super.onStateChange(state);
        }
    }
}
