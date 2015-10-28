package com.example.dlambros.mapbuddy;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.ResultReceiver;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;
import com.google.android.gms.maps.model.TileProvider;
import com.google.maps.android.heatmaps.HeatmapTileProvider;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
                                                              ConnectionCallbacks,
                                                              OnConnectionFailedListener,
                                                              LocationListener
{
    // Strings for logging
    private final String TAG = this.getClass().getSimpleName();
    private final String RESTORE = ", can restore state";

    // Text Views
    private TextView mLatitudeText;
    private TextView mLongitudeText;
    private TextView mAddress;
    private TextView mLastUpdateTimeText;

    // Buttons
    private Button mStore;
    private Button mViewLoc;
    private Button mDelete;

    private Location BuschCampusCenter = new Location("BuschCampusCenter");
    private Location HighPointSolutionsStadium = new Location("HighPointSolutionsStadium");
    private Location ElectricalEngineeringBuilding = new Location("ElectricalEngineeringBuilding");
    private Location RutgersStudentCenter = new Location("RutgersStudentCenter");
    private Location OldQueens = new Location("OldQueens");

    // String for Address output
    protected String mAddressOutput;

    // Request code to use when launching the resolution activity
    private static final int REQUEST_RESOLVE_ERROR = 1001;

    // Unique tag for the error dialog fragment
    private static final String DIALOG_ERROR = "dialog_error";

    // Bool to track whether the app is already resolving an error
    private boolean mResolvingError = false;

    private static final String STATE_RESOLVING_ERROR = "resolving_error";

    private GoogleApiClient mGoogleApiClient;

    // SQL Database for storage
    Database myDB;

    private Location mCurrentLocation;
    private String mLastUpdateTime;

    protected LocationRequest mLocationRequest;

    private String ADDRESS_KEY = "address_key";
    private String REQUESTING_LOCATION_UPDATES_KEY = "REQUESTLOC";
    private String LOCATION_KEY = "LOCKEY";
    private String LAST_UPDATED_TIME_STRING_KEY = "LASTUPTIMESTRING";
    private boolean mRequestingLocationUpdates = true;

    private GoogleMap mMap;
    private TileProvider mProvider;
    private TileOverlay mOverlay;

    /**
     * buildGoogleApiClient() defines an instance of Google API client using GoogleApiClient.Builder
     */
    protected synchronized void buildGoogleApiClient()
    {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        createLocationRequest();
    }
    
    protected void startLocationUpdates()
    {
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    protected void createLocationRequest()
    {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(5000);
        mLocationRequest.setFastestInterval(5000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    @Override
    public void onLocationChanged(Location location)
    {
        mCurrentLocation = location;
        mAddressOutput = getAddress();
        mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        updateUI();
    }

    private void updateUI()
    {
        mLatitudeText.setText(String.valueOf(mCurrentLocation.getLatitude()));
        mLongitudeText.setText(String.valueOf(mCurrentLocation.getLongitude()));
        mAddress.setText(mAddressOutput);
        mLastUpdateTimeText.setText(mLastUpdateTime);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == REQUEST_RESOLVE_ERROR)
        {
            mResolvingError = false;
            if (resultCode == RESULT_OK)
            {
                // Make sure the app is not already connected or attempting to connect
                if (!mGoogleApiClient.isConnecting() &&
                        !mGoogleApiClient.isConnected())
                {
                    mGoogleApiClient.connect();
                }
            }
        }
    }

    @Override
    public void onConnected(Bundle connectionHint)
    {
        mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        if(mCurrentLocation != null)
        {
            mLatitudeText.setText(String.valueOf(mCurrentLocation.getLatitude()));
            mLongitudeText.setText(String.valueOf(mCurrentLocation.getLongitude()));
            myDB = new Database(this);
            mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
            mLastUpdateTimeText.setText(mLastUpdateTime);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    mAddressOutput = getAddress();

                    MapsActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mAddress.setText(mAddressOutput);
                        }
                    });
                }
            });

            setUpMap();
        }
        if(mRequestingLocationUpdates)
        {
            startLocationUpdates();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        myDB = new Database(this);

        mLastUpdateTime = "";
        mAddressOutput = "";

        buildGoogleApiClient();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        mResolvingError = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_RESOLVING_ERROR, false);
        mLatitudeText = (TextView) findViewById(R.id.latText);
        mLongitudeText = (TextView) findViewById(R.id.longText);
        mLastUpdateTimeText = (TextView) findViewById(R.id.timeText);
        mAddress = (TextView) findViewById(R.id.address);

        mStore = (Button) findViewById(R.id.store);
        mViewLoc = (Button) findViewById(R.id.viewLoc);
        mDelete = (Button) findViewById(R.id.delete);
        addData();
        viewAll();
        deleteDB();

        mResolvingError = savedInstanceState != null && savedInstanceState.getBoolean(STATE_RESOLVING_ERROR, false);
        updateValuesFromBundle(savedInstanceState);
        setUpMapIfNeeded();
    }

    private void setUpMapIfNeeded()
    {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null)
        {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null)
            {
                //setUpMap();
            }
        }
    }

    private void setUpMap()
    {
        mMap.setMyLocationEnabled(true);
        LatLng my= new LatLng(mCurrentLocation.getLatitude(),mCurrentLocation.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(my, 15));

        LatLng BCC = new LatLng(40.523128,-74.458797);
        BuschCampusCenter.setLatitude(40.523128);
        BuschCampusCenter.setLongitude(-74.458797);
        mMap.addMarker(new MarkerOptions().position(BCC).title("Busch Campus Center")).setSnippet(String.valueOf(BuschCampusCenter.distanceTo(mCurrentLocation)) + " meters away");

        LatLng HPSS = new LatLng(40.513817,-74.464844);
        HighPointSolutionsStadium.setLatitude(40.513817);
        HighPointSolutionsStadium.setLongitude(-74.464844);
        mMap.addMarker(new MarkerOptions().position(HPSS).title("HighPoint Solutions Stadium")).setSnippet(String.valueOf(HighPointSolutionsStadium.distanceTo(mCurrentLocation)) + " meters away");

        LatLng EEB = new LatLng(40.521663,-74.460665);
        ElectricalEngineeringBuilding.setLatitude(40.521663);
        ElectricalEngineeringBuilding.setLongitude(-74.460665);
        mMap.addMarker(new MarkerOptions().position(EEB).title("Electrical Engineering Building")).setSnippet(String.valueOf(ElectricalEngineeringBuilding.distanceTo(mCurrentLocation)) + " meters away");

        LatLng RSC = new LatLng(40.502661,-74.451771);
        RutgersStudentCenter.setLatitude(40.502661);
        RutgersStudentCenter.setLongitude(-74.451771);
        mMap.addMarker(new MarkerOptions().position(RSC).title("Rutgers Students Center")).setSnippet(String.valueOf(RutgersStudentCenter.distanceTo(mCurrentLocation)) + " meters away");

        LatLng OQ = new LatLng(40.498720,-74.446229);
        OldQueens.setLatitude(40.498720);
        OldQueens.setLongitude(-74.446229);
        mMap.addMarker(new MarkerOptions().position(OQ).title("Old Queens")).setSnippet(String.valueOf(OldQueens.distanceTo(mCurrentLocation)) + " meters away");

        addHeatMap();
    }

    private void updateValuesFromBundle(Bundle savedInstanceState)
    {
        if (savedInstanceState != null)
        {
            // Update the value of mRequestingLocationUpdates from the Bundle, and
            // make sure that the Start Updates and Stop Updates buttons are
            // correctly enabled or disabled.
            if (savedInstanceState.keySet().contains(REQUESTING_LOCATION_UPDATES_KEY))
            {
                mRequestingLocationUpdates = savedInstanceState.getBoolean(REQUESTING_LOCATION_UPDATES_KEY);
            }

            // Update the value of mCurrentLocation from the Bundle and update the
            // UI to show the correct latitude and longitude.
            if (savedInstanceState.keySet().contains(LOCATION_KEY))
            {
                // Since LOCATION_KEY was found in the Bundle, we can be sure that
                // mCurrentLocationis not null.
                mCurrentLocation = savedInstanceState.getParcelable(LOCATION_KEY);
            }

            // Update the value of mLastUpdateTime from the Bundle and update the UI.
            if (savedInstanceState.keySet().contains(LAST_UPDATED_TIME_STRING_KEY))
            {
                mLastUpdateTime = savedInstanceState.getString(
                        LAST_UPDATED_TIME_STRING_KEY);
            }

            if(savedInstanceState.keySet().contains(ADDRESS_KEY))
            {
                mAddressOutput = savedInstanceState.getString(ADDRESS_KEY);
            }
            updateUI();
        }
    }
    
    public String getAddress()
    {
        String street = "";
        String errorMessage = "";
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

        List<Address> addresses = null;

        try{
            addresses = geocoder.getFromLocation(mCurrentLocation.getLatitude(),
                                                 mCurrentLocation.getLongitude(), 1);
        } catch (IOException ioException)
        {
            // Catch network or other I/O problems
            errorMessage = getString(R.string.service_not_available);
            Log.e(TAG, errorMessage, ioException);
        } catch (IllegalArgumentException illegalArgumentException) {
            // Catch invalid latitude or longitude values
            errorMessage = getString(R.string.invalid_lat_long_used);
            Log.e(TAG, errorMessage + ". " +
                  "Latitude = " + mCurrentLocation.getLatitude() +
                  ", Longitude = " +
                  mCurrentLocation.getLongitude(), illegalArgumentException);
        }

        // Handle case where no address was found
        if (addresses == null || addresses.size() == 0) {
            if (errorMessage.isEmpty()) {
                errorMessage = getString(R.string.no_address_found);
                Log.e(TAG, errorMessage);
                return "";
            }
        }
        else
        {
            Address address = addresses.get(0);
            ArrayList<String> addressFragments = new ArrayList<String>();
            for (int i = 0; i < address.getMaxAddressLineIndex(); i++)
            {
                addressFragments.add(address.getAddressLine(i));
            }
            Log.i(TAG, getString(R.string.address_found));
            street = TextUtils.join(System.getProperty("line.separator"), addressFragments);
        }
        return street;
    }
    public void addData()
    {
        mStore.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean isInserted = myDB.insertData(mLongitudeText.getText().toString(),
                                mLatitudeText.getText().toString(),
                                mAddress.getText().toString());
                        if (isInserted == true)
                            Toast.makeText(MapsActivity.this, "Data Inserted", Toast.LENGTH_LONG).show();
                        else
                            Toast.makeText(MapsActivity.this, "Data wasn't Inserted", Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    public void deleteDB()
    {
        mDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                myDB.deleteAllData();
            }
        });
    }

    public void viewAll()
    {
        mViewLoc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Cursor res = myDB.getAllData();
                if (res.getCount() == 0)
                {
                    // show message
                    showMessage("Error", "Nothing found");
                    return;
                }

                StringBuffer buffer = new StringBuffer();
                while (res.moveToNext())
                {
                    buffer.append("Id : " + res.getString(0) + "\n" +
                            "Longitude : " + res.getString(1) + "\n" +
                            "Latitude : " + res.getString(2) + "\n" +
                            "Address : " + res.getString(3) + "\n\n");
                }

                // show all data
                showMessage("Data", buffer.toString());
            }
        });
    }

    public void showMessage(String title, String Message)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setTitle(title);
        builder.setMessage(Message);
        builder.show();
    }

    @Override
    public void onConnectionSuspended(int cause)
    {
        //mGoogleApiClient.connect();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_maps, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings)
        {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onRestart()
    {
        super.onRestart();

        // Notification that the activity will be started
        Log.i(TAG, "onStart");
    }

    @Override
    protected void onStart()
    {
        super.onStart();

       // if (!mResolvingError)
       // {
            mGoogleApiClient.connect();
        //}

        // Notification that the activity is starting
        Log.i(TAG, "onStart");
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        //mGoogleApiClient.disconnect();
        stopLocationUpdates();
        // Notification that the activity will stop interacting with the user
        Log.i(TAG, "onPause" + (isFinishing() ? " Finishing" : ""));
    }

    protected void stopLocationUpdates()
    {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    @Override
    protected void onStop()
    {
        super.onStop();
        mGoogleApiClient.disconnect();
        // Notification that the activity is no longer visible
        // LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, (com.google.android.gms.location.LocationListener) this);
        Log.i(TAG, "onStop");
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        // Notification that the activity will be destroyed
        Log.i(TAG,
                "onDestroy " // Log which, if any, configuration changed
                        + Integer.toString(getChangingConfigurations(), 16));
    }

    ///////////////////////////////////////////////////////////////////////////////
    // Called during the lifecycle, when instance state should be saved/restored //
    ///////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(REQUESTING_LOCATION_UPDATES_KEY, mRequestingLocationUpdates);
        outState.putParcelable(LOCATION_KEY, mCurrentLocation);
        outState.putString(LAST_UPDATED_TIME_STRING_KEY, mLastUpdateTime);
        outState.putString(ADDRESS_KEY, mAddressOutput);
        super.onSaveInstanceState(outState);
       // outState.putBoolean(STATE_RESOLVING_ERROR, mResolvingError);
        Log.i(TAG, "onSaveInstanceState");
    }

 /**   @Override
    protected void onRestoreInstanceState(Bundle savedState)
    {
        super.onRestoreInstanceState(savedState);

        // Restore state
        String answer = null != savedState ? savedState.getString("answer") : "";

        Object oldTaskObject = getLastNonConfigurationInstance();

        if (null != oldTaskObject)
        {
            int oldtask = ((Integer) oldTaskObject).intValue();

            int currentTask = getTaskId();

            // Task should not change across a configuration change
            assert oldtask == currentTask;
        }
    }
*/
    /* Creates a dialog for an error message */
    private void showErrorDialog(int errorCode)
    {
        // Create a fragment for the error dialog
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt(DIALOG_ERROR, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(getSupportFragmentManager(), "errordialog");
    }

    /* Called from ErrorDialogFragment when the dialog is dismissed. */
    public void onDialogDismissed()
    {
        mResolvingError = false;
    }

    /* A fragment to display an error dialog */
    public static class ErrorDialogFragment extends DialogFragment
    {
        public ErrorDialogFragment()
        {

        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState)
        {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = this.getArguments().getInt(DIALOG_ERROR);
            return GoogleApiAvailability.getInstance().getErrorDialog(
                    this.getActivity(), errorCode, REQUEST_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog)
        {
            ((MapsActivity) getActivity()).onDialogDismissed();
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if(mGoogleApiClient.isConnected() && !mRequestingLocationUpdates)
        {
            startLocationUpdates();
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result)
    {
        if (mResolvingError)
        {
            // Already attempting to resolve an error.
            return;
        }
        else if (result.hasResolution())
        {
            try
            {
                mResolvingError = true;
                result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            }
            catch (IntentSender.SendIntentException e)
            {
                // There was an error with the resolution intent. Try again.
                mGoogleApiClient.connect();
            }
        }
        else
        {
            // Show dialog using GoogleApiAvailability.getErrorDialog()
            showErrorDialog(result.getErrorCode());
            mResolvingError = true;
        }

    }
    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.setMyLocationEnabled(true);
    }

    private void addHeatMap()
    {
        List<LatLng> list = new ArrayList<LatLng>();


        Cursor res = myDB.getAllData();
        if (res.getCount() == 0)
        {
            return;
        }
        while (res.moveToNext())
        {
            list.add(new LatLng(res.getDouble(2),res.getDouble(1)));
        }
        
        mProvider = new HeatmapTileProvider.Builder()
                .data(list)
                .build();
        mOverlay = mMap.addTileOverlay(new TileOverlayOptions().tileProvider(mProvider));
    }
}
