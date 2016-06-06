
/**
 * Copyright (c) 2010-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.whistle;

import org.openhab.core.binding.BindingProvider;

/**
 * This interface is implemented by classes that can provide mapping information
 * between openHAB items and Whistle data.
 *
 * Implementing classes should register themselves as a service in order to be
 * taken into account.
 *
 * @author John Jore
 * @since 0.8.0
 */
public interface WhistleBindingProvider extends BindingProvider {
    public String getDogID(String itemName);

    public String getDeviceID(String itemName);

    public String getCommand(String itemName);

    public String getParameter(String itemName);
}
