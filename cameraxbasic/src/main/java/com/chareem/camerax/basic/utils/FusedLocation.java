package com.chareem.camerax.basic.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.util.ArrayList;
import java.util.List;

public class FusedLocation implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private GoogleApiClient apiClient = null;
    private Long UPDATE_INTERVAL = (long)(2 * 1000); /* 10 secs */
    private Long FASTEST_INTERVAL = (long) 20000;
    private Handler handler;
    private Runnable runnable;
    private Boolean isGetLoc = false;
    private Context context;
    private Boolean fast;
    private Integer timeout = 60000;
    private Listener mListener;
    private LocationRequest mLocationRequest = null;
    private Location mLocation = null;
    private SharedPreferences pref_setting = null;

    public FusedLocation(Context mContext, Boolean mFast){
        context = mContext;
        fast = mFast;
    }

    public interface Listener {
        /** Called whenever the device's position changes so that you can call [SimpleLocation.getPosition]  */
        void onGetLocation(Location location);
        void onTimeout();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onConnected(Bundle p0) {
        if (apiClient != null){
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    apiClient,
                    mLocationRequest, this
            );
            mLocation = LocationServices.FusedLocationApi.getLastLocation(apiClient);

            mListener.onGetLocation(mLocation);
            if (apiClient.isConnected()) {
                LocationServices.FusedLocationApi.requestLocationUpdates(
                        apiClient,
                        mLocationRequest, this
                );
            }
            startLocationUpdates();
        }
    };

    @Override
    public void onConnectionSuspended(int p0) {
        if (apiClient != null)
            apiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult p0) {
        if (apiClient != null)
            apiClient.connect();
    }

    @Override
    public void onLocationChanged(Location location) {
        mLocation = location;

        if (mLocation != null) {
            mListener.onGetLocation(mLocation);
            isGetLoc = true;
            handler.removeCallbacks(runnable);
        }

        if (apiClient!= null)
        if (apiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(apiClient, this);
        }
    }

    private void startLocationUpdates(){
        if (mLocationRequest == null){
            mLocationRequest = new  LocationRequest();
            mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            mLocationRequest.setInterval(UPDATE_INTERVAL);
            mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        }

        if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission();
            Toast.makeText(context, "You need to enable permissions to display location !", Toast.LENGTH_SHORT).show();
            return;
        }

        if (fast) {
            FusedLocationProviderClient fusedClient = LocationServices.getFusedLocationProviderClient(context);
            fusedClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location == null) {
                        handler.removeCallbacks(runnable);
                        handler.postDelayed(runnable, timeout);
                    } else {
                        mLocation = location;
                        mListener.onGetLocation(location);
                        isGetLoc = true;
                    }
                }
            });
        } else {
            handler.removeCallbacks(runnable);
            handler.postDelayed(runnable, timeout);
        }
    }

    private void requestPermission(){
        ArrayList<String> permissions = new ArrayList<>();

        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        Dexter.withActivity((Activity) context)
                .withPermissions(permissions)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        if (!report.areAllPermissionsGranted()) {
                            ((Activity) context).finish();
                        } else {
                            if (apiClient == null) init();
                            else if (!apiClient.isConnected()) init();
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {

                    }
                }).check();
    }

    public void setListener(Listener listener){
        this.mListener = listener;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    private void setFast(Boolean _fast) {
        this.fast = _fast;
    }

    public void init(){
        SimpleLocation gps = new  SimpleLocation(context, false, false, 1000, false);
        if (!gps.hasLocationEnabled()){
            SimpleLocation.openDialogSetting((Activity) context);
            //SimpleLocation.openSettings(this)

            Toast.makeText(context, "Mohon aktifkan lokasi gps anda", Toast.LENGTH_SHORT).show();
        }
        gps.beginUpdates();
        pref_setting = context.getSharedPreferences("setting.conf", Context.MODE_PRIVATE);
        apiClient = new  GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this).build();
        mLocationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(UPDATE_INTERVAL)
            .setFastestInterval(FASTEST_INTERVAL);
        if (apiClient != null) {
            apiClient.connect();
        }
        runnable = new Runnable() {
            @Override
            public void run() {
                apiClient.connect();
            }
        };

        handler = new Handler();
    }

    public void getSingleUpdate() {
        if (apiClient != null) {
            isGetLoc = false;
            apiClient.connect();
        }
    }
}