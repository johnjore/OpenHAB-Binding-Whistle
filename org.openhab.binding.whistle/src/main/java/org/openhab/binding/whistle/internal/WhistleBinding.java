/**
 * Copyright (c) 2010-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.whistle.internal;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Dictionary;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.lang.StringUtils;
import org.openhab.binding.whistle.WhistleBindingProvider;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.binding.BindingProvider;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * The Whistle Refresh Service polls the data with a configurable
 * interval and posts a new event of type ({@link DateTimeType} to the event bus.
 * The interval is 15 minutes by default and can be changed via openhab.cfg.
 *
 * @author John Jore
 * @since 0.8.0
 */
public class WhistleBinding extends AbstractActiveBinding<WhistleBindingProvider>implements ManagedService {
    private static final Logger logger = LoggerFactory.getLogger(WhistleBinding.class);
    // URL for Whistle API
    private static final String APIROOT = "https://app.whistle.com/api/";
    // Default refresh interval
    private long refreshInterval = 900000L;
    protected static String username;
    protected static String password;
    protected static String authToken;

    @Override
    protected String getName() {
        return "Whistle Refresh Service";
    }

    @Override
    protected long getRefreshInterval() {
        return refreshInterval;
    }

    /**
     * @{inheritDoc}
     */
    @Override
    public void execute() {
        if (!bindingsExist()) {
            logger.warn("There is no existing Whistle binding configuration => refresh cycle aborted!");
            return;
        }
        for (WhistleBindingProvider provider : providers) {
            logger.debug("Refresh bindings '{}'", provider.getItemNames());
            for (String itemName : provider.getItemNames()) {
                logger.debug("Update item '{}'", itemName);
                updateWhistle(provider, itemName, true);
            }
        }
    }

    private void updateWhistle(WhistleBindingProvider provider, String itemName, Boolean update) {
        logger.debug("update item '{}'", itemName);
        WhistleGenericBindingProvider WhistleBindingProvider = (WhistleGenericBindingProvider) provider;
        if (WhistleBindingProvider.providesBindingFor(itemName)) {
            String dogID = WhistleBindingProvider.getDogID(itemName);
            String deviceID = WhistleBindingProvider.getDeviceID(itemName);
            String command = WhistleBindingProvider.getCommand(itemName);
            String parameter = WhistleBindingProvider.getParameter(itemName);
            switch (command) {
                case "activity":
                    // Get activity, day x since today (today is day 0) - Activity / Goal
                    long[] dogActivity = getActivity(dogID, Integer.parseInt(parameter), authToken);
                    logger.debug("Activity the last '{}' days. Active: '{}' / Goal: '{}'", parameter, dogActivity[0],
                            dogActivity[1]);
                    if (update) {
                        eventPublisher.postUpdate(itemName, new DecimalType(dogActivity[0]));
                    }
                    break;
                case "target":
                    // Get activity, day x since today (today is day 0) - Activity / Goal
                    long[] dogGoal = getActivity(dogID, Integer.parseInt(parameter), authToken);
                    logger.debug("Activity the last '{}' days. Active: '{}' / Goal: '{}'", parameter, dogGoal[0],
                            dogGoal[1]);
                    if (update) {
                        eventPublisher.postUpdate(itemName, new DecimalType(dogGoal[1]));
                    }
                    break;
                case "device":
                    switch (parameter) {
                        case "battery":
                            Double battResult = Double.parseDouble(getDeviceInfo(deviceID, parameter, authToken));
                            logger.debug("Battery: '{}'", battResult);
                            if (update) {
                                eventPublisher.postUpdate(itemName, new DecimalType(battResult));
                            }
                            break;
                        default:
                            logger.debug("Unknown parameter '{}'", parameter);
                            break;
                    }
                    break;
                case "goals":
                    // Current streak / longest
                    try {
                        Integer goalsResult = getGoals(dogID, parameter, authToken);
                        logger.debug("Goal streak: '{}'", goalsResult);
                        if (update) {
                            eventPublisher.postUpdate(itemName, new DecimalType(goalsResult));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case "averageactive":
                    int[] dogAverageActive = getAverages(dogID, Integer.parseInt(parameter), authToken);
                    logger.debug("Average last '{}' days - Active: '{}', Rest: '{}'", parameter, dogAverageActive[0],
                            dogAverageActive[1]);
                    if (update) {
                        eventPublisher.postUpdate(itemName, new DecimalType(dogAverageActive[0]));
                    }
                    break;
                case "averagerest":
                    int[] dogAverageRest = getAverages(dogID, Integer.parseInt(parameter), authToken);
                    logger.debug("Average last '{}' days - Active: '{}', Rest: '{}'", parameter, dogAverageRest[0],
                            dogAverageRest[1]);
                    if (update) {
                        eventPublisher.postUpdate(itemName, new DecimalType(dogAverageRest[1]));
                    }
                    break;
                default:
                    logger.debug("Unknown command '{}'", command);
            }
        }
    }

    // Averages
    private long[] getActivity(String DogID, int Days, String authToken) {
        logger.debug("getActivity() - '{}' '{}'", DogID, Days);
        try {
            // Get days since epoch
            long DaysSinceEpoch = System.currentTimeMillis() / 1000 / 60 / 60 / 24 - Days;
            logger.debug("Activity for days since Epoch: " + DaysSinceEpoch);
            int D = Days + 1;
            // Get activity
            JsonArray response = getArrayData("dogs/" + DogID + "/dailies?count=" + D, authToken);
            for (int i = 0; i < response.size(); i++) {
                JsonObject jobject = response.get(i).getAsJsonObject();
                if (DaysSinceEpoch == jobject.get("day_number").getAsLong()) {
                    long[] activityArray = new long[2];
                    activityArray[0] = jobject.get("minutes_active").getAsLong();
                    activityArray[1] = jobject.get("activity_goal").getAsLong();
                    logger.debug("Active: " + activityArray[0] + ", Goal: " + activityArray[1]);
                    return activityArray;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // Averages
    private int[] getAverages(String DogID, int days, String authToken) {
        logger.debug("getAverages() - '{}' '{}'", DogID, days);
        try {
            // Starting date
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, -days);
            Date todate = cal.getTime();
            String fromdate = dateFormat.format(todate);
            logger.debug("fromdate: " + fromdate);
            // Get device information
            JsonArray response = getArrayData("dogs/" + DogID + "/stats/daily_totals/?start_time=" + fromdate,
                    authToken);
            // Totals
            int[] activityArray = new int[2];
            // Loop through the data, but don't include today's data
            for (int i = 0; i < response.size() - 1; i++) {
                JsonObject jobject = response.get(i).getAsJsonObject();
                activityArray[0] += jobject.get("minutes_active").getAsInt();
                activityArray[1] += jobject.get("minutes_rest").getAsInt();
                logger.trace("Active / Rest, '{}' / '{}'", jobject.get("minutes_active").getAsInt(),
                        jobject.get("minutes_rest").getAsInt());
            }
            // Average
            activityArray[0] = activityArray[0] / days;
            activityArray[1] = activityArray[1] / days;
            logger.debug("Active: " + activityArray[0] + ", Rest: " + activityArray[1]);
            return activityArray;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private Integer getGoals(String dogID, String parameter, String authToken) throws Exception {
        // logger.debug("Looking for goal information on dog Id: '" + dogId + "'");
        logger.info("getGoals() - '{}' '{}'", dogID, parameter);
        try {
            JsonObject dogGoals = getObjectData("dogs/" + dogID + "/stats/goals", authToken);
            switch (parameter) {
                case "current":
                    Integer currentStreak = dogGoals.get("current_streak").getAsInt();
                    logger.debug("Current Streak: " + currentStreak);
                    return currentStreak;
                case "longest":
                    Integer longestStreak = dogGoals.get("longest_streak").getAsInt();
                    logger.debug("Longest Streak: " + longestStreak);
                    return longestStreak;
                default:
                    logger.warn("Unknown parameter; ''{}", parameter);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getDeviceInfo(String deviceID, String parameter, String authToken) {
        logger.info("getDeviceInfo() - '{}' '{}'", deviceID, parameter);
        try {
            // Get device information
            JsonObject deviceInfo = GetDeviceInformation(deviceID, authToken);
            // Round battery level to 2 decimal places
            double f = Double.parseDouble(deviceInfo.get("battery_level").getAsString());
            switch (parameter) {
                case "battery":
                    String batteryLevel = String.format("%.2f", f);
                    logger.debug("Battery Level: " + batteryLevel);
                    return batteryLevel;
                case "lastcheckin":
                    String lastCheckIn = deviceInfo.get("last_check_in").getAsString();
                    logger.debug("Last Check In: " + lastCheckIn);
                    return lastCheckIn;
                default:
                    logger.warn("Unknown parameter; '{}'", parameter);
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // HTTP POST request to get Authentication Token from Whistle
    protected static String getAuthToken(String username, String password) throws Exception {
        logger.debug("Using username: '{}' / password: '{}'", username, password);

        URL obj = new URL(APIROOT + "tokens.json");
        HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();
        // add request headers and parameters
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("User-Agent", "WhistleApp/102 (iPhone; iOS 7.0.4; Scale/2.00)");
        con.setRequestMethod("POST");
        String urlParameters = "{\"password\":\"" + password + "\",\"email\":\"" + username
                + "\",\"app_id\":\"com.whistle.WhistleApp\"}";
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(urlParameters);
        wr.flush();
        wr.close();
        if (con.getResponseCode() != 200) {
            logger.error("Username / password combination didn't work. Failed to get AuthenticationToken");
            return null;
        }
        // Read the buffer
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String response = in.readLine();
        in.close();
        // Parse the data and return the token
        JsonObject jobj = new Gson().fromJson(response, JsonObject.class);
        return jobj.get("token").getAsString();
    }

    // Generic function when returning a JsonArray
    static private JsonArray getArrayData(String APIURL, String authToken) throws Exception {
        String response = GetData(new URL(APIROOT + APIURL), authToken);
        JsonArray jobj = new Gson().fromJson(response, JsonArray.class);
        return jobj;
    }

    // Generic function when returning a JsonObject
    static private JsonObject getObjectData(String APIURL, String authToken) throws Exception {
        String response = GetData(new URL(APIROOT + APIURL), authToken);
        JsonObject jobj = new Gson().fromJson(response, JsonObject.class);
        return jobj;
    }

    // Generic function to get data from Whistle
    static private String GetData(URL obj, String authToken) throws Exception {
        HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();
        // add request headers and parameters
        con.setDoOutput(true);
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("User-Agent", "WhistleApp/102 (iPhone; iOS 7.0.4; Scale/2.00)");
        con.setRequestProperty("X-Whistle-AuthToken", authToken);
        // System.out.println("Response Code : " + con.getResponseCode());
        if (con.getResponseCode() != 200) {
            logger.error("Failed to get requested data, response code: '{}'", con.getResponseCode());
            return null;
        }
        // Get the data
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String response = in.readLine();
        in.close();
        return response;
    }

    // Enumerate available dogs
    static protected String GetDogDeviceID(String DogId, String token) throws Exception {
        logger.debug("Looking for DogId: '" + DogId + "'");
        JsonArray response = getArrayData("dogs.json", token);
        // logger.trace("JSON Array size is : " + response.size());
        // logger.trace("JSONArray: " + response);
        for (int i = 0; i < response.size(); i++) {
            // logger.debug("On loop " + i + ", found " + response.get(i));
            JsonObject jobject = response.get(i).getAsJsonObject();
            String foundDogId = jobject.get("id").getAsString();
            logger.info("Found DogID: '{}' / '{}'", foundDogId, jobject.get("name").getAsString());
            // A match?
            if (DogId.equals(foundDogId)) {
                logger.debug("Match found: DogId: '" + DogId + "', foundDogId: '" + foundDogId + "'");
                return jobject.get("device_id").getAsString();
            }
        }
        // No match found
        return null;
    }

    // GetDeviceInformation
    static private JsonObject GetDeviceInformation(String deviceId, String token) throws Exception {
        logger.debug("Looking for information on device Id: '" + deviceId + "'");
        return getObjectData("devices/" + deviceId + ".json", token);
    }

    protected void addBindingProvider(WhistleBindingProvider bindingProvider) {
        super.addBindingProvider(bindingProvider);
    }

    protected void removeBindingProvider(WhistleBindingProvider bindingProvider) {
        super.removeBindingProvider(bindingProvider);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void updated(Dictionary config) throws ConfigurationException {
        if (config != null) {
            String usernameString = (String) config.get("username");
            if (StringUtils.isNotBlank(usernameString)) {
                username = usernameString;
            }
            String passwordString = (String) config.get("password");
            if (StringUtils.isNotBlank(passwordString)) {
                password = passwordString;
            }
            String refreshIntervalString = (String) config.get("refresh");
            if (StringUtils.isNotBlank(refreshIntervalString)) {
                refreshInterval = Long.parseLong(refreshIntervalString);
            }
            setProperlyConfigured(true);
            logger.debug("Loaded configuration - '" + username + "', refresh: '" + refreshInterval + "'");
        }
    }

    @Override
    public void bindingChanged(BindingProvider provider, String itemName) {
        try {
            logger.debug("bindingChanged - '{}'", itemName);
            WhistleGenericBindingProvider WhistleBindingProvider = (WhistleGenericBindingProvider) provider;
            if (WhistleBindingProvider.providesBindingFor(itemName)) {
                updateWhistle(WhistleBindingProvider, itemName, false);
            }
            super.bindingChanged(provider, itemName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
