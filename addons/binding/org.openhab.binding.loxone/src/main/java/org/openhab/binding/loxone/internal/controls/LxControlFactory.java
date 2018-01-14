/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.loxone.internal.controls;

import java.util.HashMap;
import java.util.Map;

import org.openhab.binding.loxone.handler.LoxoneMiniserverHandlerApi;
import org.openhab.binding.loxone.internal.controls.LxControl.LxControlInstance;
import org.openhab.binding.loxone.internal.core.LxCategory;
import org.openhab.binding.loxone.internal.core.LxContainer;
import org.openhab.binding.loxone.internal.core.LxJsonApp3.LxJsonControl;
import org.openhab.binding.loxone.internal.core.LxUuid;

/**
 * A factory of controls of Loxone Miniserver.
 * It creates various types of control objects based on control type received from Miniserver.
 *
 * @author Pawel Pieczul
 *
 */
public class LxControlFactory {
    static {
        CONTROLS = new HashMap<>();
        add(new LxControlDimmer.Factory());
        add(new LxControlInfoOnlyAnalog.Factory());
        add(new LxControlInfoOnlyDigital.Factory());
        add(new LxControlJalousie.Factory());
        add(new LxControlLightController.Factory());
        add(new LxControlLightControllerV2.Factory());
        add(new LxControlPushbutton.Factory());
        add(new LxControlRadio.Factory());
        add(new LxControlSwitch.Factory());
        add(new LxControlTextState.Factory());
        add(new LxControlTimedSwitch.Factory());
    }

    private static final Map<String, LxControlInstance> CONTROLS;

    /**
     * Create a {@link LxControl} object for a control received from the Miniserver
     *
     * @param handlerApi
     *            thing handler object representing the Miniserver
     * @param uuid
     *            UUID of the control to be created
     * @param json
     *            JSON describing the control as received from the Miniserver
     * @param room
     *            Room that this control belongs to
     * @param category
     *            Category that this control belongs to
     * @return
     *         created control object or null if error
     */
    public static LxControl createControl(LoxoneMiniserverHandlerApi handlerApi, LxUuid uuid, LxJsonControl json,
            LxContainer room, LxCategory category) {
        if (json == null || json.type == null || json.name == null) {
            return null;
        }
        String type = json.type.toLowerCase();
        LxControlInstance control = CONTROLS.get(type);
        if (control != null) {
            return control.create(handlerApi, uuid, json, room, category);
        }
        return null;
    }

    private static void add(LxControlInstance control) {
        CONTROLS.put(control.getType(), control);
    }
}
