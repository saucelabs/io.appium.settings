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
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
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

public class LocationService extends Service {
    private static final List<String> LOCATION_PROVIDERS = Arrays.asList(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER);
    private static final int LOCATION_UPDATE_INTERVAL_MS = 500;

    private static final String TAG = "MOCKED LOCATION SERVICE";

    private LocationManager locationManager;
    private GoogleApiClient googleApiClient;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        for (String p : new String[]{"android.permission.ACCESS_FINE_LOCATION"}) {
            if (getApplicationContext().checkCallingOrSelfPermission(p)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, String.format("Cannot mock location due to missing permission '%s'", p));
                return START_NOT_STICKY;
            }
        }
        double longitude;
        try {
            longitude = Double.valueOf(intent.getStringExtra("longitude"));
        } catch (NumberFormatException e) {
            Log.e(TAG, String.format("longitude should be a valid number. '%s' is given instead",
                    intent.getStringExtra("longitude")));
            return START_NOT_STICKY;
        }
        double latitude;
        try {
            latitude = Double.valueOf(intent.getStringExtra("latitude"));
        } catch (NumberFormatException e) {
            Log.e(TAG, String.format("latitude should be a valid number. '%s' is given instead",
                    intent.getStringExtra("latitude")));
            return START_NOT_STICKY;
        }
        Log.i(TAG,
                String.format("Setting the location from service with longitude: %.5f, latitude: %.5f",
                        longitude, latitude));

        setLocationManagerLocation(latitude, longitude, 0, 0, 0, 1);
        try {
            Thread.sleep(LOCATION_UPDATE_INTERVAL_MS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Error while sleeping");
        }

        setLocationClientLocation("fused", latitude, longitude, 0, 0, 0, 1);
        try {
            Thread.sleep(LOCATION_UPDATE_INTERVAL_MS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Error while sleeping");
        }

        return START_NOT_STICKY;
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
            Log.e(TAG, "setting mocklocations is not supported by this device", e);
        }
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


    private void prepareLocationClient() {
        if (isGooglePlayServicesAvailable() == false) {
            return;
        }

        googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .build();

        googleApiClient.connect();
    }

    private boolean isGooglePlayServicesAvailable() {
        try {
            if (GooglePlayServicesUtil.isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS) {
                Log.e("MockLocationProvider", "Google Play Services are not available.");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Google Play Services are not available.", e);

            return false;
        }
        return true;
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

                Log.i(TAG, "Setting mock location " + loc + " for provider " + provider);
                locationManager.setTestProviderLocation(provider, loc);
            } catch (java.lang.SecurityException e) {
                Log.i(TAG, "ACCESS_MOCK_LOCATION permission denied. skipping.");
            }

        }
    }

    private void setLocationClientLocation(String provider, double latitude, double longitude, double altitude, float bearing,
                                           float speed, float accuracy) {
        if (isGooglePlayServicesAvailable() == false) {
            return;
        }

        if (googleApiClient.isConnected() == false) {
            Log.e(TAG, "Google API Client is not connected, not setting location.");
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
            Log.i(TAG, "Setting mock location for location client: " + loc);
            LocationServices.FusedLocationApi.setMockLocation(googleApiClient, loc);
            LocationServices.FusedLocationApi.setMockMode(googleApiClient, true);
        } catch (java.lang.SecurityException e) {
            Log.i(TAG, "ACCESS_MOCK_LOCATION permission denied. skipping.");
        }
    }

    private void setElapsedTime(Location mockLocation) {
        if (Build.VERSION.SDK_INT > 16) {
            mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtime());
        }
    }

}
