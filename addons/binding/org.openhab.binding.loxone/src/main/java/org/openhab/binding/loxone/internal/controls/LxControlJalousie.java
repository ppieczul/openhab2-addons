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

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.StopMoveType;
import org.eclipse.smarthome.core.library.types.UpDownType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.type.ChannelTypeUID;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.loxone.handler.LoxoneMiniserverHandlerApi;
import org.openhab.binding.loxone.internal.core.LxCategory;
import org.openhab.binding.loxone.internal.core.LxContainer;
import org.openhab.binding.loxone.internal.core.LxJsonApp3.LxJsonControl;
import org.openhab.binding.loxone.internal.core.LxUuid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A jalousie type of control on Loxone Miniserver.
 * <p>
 * According to Loxone API documentation, a jalousie control covers:
 * <ul>
 * <li>Blinds</li>
 * <li>Automatic blinds</li>
 * <li>Automatic blinds integrated</li>
 * </ul>
 * <p>
 * Jalousie control has three channels:
 * <ul>
 * <li>0 (default) - rollershutter position</li>
 * <li>1 - shading command (always off switch, sending on triggers shading)</li>
 * <li>2 - automatic shading (on/off switch)</li>
 * </ul>
 *
 * @author Pawel Pieczul - initial contribution
 *
 */
public class LxControlJalousie extends LxControl {

    static class Factory extends LxControlInstance {
        @Override
        LxControl create(LoxoneMiniserverHandlerApi handlerApi, LxUuid uuid, LxJsonControl json, LxContainer room,
                LxCategory category) {
            return new LxControlJalousie(handlerApi, uuid, json, room, category);
        }

        @Override
        String getType() {
            return TYPE_NAME;
        }
    }

    /**
     * A name by which Miniserver refers to jalousie controls
     */
    private static final String TYPE_NAME = "jalousie";

    /**
     * Jalousie is moving up
     */
    private static final String STATE_UP = "up";
    /**
     * Jalousie is moving down
     */
    private static final String STATE_DOWN = "down";
    /**
     * The position of the Jalousie, a number from 0 to 1
     * Jalousie upper position = 0
     * Jalousie lower position = 1
     */
    private static final String STATE_POSITION = "position";
    /**
     * Only used by ones with Autopilot
     */
    private static final String STATE_AUTO_ACTIVE = "autoactive";
    /**
     * Command string used to set control's state to Full Down
     */
    private static final String CMD_FULL_DOWN = "FullDown";
    /**
     * Command string used to set control's state to Full Up
     */
    private static final String CMD_FULL_UP = "FullUp";
    /**
     * Command string used to stop rollershutter
     */
    private static final String CMD_STOP = "Stop";
    /**
     * Command to shade the jalousie
     */
    private static final String CMD_SHADE = "shade";
    /**
     * Command to enable automatic shading
     */
    private static final String CMD_AUTO = "auto";
    /**
     * Command to disable automatic shading
     */
    private static final String CMD_NO_AUTO = "NoAuto";

    private final Logger logger = LoggerFactory.getLogger(LxControlJalousie.class);

    private final ChannelUID shadeChannelId = getChannelId(1);
    private final ChannelUID autoShadeChannelId = getChannelId(2);

    private Double targetPosition;

    /**
     * Create jalousie control object.
     *
     * @param handlerApi
     *            thing handler object representing the Miniserver
     * @param uuid
     *            jalousie's UUID
     * @param json
     *            JSON describing the control as received from the Miniserver
     * @param room
     *            room to which jalousie belongs
     * @param category
     *            category to which jalousie belongs
     */
    LxControlJalousie(LoxoneMiniserverHandlerApi handlerApi, LxUuid uuid, LxJsonControl json, LxContainer room,
            LxCategory category) {
        super(handlerApi, uuid, json, room, category);
        addChannel("Rollershutter", new ChannelTypeUID(BINDING_ID, MINISERVER_CHANNEL_TYPE_ROLLERSHUTTER),
                defaultChannelId, defaultChannelLabel, "Rollershutter", tags);
        addChannel("Switch", new ChannelTypeUID(BINDING_ID, MINISERVER_CHANNEL_TYPE_SWITCH), shadeChannelId,
                defaultChannelLabel + " / Shade", "Rollershutter shading", null);
        addChannel("Switch", new ChannelTypeUID(BINDING_ID, MINISERVER_CHANNEL_TYPE_SWITCH), autoShadeChannelId,
                defaultChannelLabel + " / Auto Shade", "Rollershutter automatic shading", null);
    }

    @Override
    public void handleCommand(ChannelUID channelId, Command command) throws IOException {
        if (defaultChannelId.equals(channelId)) {
            if (command instanceof PercentType) {
                moveToPosition(((PercentType) command).doubleValue() / 100);
            } else if (command instanceof UpDownType) {
                if ((UpDownType) command == UpDownType.UP) {
                    sendAction(CMD_FULL_UP);
                } else {
                    sendAction(CMD_FULL_DOWN);
                }
            } else if (command instanceof StopMoveType) {
                if ((StopMoveType) command == StopMoveType.STOP) {
                    sendAction(CMD_STOP);
                }
            }
        } else if (shadeChannelId.equals(channelId)) {
            if (command instanceof OnOffType) {
                if ((OnOffType) command == OnOffType.ON) {
                    sendAction(CMD_SHADE);
                }
            }
        } else if (autoShadeChannelId.equals(channelId)) {
            if (command instanceof OnOffType) {
                if ((OnOffType) command == OnOffType.ON) {
                    sendAction(CMD_AUTO);
                } else {
                    sendAction(CMD_NO_AUTO);
                }
            }
        }
    }

    @Override
    public State getChannelState(ChannelUID channelId) {
        if (defaultChannelId.equals(channelId)) {
            Double value = getStateValue(STATE_POSITION);
            if (value != null && value >= 0 && value <= 1) {
                // state UP or DOWN from Loxone indicates blinds are moving up or down
                // state UP in openHAB means blinds are fully up (0%) and DOWN means fully down (100%)
                // so we will update only position and not up or down states
                return new PercentType((int) (value * 100));
            }
        } else if (shadeChannelId.equals(channelId)) {
            // this channel is used to trigger shading only
            return OnOffType.OFF;
        } else if (autoShadeChannelId.equals(channelId)) {
            Double value = getStateValue(STATE_AUTO_ACTIVE);
            if (value != null) {
                return value == 1.0 ? OnOffType.ON : OnOffType.OFF;
            }
        }
        return null;
    }

    /**
     * Monitor jalousie position against desired target position and stop it if target position is reached.
     */
    @Override
    public void onStateChange(LxControlState state) {
        // check position changes
        if (STATE_POSITION.equals(state.getName()) && targetPosition != null && targetPosition > 0
                && targetPosition < 1) {
            // see in which direction jalousie is moving
            Double currentPosition = state.getValue();
            Double upValue = getStateValue(STATE_UP);
            Double downValue = getStateValue(STATE_DOWN);
            if (currentPosition != null && upValue != null && downValue != null) {
                if (((upValue == 1) && (currentPosition <= targetPosition))
                        || ((downValue == 1) && (currentPosition >= targetPosition))) {
                    targetPosition = null;
                    try {
                        sendAction(CMD_STOP);
                    } catch (IOException e) {
                        logger.debug("Error stopping jalousie when meeting target position.");
                    }
                }
            }
        } else {
            super.onStateChange(state);
        }
    }

    /**
     * Move the rollershutter (jalousie) to a desired position.
     * <p>
     * The jalousie will start moving in the desired direction based on the current position. It will stop moving once
     * there is a state update event received with value above/below (depending on direction) or equal to the set
     * position.
     *
     * @param position
     *            end position to move jalousie to, floating point number from 0..1 (0-fully closed to 1-fully open)
     * @throws IOException
     *             when something went wrong with communication
     */
    private void moveToPosition(Double position) throws IOException {
        Double currentPosition = getStateValue(STATE_POSITION);
        if (currentPosition != null && currentPosition >= 0 && currentPosition <= 1) {
            if (currentPosition > position) {
                logger.debug("Moving jalousie up from {} to {}", currentPosition, position);
                targetPosition = position;
                sendAction(CMD_FULL_UP);
            } else if (currentPosition < position) {
                logger.debug("Moving jalousie down from {} to {}", currentPosition, position);
                targetPosition = position;
                sendAction(CMD_FULL_DOWN);
            }
        }
    }
}
