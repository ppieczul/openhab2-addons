/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.loxone.internal.controls;

import java.io.IOException;

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.loxone.handler.LoxoneMiniserverHandlerApi;
import org.openhab.binding.loxone.internal.core.LxCategory;
import org.openhab.binding.loxone.internal.core.LxContainer;
import org.openhab.binding.loxone.internal.core.LxJsonApp3.LxJsonControl;
import org.openhab.binding.loxone.internal.core.LxUuid;

/**
 * A pushbutton type of control on Loxone Miniserver.
 * <p>
 * According to Loxone API documentation, a pushbutton control covers virtual input of type pushbutton
 *
 * @author Pawel Pieczul - initial contribution
 *
 */
public class LxControlPushbutton extends LxControlSwitch {

    static class Factory extends LxControlInstance {
        @Override
        LxControl create(LoxoneMiniserverHandlerApi handlerApi, LxUuid uuid, LxJsonControl json, LxContainer room,
                LxCategory category) {
            return new LxControlPushbutton(handlerApi, uuid, json, room, category);
        }

        @Override
        String getType() {
            return TYPE_NAME;
        }
    }

    /**
     * A name by which Miniserver refers to pushbutton controls
     */
    private static final String TYPE_NAME = "pushbutton";
    /**
     * Command string used to set control's state to ON and OFF (tap)
     */
    private static final String CMD_PULSE = "Pulse";

    /**
     * Create pushbutton control object.
     *
     * @param handlerApi
     *            thing handler object representing the Miniserver
     * @param uuid
     *            switch's UUID
     * @param json
     *            JSON describing the control as received from the Miniserver
     * @param room
     *            room to which switch belongs
     * @param category
     *            category to which switch belongs
     */
    LxControlPushbutton(LoxoneMiniserverHandlerApi handlerApi, LxUuid uuid, LxJsonControl json, LxContainer room,
            LxCategory category) {
        super(handlerApi, uuid, json, room, category);
    }

    @Override
    public void handleCommand(ChannelUID channelId, Command command) throws IOException {
        if (command instanceof OnOffType) {
            if ((OnOffType) command == OnOffType.ON) {
                sendAction(CMD_PULSE);
            } else {
                off();
            }
        }
    }
}
