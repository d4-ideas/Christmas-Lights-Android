package com.d4_ideas.christmaslights;

import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;

public class find_lights extends FragmentActivity {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private static final String DEBUG_TAG = "Christmas Lights";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_lights);
        setUpMapIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);

        String provider = locationManager.getBestProvider(criteria, true);

        LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                showCurrentLocation(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {

            }

            @Override
            public void onProviderEnabled(String provider) {

            }

            @Override
            public void onProviderDisabled(String provider) {

            }
        };

        locationManager.requestLocationUpdates(provider, 2000, 0, locationListener);

        Location location = locationManager.getLastKnownLocation(provider);
        if (location != null) {
            showCurrentLocation(location);
        }
    }

    private void showCurrentLocation (Location location) {
        // first update the map
        String result = null;
        String stringUrl = "http://192.168.42.18/post.php";
        String urlParameters = "fName=foo&lName=bar";
        mMap.clear();
        LatLng currentPosition = new LatLng(location.getLatitude(), location.getLongitude());

        mMap.addMarker(new MarkerOptions()
                        .position(currentPosition)
                        .snippet("Lat: " + location.getLatitude() + ", Lng: " + location.getLongitude())
                        .flat(true)
                        .title("I'm here!")
        );

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPosition, 18));

        //now update the server
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnected()) {
            // fetch data
            Log.d(DEBUG_TAG, "Looks like the network is available.");
            urlParameters = "lat=" + location.getLatitude() + "&long=" + location.getLongitude();
            new DoPostTask().execute(stringUrl, urlParameters);
        } else {
            Log.d(DEBUG_TAG, "Sorry, no network connection.");
        }
    }

    private class DoPostTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls){
            try {
                return postUrl(urls[0], urls[1]);
            } catch (IOException e) {
                return "Unable to post to web page.";
            }
        }

        @Override
        protected void onPostExecute(String result) {
            Log.d(DEBUG_TAG, result);
        }
    }

    private String postUrl(String myurl, String urlParameters) throws IOException {
        InputStream is = null;
        // Only display the first 500 characters of the retrieved
        // web page content.
        int len = 500;

        try {
            URL url = new URL(myurl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000 /* milliseconds */);
            conn.setConnectTimeout(15000 /* milliseconds */);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Length", "" + Integer.toString(urlParameters.getBytes().length));
            conn.setRequestProperty("Content-Language", "en-US");

            //Send Request
            DataOutputStream wr = new DataOutputStream (
                    conn.getOutputStream ());
            wr.writeBytes(urlParameters);
            wr.flush();
            wr.close();
            // Starts the query
            conn.connect();
            int response = conn.getResponseCode();
            Log.d(DEBUG_TAG, "The response is: " + response);
            is = conn.getInputStream();

            // Convert the InputStream into a string
            String contentAsString = readIt(is, len);
            return contentAsString;

            // Makes sure that the InputStream is closed after the app is
            // finished using it.
        }
        finally {
            if (is != null) {
                is.close();
            }
        }
    }

    public String readIt(InputStream stream, int len) throws IOException, UnsupportedEncodingException {
        Reader reader = null;
        reader = new InputStreamReader(stream, "UTF-8");
        char[] buffer = new char[len];
        reader.read(buffer);
        return new String(buffer);
    }
}