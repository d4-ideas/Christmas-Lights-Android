package com.d4_ideas.christmaslights;

import android.content.Context;
import android.content.IntentSender;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
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
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.VisibleRegion;
import com.google.maps.android.heatmaps.HeatmapTileProvider;
import com.google.maps.android.heatmaps.WeightedLatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class find_lights extends FragmentActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private UiSettings mUiSettings;
    private static final String DEBUG_TAG = "Christmas Lights";
    public Location ourLocation;
    private static final String POSTENDPOINT = "http://192.168.1.3:3000/points";
    private static final String GETENDPOINT = "http://192.168.1.3:3000/points";
    private List<WeightedLatLng> thePoints = new ArrayList<WeightedLatLng>();
    private List<WeightedLatLng> lastPoints = new ArrayList<WeightedLatLng>();
    private HeatmapTileProvider mProvider;
    private TileOverlay mOverlay;
    private GoogleApiClient mGoogleApiClient;
    public static final long UPDATE_INTERVAL_IN_MILLISECONDS = 10000;
    public static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private LocationRequest mLocationRequest;
    private String id = Build.SERIAL;
    // don't request markers more often than delay milliseconds
    private int delay = 60000;
    private long lastUpdate = 0;
    private VisibleRegion lastVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(DEBUG_TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_lights);
        setUpMapIfNeeded();
        buildGoogleApiClient();
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(1 * 1000); // 1 second, in milliseconds
    }
    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
        mGoogleApiClient.connect();
    }
    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
    }    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            Log.i(DEBUG_TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
        }
        Log.i(DEBUG_TAG, "Connection failed: ConnectionResult.getErrorCode() = " + connectionResult.getErrorCode());
    }
    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(DEBUG_TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }
    public void onConnected(Bundle connectionHint) {
        Log.d(DEBUG_TAG, "onConnected.");
        // Provides a simple way of getting a device's location and is well suited for
        // applications that do not require a fine-grained location and that do not need location
        // updates. Gets the best and most recent location currently available, which may be null
        // in rare cases when a location is not available.
        ourLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        if (ourLocation != null){
            showCurrentLocation(ourLocation);
            Log.d(DEBUG_TAG, ourLocation.toString());
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(ourLocation.getLatitude(), ourLocation.getLongitude()), 18));
        }
    }
    @Override
    public void onLocationChanged(Location location) {
        ourLocation = location;
        Log.d(DEBUG_TAG, "onLocationChanged.");
        showCurrentLocation(ourLocation);
        getMarkers();
    }    /**
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
        mMap.setMyLocationEnabled(true);
        mMap.setPadding(0,0,0,160);
        mUiSettings = mMap.getUiSettings();
        mUiSettings.setCompassEnabled(true);
        mUiSettings.setZoomControlsEnabled(true);
        mMap.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition cameraPosition) {
                getMarkers();
            }
        });

    }
    private void getMarkers() {
        String urlParameters;
        String url;
        final VisibleRegion visibleRegion=mMap.getProjection().getVisibleRegion();
        if (
                (System.currentTimeMillis() < lastUpdate + delay)
            &&
                (visibleRegion.equals(lastVisible))
            ){
            Log.d(DEBUG_TAG,"Too early to get markers");
            return;
        }
        else {
            Log.d(DEBUG_TAG,"Getting a new set of markers");
        }
        lastUpdate = System.currentTimeMillis();
        lastVisible = visibleRegion;
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (null == ourLocation){
            Log.d(DEBUG_TAG, "We have no location!");
        } else if (networkInfo != null && networkInfo.isConnected()) {
            // fetch data
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
                            thePoints=convertJson(response);
                            if (thePoints.equals(lastPoints)){
                                mProvider=new HeatmapTileProvider.Builder().weightedData(thePoints).build();
                                mOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider));
                                mOverlay.clearTileCache();
                                lastPoints = thePoints;
                            }
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
                    params.put("id", id);
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

//        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentPosition, 18));
    }
    protected GoogleMap getMap() {
        setUpMapIfNeeded();
        return mMap;
    }
    private List<WeightedLatLng> convertJson (JSONObject geoJsonObject){
        JSONArray intermediateObject;
        double intermediateIntensity;
        List<WeightedLatLng> ourPoints = new ArrayList<WeightedLatLng>();
        JSONArray array = null;
        try {
            array = geoJsonObject.getJSONArray("features");
            for (int i = 0; i < array.length(); i++){
                intermediateObject = array.getJSONObject(i).getJSONObject("geometry").getJSONArray("coordinates");
                intermediateIntensity=array
                        .getJSONObject(i)
                        .getJSONObject("properties")
                        .getDouble("votes");
                ourPoints.add(
                        new WeightedLatLng(
                                new LatLng(intermediateObject.getDouble(1), intermediateObject.getDouble(0)),
                                intermediateIntensity));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return ourPoints;
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }
}