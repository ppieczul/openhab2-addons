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

import org.openhab.binding.loxone.internal.LxServerHandlerApi;
import org.openhab.binding.loxone.internal.types.LxCategory;
import org.openhab.binding.loxone.internal.types.LxContainer;
import org.openhab.binding.loxone.internal.types.LxUuid;

/**
 * An LeftRightDigital type of control on Loxone Miniserver.
 * <p>
 * According to Loxone API documentation, LeftRightDigital control is a virtual input that is digital and has an input
 * type left-right buttons. It has no states and can only accept commands. Only left/right (which are actually equal to
 * up/down commands of {@link LxControlUpDownDigital}) on/off commands are generated. Pulse commands are not supported,
 * because of lack of corresponding feature in openHAB. Pulse can be emulated by quickly alternating between ON and OFF
 * commands.
 *
 * @author Pawel Pieczul - initial contribution
 *
 */
public class LxControlLeftRightDigital extends LxControlUpDownDigital {

    static class Factory extends LxControlInstance {
        @Override
        LxControl create(LxUuid uuid) {
            return new LxControlLeftRightDigital(uuid);
        }

        @Override
        String getType() {
            return TYPE_NAME;
        }
    }

    static final String TYPE_NAME = "leftrightdigital";

    LxControlLeftRightDigital(LxUuid uuid) {
        super(uuid);
    }

    @Override
    public void initialize(LxServerHandlerApi thingHandler, LxContainer room, LxCategory category) {
        super.initialize(thingHandler, room, category, " / Left", "Left/Right Digital: Left", " / Right",
                "Left/Right Digital: Right");

    }
}
