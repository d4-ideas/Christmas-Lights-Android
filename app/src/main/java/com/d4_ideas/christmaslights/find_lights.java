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
import android.view.View;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.maps.android.geojson.GeoJsonFeature;
import com.google.maps.android.geojson.GeoJsonLayer;
import com.google.maps.android.geojson.GeoJsonPointStyle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class find_lights extends FragmentActivity {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private static final String DEBUG_TAG = "Christmas Lights";
    public Location ourLocation;
    private static final String POSTENDPOINT = "http://192.168.42.18/post.php";
    private static final String GETENDPOINT = "http://192.168.42.18/get.php";
    JSONArray markers = null;
    private GeoJsonLayer mLayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(DEBUG_TAG, "onCreate");
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
        Log.d(DEBUG_TAG, "setUpMapIfNeeded.");
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
        Log.d(DEBUG_TAG, "starting setUpMap");
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition cameraPosition) {
                getMarkers();
            }
        });

        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);

        String provider = locationManager.getBestProvider(criteria, true);

        LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location ourLocation) {
                Log.d(DEBUG_TAG, "onLocationChanged.");
                showCurrentLocation(ourLocation);
                getMarkers();
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };

        locationManager.requestLocationUpdates(provider, 2000, 0, locationListener);

        ourLocation = locationManager.getLastKnownLocation(provider);
        if (ourLocation != null) {
            showCurrentLocation(ourLocation);
        }
    }
    private void getMarkers() {
        Log.d(DEBUG_TAG, "getMarkers");
        String urlParameters;
        String url;
        final VisibleRegion visibleRegion=mMap.getProjection().getVisibleRegion();

        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (null == ourLocation){
            Log.d(DEBUG_TAG, "We have no location!");
        } else if (networkInfo != null && networkInfo.isConnected()) {
            // fetch data
            Log.d(DEBUG_TAG, "Looks like the network is available.");
            Log.d(DEBUG_TAG, visibleRegion.latLngBounds.northeast.toString());
            urlParameters =
                       "N=" + visibleRegion.latLngBounds.northeast.latitude
                    + "&W=" + visibleRegion.latLngBounds.northeast.longitude
                    + "&S=" + visibleRegion.latLngBounds.southwest.latitude
                    + "&E=" + visibleRegion.latLngBounds.southwest.longitude;
            url = GETENDPOINT + "?" + urlParameters;
            JsonObjectRequest jsonRequest = new JsonObjectRequest
                    (Request.Method.GET, url, null, new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            Log.d(DEBUG_TAG, response.toString());
                            mLayer = new GeoJsonLayer(getMap(), response);
                            decorateMarkers();
                            mLayer.addLayerToMap();
                        }
                    }, new Response.ErrorListener() {

                        @Override
                        public void onErrorResponse(VolleyError error) {
                            error.printStackTrace();
                            Log.d(DEBUG_TAG, "something went wrong requesting json.");
                        }
                    });
            Volley.newRequestQueue(this).add(jsonRequest);
        } else {
            Log.d(DEBUG_TAG, "Sorry, no network connection.");
        }
    }
    public void upVote(View view) {
        Log.d(DEBUG_TAG, "upVote");
        vote("up");
    }
    public void downVote(View view) {
        Log.d(DEBUG_TAG, "downVote");
        vote("down");
    }
    private void vote(final String theVote){
        Log.d(DEBUG_TAG, "Voting");
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (null == ourLocation){
            Log.d(DEBUG_TAG, "We have no location!");
        } else if (networkInfo != null && networkInfo.isConnected()) {
            // fetch data
            Log.d(DEBUG_TAG, "Looks like the network is available.");

            // Instantiate the RequestQueue.
            RequestQueue queue = Volley.newRequestQueue(this);

            // Request a string response from the provided URL.
            StringRequest stringRequest = new StringRequest(Request.Method.POST, POSTENDPOINT,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            // Display the first 500 characters of the response string.
                            Log.d(DEBUG_TAG, "Response is: "+ response);
                        }
                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Log.d(DEBUG_TAG, "post did not work");
                    Log.d(DEBUG_TAG, error.toString());
                }
            }){
                protected Map<String, String> getParams() throws com.android.volley.AuthFailureError {
                    Map<String, String> params = new HashMap<String, String>();
                    params.put("lat", String.valueOf(ourLocation.getLatitude()));
                    params.put("long", String.valueOf(ourLocation.getLongitude()));
                    params.put("vote", theVote);
                    return params;
                };
            };
            // Add the request to the RequestQueue.
            queue.add(stringRequest);
        } else {
            Log.d(DEBUG_TAG, "Sorry, no network connection.");
        }
    }
    private void showCurrentLocation (Location location) {
        Log.d(DEBUG_TAG, "showCurrentLocation");
        mMap.clear();
        LatLng currentPosition = new LatLng(location.getLatitude(), location.getLongitude());

        mMap.addMarker(new MarkerOptions()
                        .position(currentPosition)
                        .snippet("Lat: " + location.getLatitude() + ", Lng: " + location.getLongitude())
                        .flat(true)
                        .title("I'm here!")
        );

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPosition, 18));
    }
    protected GoogleMap getMap() {
        setUpMapIfNeeded();
        return mMap;
    }
    private void decorateMarkers() {
        // Iterate over all the features stored in the layer
        for (GeoJsonFeature feature : mLayer.getFeatures()) {
            // Check if the magnitude property exists
            if (feature.hasProperty("votes")) {
                float votes = Float.parseFloat(feature.getProperty("votes"));

                // Get the icon for the feature
                BitmapDescriptor pointIcon = BitmapDescriptorFactory.defaultMarker();

                // Create a new point style
                GeoJsonPointStyle pointStyle = new GeoJsonPointStyle();

                // Set options for the point style
                pointStyle.setIcon(pointIcon);
                pointStyle.setAlpha(votes);

                // Assign the point style to the feature
                feature.setPointStyle(pointStyle);
            }
        }
    }
}