/**
 * Copyright (c) 2010-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.whistle.internal;

import org.openhab.binding.whistle.WhistleBindingProvider;
import org.openhab.core.binding.BindingConfig;
import org.openhab.core.items.Item;
import org.openhab.core.library.items.NumberItem;
import org.openhab.model.item.binding.AbstractGenericBindingProvider;
import org.openhab.model.item.binding.BindingConfigParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * This class can parse information from the generic binding format and
 * provides Whistle binding information from it. It registers as a
 * {@link WhistleBindingProvider} service as well.
 * </p>
 *
 * <p>
 * Here are some examples for valid binding configuration strings:
 * <ul>
 * <li><code>{ whistle="100000:device:battery" }</code>
 * <li><code>{ whistle="100000:activity:7" }</code>
 * <li><code>{ whistle="100000:goals:current" }</code>
 * <li><code>{ whistle="100000:goals:longest" }</code>
 * </ul>
 *
 * @author John Jore
 * @since 0.8.0
 */
public class WhistleGenericBindingProvider extends AbstractGenericBindingProvider implements WhistleBindingProvider {
    private static Logger logger = LoggerFactory.getLogger(WhistleActivator.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBindingType() {
        return "whistle";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateItemType(Item item, String bindingConfig) throws BindingConfigParseException {
        if (!(item instanceof NumberItem)) {
            throw new BindingConfigParseException(
                    "item '" + item.getName() + "' is of type '" + item.getClass().getSimpleName()
                            + "', only NumericItems are allowed - please check your *.items configuration");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processBindingConfiguration(String context, Item item, String bindingConfig)
            throws BindingConfigParseException {
        logger.debug("Creating binding for item: '{}'", item);
        super.processBindingConfiguration(context, item, bindingConfig);
        String[] configParts = bindingConfig.trim().split(":");
        if (configParts.length != 3) {
            throw new BindingConfigParseException("whistle binding configuration must contain three parts");
        }

        // Make sure we really do have username/password before proceeding
        try {
            while (WhistleBinding.username == null && WhistleBinding.password == null) {
                logger.info("Waiting here until we have username / password details from config file");
                Thread.sleep(30000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        WhistleBindingConfig config = new WhistleBindingConfig();

        // We want the deviceID to be part of the config and only add bindings for dogs we can "see"
        try {
            // Get authToken for username/password
            if (WhistleBinding.authToken == null) {
                WhistleBinding.authToken = WhistleBinding.getAuthToken(WhistleBinding.username,
                        WhistleBinding.password);
                logger.debug("got authToken '{}'", WhistleBinding.authToken);
            }
            config.dogID = configParts[0];
            config.command = configParts[1];
            config.parameter = configParts[2];

            config.deviceID = WhistleBinding.GetDogDeviceID(config.dogID, WhistleBinding.authToken);

            if (config.deviceID != null) {
                logger.debug("binding configuration dogID: '{}' deviceID:'{}' command:'{}' parameter:'{}'",
                        config.dogID, config.deviceID, config.command, config.parameter);
                addBindingConfig(item, config);
            } else {
                logger.error("Dog '{}' not found. Failed to add binding; command:'{}' parameter:'{}'", config.dogID,
                        config.command, config.parameter);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getDogID(String itemName) {
        WhistleBindingConfig config = (WhistleBindingConfig) bindingConfigs.get(itemName);
        return config != null ? config.dogID : null;
    }

    @Override
    public String getDeviceID(String itemName) {
        WhistleBindingConfig config = (WhistleBindingConfig) bindingConfigs.get(itemName);
        return config != null ? config.deviceID : null;
    }

    @Override
    public String getCommand(String itemName) {
        WhistleBindingConfig config = (WhistleBindingConfig) bindingConfigs.get(itemName);
        return config != null ? config.command : null;
    }

    @Override
    public String getParameter(String itemName) {
        WhistleBindingConfig config = (WhistleBindingConfig) bindingConfigs.get(itemName);
        return config != null ? config.parameter : null;
    }

    /*
     * This is an internal data structure to store information from the binding config
     * strings and use it to answer the requests to the Whistle binding provider.
     */
    static public class WhistleBindingConfig implements BindingConfig {
        public String dogID;
        public String deviceID;
        public String command;
        public String parameter;
    }
}
