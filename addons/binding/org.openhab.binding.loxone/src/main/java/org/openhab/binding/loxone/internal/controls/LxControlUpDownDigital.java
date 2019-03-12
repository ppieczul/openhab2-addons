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

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.loxone.internal.LxServerHandler;
import org.openhab.binding.loxone.internal.types.LxCategory;
import org.openhab.binding.loxone.internal.types.LxContainer;
import org.openhab.binding.loxone.internal.types.LxUuid;

/**
 * An UpDownDigital type of control on Loxone Miniserver.
 * <p>
 * According to Loxone API documentation, UpDownDigital control is a virtual input that is digital and has an input type
 * up-down buttons. Buttons act like on an integrated up/down arrows switch - only one direction can be active at a
 * time. Pushing button in one direction will automatically set the other direction to off.
 * This control has no states and can only accept commands. Only up/down on/off commands are generated. Pulse
 * commands are not supported, because of lack of corresponding feature in openHAB. Pulse can be emulated by quickly
 * alternating between ON and OFF commands. Because this control has no states, there will be no openHAB state changes
 * triggered by the Miniserver and we need to take care of updating the states inside this class.
 *
 * @author Pawel Pieczul - initial contribution
 *
 */
public class LxControlUpDownDigital extends LxControl {

    static class Factory extends LxControlInstance {
        @Override
        LxControl create(LxUuid uuid) {
            return new LxControlUpDownDigital(uuid);
        }

        @Override
        String getType() {
            return TYPE_NAME;
        }
    }

    /**
     * A name by which Miniserver refers to switch controls
     */
    static final String TYPE_NAME = "updowndigital";

    private static final String CMD_UP_ON = "UpOn";
    private static final String CMD_UP_OFF = "UpOff";
    private static final String CMD_DOWN_ON = "DownOn";
    private static final String CMD_DOWN_OFF = "DownOff";

    private OnOffType upState = OnOffType.OFF;
    private OnOffType downState = OnOffType.OFF;
    private ChannelUID upChannelId;
    private ChannelUID downChannelId;

    LxControlUpDownDigital(LxUuid uuid) {
        super(uuid);
    }

    @Override
    public void initialize(LxServerHandler thingHandler, LxContainer room, LxCategory category) {
        initialize(thingHandler, room, category, " / Up", "Up/Down Digital: Up", " / Down", "Up/Down Digital: Down");
    }

    void initialize(LxServerHandler thingHandler, LxContainer room, LxCategory category, String upChannelLabel,
            String upChannelDescription, String downChannelLabel, String downChannelDescription) {
        super.initialize(thingHandler, room, category);
        upChannelId = addChannel("Switch", new ChannelTypeUID(BINDING_ID, MINISERVER_CHANNEL_TYPE_SWITCH),
                defaultChannelLabel + upChannelLabel, upChannelDescription, tags, this::handleUpCommands,
                () -> upState);
        downChannelId = addChannel("Switch", new ChannelTypeUID(BINDING_ID, MINISERVER_CHANNEL_TYPE_SWITCH),
                defaultChannelLabel + downChannelLabel, downChannelDescription, tags, this::handleDownCommands,
                () -> downState);
    }

    private void handleUpCommands(Command command) throws IOException {
        if (command instanceof OnOffType) {
            if ((OnOffType) command == OnOffType.ON && upState == OnOffType.OFF) {
                setStates(OnOffType.ON, OnOffType.OFF);
                sendAction(CMD_UP_ON);
            } else if (upState == OnOffType.ON) {
                setStates(OnOffType.OFF, OnOffType.OFF);
                sendAction(CMD_UP_OFF);
            }
        }
    }

    private void handleDownCommands(Command command) throws IOException {
        if (command instanceof OnOffType) {
            if ((OnOffType) command == OnOffType.ON && downState == OnOffType.OFF) {
                setStates(OnOffType.OFF, OnOffType.ON);
                sendAction(CMD_DOWN_ON);
            } else if (downState == OnOffType.ON) {
                setStates(OnOffType.OFF, OnOffType.OFF);
                sendAction(CMD_DOWN_OFF);
            }
        }
    }

    private void setStates(OnOffType upState, OnOffType downState) {
        this.upState = upState;
        this.downState = downState;
        thingHandler.setChannelState(upChannelId, upState);
        thingHandler.setChannelState(downChannelId, downState);
    }
}
