package com.example.tokentravmarker;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.location.Location;
import android.media.MediaPlayer;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.mapboxsdk.style.sources.Source;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconOffset;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, PermissionsListener, MapboxMap.OnMapClickListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    // Map variables
    private MapView mapView;
    private MapboxMap mapboxMap;
    private Style style;

    // Permissions
    private PermissionsManager permissionsManager;

    // Dialogs
    Dialog myDialog;

    // Location Variables
    private GoogleApiClient googleApiClient;
    private Location location;

    // Used to request a quality of service for location updates from the FusedLocationProviderAPI
    private LocationRequest locationRequest;

    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;
    private static final long UPDATE_INTERVAL = 5000, FASTEST_INTERVAL = 5000; // = 5 seconds

    // Device Location Variables
    private double deviceLat;
    private double deviceLong;

    // Click Location Variables
    private double tokenLat;
    private double tokenLong;

  @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, getString(R.string.access_token));
        setContentView(R.layout.activity_main);
        myDialog = new Dialog(this);

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        // Build google API Location Services API
        googleApiClient = new GoogleApiClient.Builder(this).
                addApi(LocationServices.API).
                addConnectionCallbacks(this).
                addOnConnectionFailedListener(this).build();

        // Navigation Bar
        BottomNavigationView bottomNavigationView = (BottomNavigationView) findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {

            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.action_recents:
                        Toast.makeText(MainActivity.this, "Recents", Toast.LENGTH_SHORT).show();
                        break;
                    case R.id.action_favorites:
                        Toast.makeText(MainActivity.this, "Favorites", Toast.LENGTH_SHORT).show();
                        break;
                    case R.id.action_nearby:
                        Toast.makeText(MainActivity.this, "Nearby", Toast.LENGTH_SHORT).show();
                        break;
                }
                return true;
            }
        });
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();

        if (googleApiClient != null) {
            googleApiClient.connect();
        }
    }
    



 /**
     * MAPBOX METHODS
     *
     * @onMapReady - adds Tokens to map, sets style, adds Location component
     * 
     *@onMapClick - checks if token is clicked
     *
     * 
     */
  

    @Override
    public void onMapReady(@NonNull final MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;

        mapboxMap.setStyle(Style.MAPBOX_STREETS,
                new Style.OnStyleLoaded() {
                    @Override
                    public void onStyleLoaded(@NonNull Style style) {
                        this.style = style;
                        enableLocationComponent(style);
                        addTokens();

                        mapboxMap.addOnMapClickListener(MainActivity.this);
                    }
                });
    }

    @Override
    public boolean onMapClick(@NonNull LatLng point) {
        final PointF screenClick = mapboxMap.getProjection().toScreenLocation(point);
        List <Feature> features = mapboxMap.queryRenderedFeatures(screenClick, "coffee-layer", "beer-layer", "nature-layer");

        if (!features.isEmpty()) {
            Feature selectedFeature = features.get(0);
            String title = selectedFeature.getStringProperty("title");
            Toast.makeText(this, title, Toast.LENGTH_SHORT).show();

            tokenLat = (Double) selectedFeature.getNumberProperty("latitude");
            tokenLong = (Double) selectedFeature.getNumberProperty("longitude");

            showPopup();
        }
        return false;
    }


// Creates a Dialog prompting user to Collect Token 

    private void showPopup() {
        myDialog.setContentView(R.layout.activity_pop);
        Button collectToken = (Button) myDialog.findViewById(R.id.collect_button);
        Button getDirections = (Button) myDialog.findViewById(R.id.directions_button);
        Button favorite = (Button) myDialog.findViewById(R.id.favorite_button);
        myDialog.show();

        collectToken.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                collectToken();
            }
        });
        
        
         getDirections.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getDirections();
            }
        });
        
         favoriteToken.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                favoriteToken();
            }
        });
                
    }


    public void collectToken() {
        myDialog.cancel();

        if (checkLocation(deviceLong, deviceLat, tokenLong, tokenLat)) {
            final MediaPlayer mp = MediaPlayer.create(this, R.raw.collectnoise);
            mp.start();
            Toast.makeText(this, "Token Collected", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Your Location does not match", Toast.LENGTH_SHORT).show();
        }

    }
    
    
   public void getDirections () { }
    

// Add Tokens to map
    private void addTokens() {
        String geoJson = loadJSONFromAsset("data.geojson");
        String beerJson = loadJSONFromAsset("beerdata.geojson");
        String natureJson = loadJSONFromAsset("nature.geojson");

        FeatureCollection featureCollection = FeatureCollection.fromJson(geoJson);
        FeatureCollection beerFeatureCollection = FeatureCollection.fromJson(beerJson);
        FeatureCollection natureFeatureCollection = FeatureCollection.fromJson(natureJson);

        Source source = new GeoJsonSource("my.data.source", featureCollection);
        Source beerSource = new GeoJsonSource("beer.source", beerFeatureCollection);
        Source natureSource = new GeoJsonSource("nature.source", natureFeatureCollection);

        style.addSource(source);
        style.addSource(beerSource);
        style.addSource(natureSource);

        style.addImage("coffee_token", BitmapFactory.decodeResource(
                this.getResources(), R.drawable.coffeetoken));
        style.addImage("beer_token", BitmapFactory.decodeResource(
                this.getResources(), R.drawable.beertoken));
        style.addImage("nature_token", BitmapFactory.decodeResource(
                this.getResources(), R.drawable.naturetoken));

        SymbolLayer coffeeLayer = new SymbolLayer("coffee-layer", "my.data.source");
        SymbolLayer beerLayer = new SymbolLayer("beer-layer", "beer.source");
        SymbolLayer natureLayer = new SymbolLayer("nature-layer", "nature.source");

        style.addLayer(coffeeLayer
                .withProperties(PropertyFactory.iconImage("coffee_token"), iconOffset(new Float[]{0f, -9f})));
        style.addLayer(beerLayer
                .withProperties(PropertyFactory.iconImage("beer_token"), iconOffset(new Float[]{0f, -9f})));
        style.addLayer(natureLayer
                .withProperties(PropertyFactory.iconImage("nature_token"), iconOffset(new Float[]{0f, -9f})));
    }


// Load JSON as String from a JSON file

    private String loadJSONFromAsset(String fileName) {
        String json;
        try {
            InputStream is = this.getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }

// Check Device Location to see if it matches Token Location
    private boolean checkLocation(double deviceLong, double deviceLat, double tokenLong, double tokenLat){

       if(Math.abs(deviceLong-tokenLong) < 0.003 && (Math.abs(deviceLat-tokenLat) < 0.003)){
            return true;
        }
        return false;
    }



// Check Nature Token

    private boolean checkNatureLocation(double deviceLong, double deviceLat, double tokenLong, double tokenLat){

        if(deviceLong == tokenLong && deviceLat == tokenLat){

        }

        return true;
    }




    /**
     * LOCATION METHODS
     *
     * @enableLocationComponent - Checks if permissions are enable / requests permissions
     * - Sets location component on map / tracks user location
     *
     *
     * Permissions Standard Override Methods
     */

    @SuppressWarnings({"MissingPermission"})
    private void enableLocationComponent(@NonNull Style loadedMapStyle) {

        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {

            LocationComponent locationComponent = mapboxMap.getLocationComponent();

            locationComponent.activateLocationComponent(
                    LocationComponentActivationOptions.builder(this, loadedMapStyle).build());

            // Enable to make component visible
            locationComponent.setLocationComponentEnabled(true);

            // Set the component's camera mode
            locationComponent.setCameraMode(CameraMode.TRACKING);

            // Set the component's render mode
            locationComponent.setRenderMode(RenderMode.COMPASS);
        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(this, R.string.user_location_permission_explanation, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            mapboxMap.getStyle(new Style.OnStyleLoaded() {
                @Override
                public void onStyleLoaded(@NonNull Style style) {
                    enableLocationComponent(style);
                }
            });
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show();
            finish();
        }
    }












    /**
     * Standard Override Activity Methods
     */


    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapboxMap != null) {
            mapboxMap.removeOnMapClickListener(this);
        }
        mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }














    /**
     * Google API Connection Callbacks Override Methods
     *
     * onConnected - Override
     *
     * startLocationUpdates() - Then, we start to request for location updates in a startLocationUpdates dedicated method.
     * In this method, we build a LocationRequest object detailing the priority,
     * the refresh interval and the fastest interval for location updates.
     *
     *
     * checkPlayServices() -
     */

    @Override
    public void onConnected(@Nullable Bundle bundle) {

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // If permissions are set, we can get last location
        location = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);

        deviceLat = location.getLatitude();
        deviceLong = location.getLongitude();

        startLocationUpdates();
    }

    private void startLocationUpdates() {
        locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(UPDATE_INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "You need to enable permissions to display location !", Toast.LENGTH_SHORT).show();
        }

        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
    }

    private boolean checkPlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);

        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST);
            } else {
                finish();
            }

            return false;
        }

        return true;
    }















    /**
     * Google API onConnectionFailed Ovveride methods
     *
     * - Does Nothing currently
     */

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }














    /**
     * On Location changed - updates deviceLat / deviceLong variables when location changes
     *
     * @param location
     */
    @Override
    public void onLocationChanged(Location location) {
        deviceLat = location.getLatitude();
        deviceLong = location.getLongitude();

    }
}
