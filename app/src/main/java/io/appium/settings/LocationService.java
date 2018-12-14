/*
  Copyright 2012-present Appium Committers
  <p>
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
  <p>
  http://www.apache.org/licenses/LICENSE-2.0
  <p>
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package io.appium.settings;

import android.app.Service;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocationService extends Service {
    private static final int LOCATION_UPDATE_INTERVAL_MS = 2000;

    private static final List<String> LOCATION_PROVIDERS = Arrays.asList(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER);

    public interface Keys {
        String LONGITUDE = "longitude";
        String LATITUDE = "latitude";
//        String ALTITUDE = "altitude";
//        String BEARING = "bearing";
//        String SPEED = "speed";
    }

    public static String LOG_TAG = LocationService.class.getName();

    private final AsyncTask<Void, Void, Void> gpsLocationTask;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private LocationManager locationManager;
    private GoogleApiClient googleApiClient;

    private Intent intent;

    public LocationService() {
        Log.i(LOG_TAG, "create mock location service");

        gpsLocationTask = new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {

                while (isCancelled() == false) {
                    float lat = getFloatExtra(intent, Keys.LATITUDE, 0f);
                    float log = getFloatExtra(intent, Keys.LONGITUDE, 0f);
                    float alt = 0f;
                    float bearing = 0f;
                    float speed = 0f;

                    setLocationManagerLocation(lat, log, alt, bearing, speed, Criteria.ACCURACY_FINE);
                    setLocationClientLocation("fused", lat, log, alt, bearing, speed, Criteria.ACCURACY_FINE);

                    sleep(LOCATION_UPDATE_INTERVAL_MS);
                }

                return null;
            }
        };

    }

    @Override
    public void onCreate() {
        super.onCreate();

        prepareLocationManager();
        prepareLocationClient();
    }

    private void prepareLocationManager() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            addTestProviders();
            enableTestProviders(true);
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "setting mocklocations is not supported by this device", e);
        }
    }

    private void prepareLocationClient() {
        if (isGooglePlayServicesAvailable() == false) {
            return;
        }

        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .build();

        googleApiClient.connect();
    }

    private void addTestProviders() {
        locationManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false, false, true, true, true, Criteria.POWER_HIGH,
                Criteria.ACCURACY_FINE);
        locationManager
                .addTestProvider(LocationManager.NETWORK_PROVIDER, true, false, false, false, true, true, true, Criteria.POWER_MEDIUM,
                        Criteria.ACCURACY_FINE);
    }

    private void enableTestProviders(boolean enable) {
        for (String provider : LOCATION_PROVIDERS) {
            locationManager.setTestProviderEnabled(provider, enable);
        }
    }

    private void setLocationManagerLocation(double latitude, double longitude, double altitude, float bearing,
                                            float speed, float accuracy) {
        for (String provider : LOCATION_PROVIDERS) {
            try {
                Location loc = new Location(provider);
                loc.setLatitude(latitude);
                loc.setLongitude(longitude);
                loc.setAccuracy(accuracy);
                loc.setAltitude(altitude);
                loc.setBearing(bearing);
                loc.setSpeed(speed);
                loc.setTime(System.currentTimeMillis());
                setElapsedTime(loc);

                Log.i(LOG_TAG, "Setting mock location " + loc + " for provider " + provider);
                locationManager.setTestProviderLocation(provider, loc);
            } catch (java.lang.SecurityException e) {
                Log.i(LOG_TAG, "ACCESS_MOCK_LOCATION permission denied. skipping.");
            }

        }
    }

    private void setLocationClientLocation(String provider, double latitude, double longitude, double altitude, float bearing,
                                           float speed, float accuracy) {
        if (isGooglePlayServicesAvailable() == false) {
            return;
        }

        if (googleApiClient.isConnected() == false) {
            Log.e(LOG_TAG, "Google API Client is not connected, not setting location.");
            return;
        }

        try {
            Location loc = new Location(provider);
            loc.setLatitude(latitude);
            loc.setLongitude(longitude);
            loc.setAccuracy(accuracy);
            loc.setAltitude(altitude);
            loc.setBearing(bearing);
            loc.setSpeed(speed);
            loc.setTime(System.currentTimeMillis());
            setElapsedTime(loc);

            Log.i(LOG_TAG, "Setting mock location for location client: " + loc);
            LocationServices.FusedLocationApi.setMockLocation(googleApiClient, loc);
            LocationServices.FusedLocationApi.setMockMode(googleApiClient, true);
        } catch (java.lang.SecurityException e) {
            Log.i(LOG_TAG, "ACCESS_MOCK_LOCATION permission denied. skipping.");
        }
        sleep(LOCATION_UPDATE_INTERVAL_MS);
    }

    private void setElapsedTime(Location mockLocation) {
        if (Build.VERSION.SDK_INT > 16) {
            mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtime());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        gpsLocationTask.cancel(true);

        cleanupLocationManager();
        cleanupLocationClient();
    }

    private void cleanupLocationManager() {
        try {
            enableTestProviders(false);
            removeTestProviders();
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "setting mocklocations is not supported by this device", e);
        }
    }

    private void removeTestProviders() {
        for (String provider : LOCATION_PROVIDERS) {
            locationManager.removeTestProvider(provider);
        }
    }

    private void cleanupLocationClient() {
        if (isGooglePlayServicesAvailable() == false) {
            return;
        }

        LocationServices.FusedLocationApi.setMockMode(googleApiClient, false);
        googleApiClient.disconnect();
    }

    @Override
    public void onStart(Intent intent, int startId) {
        handleCommand(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleCommand(intent);
        return START_REDELIVER_INTENT;
    }

    private void handleCommand(Intent intent) {
        this.intent = intent;

        if (started.getAndSet(true) == false) {
            gpsLocationTask.execute();
        }
    }

    @Override
    public IBinder onBind(Intent paramIntent) {
        return null;
    }

    private boolean isGooglePlayServicesAvailable() {
        try {
            if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS) {
                Log.e(LOG_TAG, "Google Play Services are not available.");
                return false;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Google Play Services are not available.", e);

            return false;
        }
        return true;
    }

    public static float getFloatExtra(Intent intent, String key, float def) {
        try {
            if (intent.hasExtra(key)) {
                return Float.parseFloat(intent.getStringExtra(key));
            }
        } catch (NumberFormatException e) {
            Log.w(LOG_TAG, e);
        }
        return def;
    }

    public static void sleep(long time) {
        if (time <= 0) {
            return;
        }

        try {
            Thread.sleep(time);
        } catch (InterruptedException exception) {
            Log.w(LOG_TAG, exception);
        }
    }

}
