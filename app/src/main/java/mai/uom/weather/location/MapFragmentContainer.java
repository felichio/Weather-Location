package mai.uom.weather.location;


import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;


/**
 * MapFragmentContainerClass
 * Represents the google map with all it's functionality
 */
public class MapFragmentContainer extends Fragment implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{
    private final int DEFAULT_ZOOM = 15;
    /** Google map object **/
    private GoogleMap map;
    /** Fragment view **/
    private View mapView;
    /** Map container callbacks **/
    private MapContainerCallbacks callbacks;
    /** Google map api for getting google services **/
    private GoogleApiClient mGoogleApiClient;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /**
         * Connect to Google api client
         */
        GoogleApiClient.Builder builder = new GoogleApiClient.Builder(getActivity().getApplicationContext());
        builder.addApi(LocationServices.API);
        builder.addConnectionCallbacks(this);
        builder.addOnConnectionFailedListener(this);
        mGoogleApiClient = builder.build();
        mGoogleApiClient.connect();
        /** Declare that our fragment has it's own menu **/
        setHasOptionsMenu(true);
    }

    /**
     * Resister the SupportMapFragment to pass
     * asynchronous callbacks to our MapContainerFragment
     */
    private void mapAsync() {
        SupportMapFragment mapFragment = (SupportMapFragment)getChildFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        /** Create the fragment view **/
        mapView = inflater.inflate(R.layout.fragment_map_container, container, false);
        /** Resister the map fragment callbacks **/
        mapAsync();
        return mapView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        /** Creating the menu from the xml file **/
        inflater.inflate(R.menu.menu_map_container_fragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        /** if action_pin called push current location **/
        if(item.getItemId() == R.id.action_pin) {
            if(map.isMyLocationEnabled() && map.getMyLocation()!=null) {
                /** Push the current location to the callbacks **/
                pushLocationToCallbacks(new LatLng(map.getMyLocation().getLatitude(),
                        map.getMyLocation().getLongitude()));
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        /** Hold the googleMap to a private var */
        map = googleMap;
        /** Enable zoom buttons **/
        googleMap.getUiSettings().setZoomControlsEnabled(true);

        /** Enable Marker options **/
        googleMap.getUiSettings().setMapToolbarEnabled(true);
        /** Enables my location **/
        enableMyLocation();
        /** Handles the event of long press on map **/
        googleMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                /** Push the location to the callbacks **/
                pushLocationToCallbacks(latLng);

            }
        });

    }

    /**
     * Push the location to callback listener
     * @param latLng Location
     */
    private void pushLocationToCallbacks(LatLng latLng) {
        if (callbacks != null)
            callbacks.onMarkerAdded(latLng.latitude, latLng.longitude);
    }
    /**
     * Add a marker to the map
     * @param lat Latitude
     * @param lng Longitude
     * @param title Title
     * @param snippet Message
     */
    public void addMarker(double lat,double lng,String title,String snippet) {
        /** Add a maker to the location **/
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.title(title);
        markerOptions.snippet(snippet);
        markerOptions.position(new LatLng(lat,lng));
        markerOptions.draggable(false);
        map.addMarker(markerOptions);
        /** Vibrate to give feedback of the action to the user **/
        vibrate();
    }
    /**
     * Enable my location (Check for permission first)
     */
    private void enableMyLocation() {
        if(PermissionManager.requestPermission(getActivity(), this, Manifest.permission.ACCESS_FINE_LOCATION,
                PermissionManager.PERMISSION_CODE_REQUEST_LOCATION)){
            map.setMyLocationEnabled(true);
        }
    }

    /**
     * Vibrates the device for an instance
     */
    public void vibrate() {
        Vibrator v = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(25);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PermissionManager.PERMISSION_CODE_REQUEST_LOCATION:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Permission granted
                    map.getUiSettings().setMyLocationButtonEnabled(true);
                    map.setMyLocationEnabled(true);
                    //Move camera to my last know location
                    moveCameraToMyLastKnownLocation();
                }
                else //Toast a message to the user that permission denied(By him)
                    Toast.makeText(getActivity(),R.string.permission_denied,Toast.LENGTH_LONG).show();
                break;
            default:
                break;
        }
    }

    /**
     * Set callbacks listener
     * @param callbacks The listener
     */
    public void setMapContainerCallbacks(MapContainerCallbacks callbacks) {
        this.callbacks = callbacks;
    }

    @Override
    public void onConnected(Bundle bundle) {
        /** On connected move to the last know location **/
        moveCameraToMyLastKnownLocation();
    }

    /**
     * Moves the camera to my last known location
     */
    private void moveCameraToMyLastKnownLocation() {
        try {
            /** Check if the permission is granted first **/
            if(PermissionManager.checkPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION)) {
                /** Request the last know location from the google api **/
                Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                        mGoogleApiClient);
                /** Move camera to the last known location **/
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()), DEFAULT_ZOOM));
            }
        }
        catch (NullPointerException e) {
            e.printStackTrace();
            Log.e("MoveToMyLastKnowLoc","Maybe the last known location is null!");
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.e("ConnectionSuspended", "MapContainerFragment onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e("ConnectionFailed","MapContainerFragment onConnectionFailed:"+connectionResult.getErrorMessage());
    }

    /**
     * Map Container callbacks
     */
    public interface MapContainerCallbacks {
        void onMarkerAdded(double lat,double lng);
    }
}
