/**
 * Copyright (c) 2010-2017 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.loxone.internal;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.type.DynamicStateDescriptionProvider;
import org.eclipse.smarthome.core.types.StateDescription;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dynamic channel state description provider.
 * Overrides the state description for the controls, which receive its configuration in the runtime.
 *
 * @author Pawel Pieczul - Initial contribution
 */
@NonNullByDefault
@Component(service = { DynamicStateDescriptionProvider.class,
        LoxoneDynamicStateDescriptionProvider.class }, immediate = true)
public class LoxoneDynamicStateDescriptionProvider implements DynamicStateDescriptionProvider {

    private Map<ChannelUID, StateDescription> descriptions = new HashMap<>();
    private Logger logger = LoggerFactory.getLogger(LoxoneDynamicStateDescriptionProvider.class);

    /**
     * Add a state description for a channel. This description will be used when preparing the channel state by
     * the framework for presentation.
     *
     * @param channelUID
     *            channel UID
     * @param description
     *            state description for the channel
     */
    public void addDescription(ChannelUID channelUID, StateDescription description) {
        logger.debug("Adding state description for channel {}", channelUID);
        synchronized (descriptions) {
            descriptions.put(channelUID, description);
        }
    }

    /**
     * Clear all registered state descriptions
     */
    public void removeAllDescriptions() {
        logger.debug("Removing all state descriptions");
        synchronized (descriptions) {
            descriptions.clear();
        }
    }

    @Override
    public @Nullable StateDescription getStateDescription(Channel channel,
            @Nullable StateDescription originalStateDescription, @Nullable Locale locale) {
        synchronized (descriptions) {
            StateDescription description = descriptions.get(channel.getUID());
            if (description != null) {
                logger.debug("Providing state description for channel {}", channel.getUID());
            }
            return description;
        }
    }
}
