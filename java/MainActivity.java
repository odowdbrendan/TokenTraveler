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
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.api.directions.v5.MapboxDirections;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
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
import com.mapbox.mapboxsdk.style.layers.Layer;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;

import static android.view.View.VISIBLE;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconOffset;

import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.mapboxsdk.style.sources.Source;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


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

    // Used to specify the intervals for which location updates occur
    private static final long UPDATE_INTERVAL = 5000, FASTEST_INTERVAL = 5000; // = 5 seconds

    // Device Location Variables
    private double deviceLat;
    private double deviceLong;

    // Selected Token Variables
    private Feature selectedFeature;
    private double tokenLat;
    private double tokenLong;
    private String collectedTokenTitle;
    private String pointOfInterest;

    // Specify the start and end points for directions
    private Point origin, destination;

    // MapboxDirections and DirectionsRoute variables
    private DirectionsRoute currentRoute;
    private NavigationMapRoute navigationMapRoute;

    // Collected Tokens and Favourited Tokens arrays
    public ArrayList<String> collectedTokens = new ArrayList<String>();
    public ArrayList<String> favouriteTokens = new ArrayList<String>();

    // Collected Tokens Counter Variables
    public int coffeeCount;
    public int beerCount;
    public int natureCount;

    private Button directionsButton;

    @Override
    public void onMapReady(@NonNull final MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;

        mapboxMap.setStyle(Style.MAPBOX_STREETS,  // sets the map style to include streets, street names, etc.
                new Style.OnStyleLoaded() {
                    @Override
                    public void onStyleLoaded(@NonNull Style style) {
                        MainActivity.this.style = style;
                        enableLocationComponent(style);  // Adds device location to map
                        addTokens();  // Adds tokens to map

                        mapboxMap.addOnMapClickListener(MainActivity.this);

                        directionsButton = findViewById(R.id.startButton);
                        directionsButton.setOnClickListener(new View.OnClickListener() {
    @Override
    public void onClick(View v) {  // Opens navigation view if Start Navigation button is clicked
        NavigationLauncherOptions navigateLauncher = NavigationLauncherOptions.builder()
                .directionsRoute(currentRoute)
                .build();
        NavigationLauncher.startNavigation(MainActivity.this, navigateLauncher);
    }
                        });
                    }
                });
    }

    @Override
    public boolean onMapClick(@NonNull LatLng point) {
        final PointF screenClick = mapboxMap.getProjection().toScreenLocation(point);
        // Queries token layers for user click
        List<Feature> features = mapboxMap.queryRenderedFeatures(screenClick, "coffee-layer", "beer-layer", "nature-layer");

        if (!features.isEmpty()) {
            selectedFeature = features.get(0);  // gets the first token on user click

            collectedTokenTitle = selectedFeature.getStringProperty("title");
            Toast.makeText(this, collectedTokenTitle, Toast.LENGTH_SHORT).show(); // sends message to UI with token name

            tokenLat = (Double) selectedFeature.getNumberProperty("latitude");  // updates token lat/long variable
            tokenLong = (Double) selectedFeature.getNumberProperty("longitude");
            pointOfInterest = selectedFeature.getStringProperty("poi");

            showPopup();
        }
        return false;
    }

    /**
     * This class displays a popup dialog with 3 buttons (Collect Token, Favourite Token, Get Directions)
     */
    private void showPopup() {
        myDialog.setContentView(R.layout.activity_pop);

        Button collectToken = myDialog.findViewById(R.id.collect_button);
        Button getDirections = myDialog.findViewById(R.id.directions_button);
        Button favouriteToken = myDialog.findViewById(R.id.favourite_button);
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

        favouriteToken.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                favouriteToken();
            }
        });
    }

    /**
     * Checks the user location and if it matches the token, token is collected
     */
    private void collectToken() {
        myDialog.cancel();

        if (checkLocation(deviceLong, deviceLat, tokenLong, tokenLat)) {  // Checks Location
            final MediaPlayer mp = MediaPlayer.create(this, R.raw.collectnoise); // Plays chime
            mp.start();
            Toast.makeText(this, "Token Collected", Toast.LENGTH_SHORT).show();
            collectedTokens.add(selectedFeature.getStringProperty("title")); // Adds token to collected tokens array
            
            if (pointOfInterest.equalsIgnoreCase("nature"))
                natureCount++;  // updates counters accordingly
            if (pointOfInterest.equalsIgnoreCase("coffee")) coffeeCount++;
            if (pointOfInterest.equalsIgnoreCase("beer")) {
                beerCount++;
            }

        } else {
            Toast.makeText(this, "Your Location does not match", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean checkLocation(double deviceLong, double deviceLat, double tokenLong, double tokenLat) {

        if (Math.abs(deviceLong - tokenLong) < 0.0003 && (Math.abs(deviceLat - tokenLat) < 0.0003)) {
            return true;
        }
        return false;
    }

    /**
     * Draws a route from user location to selected token
     */
    private void getDirections() {
        myDialog.cancel();

        // Sets origin and destination points
        origin = Point.fromLngLat(location.getLongitude(), location.getLatitude());
        destination = Point.fromLngLat(tokenLong, tokenLat);

        getRoute(origin, destination);

        directionsButton.setEnabled(true);
        directionsButton.setBackgroundResource(R.color.Blue);
    }

    /**
     * Adds selected favourite token to Favourites array
     */
    private void favouriteToken() {
        myDialog.cancel();
        favouriteTokens.add(selectedFeature.getStringProperty("title"));
    }

    /**
     * Add Tokens to map
     */
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

    /**
     * Loads JSON from Assets file
     *
     * @param fileName name of file
     * @return JSON as a String
     */
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

    /**
     * Draws route on map
     *
     * @param origin      starting point (user location)
     * @param destination end point (selected token location)
     */
    private void getRoute(Point origin, Point destination) {
        NavigationRoute.builder(this)
                .accessToken(Mapbox.getAccessToken())
                .origin(origin)
                .destination(destination)
                .build()
                .getRoute(new Callback<DirectionsResponse>() {
                    @Override
                    public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {

                        currentRoute = response.body().routes().get(0);

                        // Draw the route on the map
                        if (navigationMapRoute != null) {
                            navigationMapRoute.removeRoute();
                        } else {
                            navigationMapRoute = new NavigationMapRoute(null, mapView, mapboxMap, R.style.NavigationMapRoute);
                        }
                        navigationMapRoute.addRoute(currentRoute);
                    }

                    @Override
                    public void onFailure(Call<DirectionsResponse> call, Throwable throwable) {
                    }
                });
    }


    /**
     * LOCATION METHODS
     *
     * @enableLocationComponent - Checks if permissions are enable / requests permissions
     * - Sets location component on map / tracks user location
     * <p>
     * <p>
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
     * Standard Lifecycle Override Activity Methods
     */

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
        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {

            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.collected_tokens:
                        myDialog.setContentView(R.layout.activity_collected_tokens);
                        TextView textView = myDialog.findViewById(R.id.textView);
                        myDialog.show();
                        for (int i = 0; i < collectedTokens.size(); i++) {
                            textView.append(collectedTokens.get(i));
                            textView.append("\n");
                        }
                        textView.append("Coffee Tokens Collected: " + coffeeCount);
                        textView.append("\n");
                        textView.append("Beer Tokens Collected: " + beerCount);
                        textView.append("\n");
                        textView.append("Nature Tokens Collected: " + natureCount);

                        break;
                    case R.id.action_favorites:
                        myDialog.setContentView(R.layout.activity_collected_tokens);
                        TextView textVieww = myDialog.findViewById(R.id.textView);
                        myDialog.show();
                        textVieww.append("Favorites: ");
                        textVieww.append("\n");
                        for (int i = 0; i < favouriteTokens.size(); i++) {
                            textVieww.append(favouriteTokens.get(i));
                            textVieww.append("\n");
                        }
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
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();

        if (googleApiClient != null) {
            googleApiClient.connect();
        }
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
     * <p>
     * onConnected - Override
     * <p>
     * startLocationUpdates() - Then, we start to request for location updates in a startLocationUpdates dedicated method.
     * In this method, we build a LocationRequest object detailing the priority,
     * the refresh interval and the fastest interval for location updates.
     * <p>
     * <p>
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

 /**
 *
 * Updates the location variables every 5 seconds
 *
 **/
                
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

    /**
     * Google API onConnectionFailed Ovveride methods
     * <p>
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
