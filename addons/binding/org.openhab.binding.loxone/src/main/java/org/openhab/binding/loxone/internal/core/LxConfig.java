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
package org.openhab.binding.loxone.internal.core;

import java.util.Map;
import java.util.Objects;

import org.openhab.binding.loxone.internal.LxServerHandlerApi;
import org.openhab.binding.loxone.internal.controls.LxControl;

import com.google.gson.annotations.SerializedName;

/**
 * A structure of JSON file http://miniserver/data/LoxAPP3.json used for parsing it with Gson library.
 *
 * @author Pawel Pieczul - initial contribution
 *
 */
public class LxConfig {

    public Map<LxUuid, LxContainer> rooms;
    @SerializedName("cats")
    public Map<LxUuid, LxCategory> categories;
    public Map<LxUuid, LxControl> controls;

    public class LxServerInfo {
        public String serialNr;
        public String location;
        public String roomTitle;
        public String catTitle;
        public String msName;
        public String projectName;
        public String remoteUrl;
        public String swVersion;
        public String macAddress;
    }

    public LxServerInfo msInfo;

    public void finalize(LxServerHandlerApi api) {
        rooms.values().removeIf(o -> (o == null || o.getUuid() == null));
        categories.values().removeIf(o -> (o == null || o.getUuid() == null));
        controls.values().removeIf(Objects::isNull);
        controls.values().forEach(c -> {
            LxContainer room = rooms.get(c.getRoomUuid());
            LxCategory category = categories.get(c.getCategoryUuid());
            c.initialize(api, room, category);
        });
    }
}
