package id.flutter.background_service;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import id.flutter.background_service.constant.ArgumentKey;
import id.flutter.background_service.constant.ChannelName;
import id.flutter.background_service.constant.MethodName;
import id.flutter.background_service.delegate.BluetoothStateDelegate;
import id.flutter.background_service.delegate.CallDelegate;
import id.flutter.background_service.delegate.CharacteristicsDelegate;
import id.flutter.background_service.delegate.DescriptorsDelegate;
import id.flutter.background_service.delegate.DeviceConnectionDelegate;
import id.flutter.background_service.delegate.DevicesDelegate;
import id.flutter.background_service.delegate.LogLevelDelegate;
import id.flutter.background_service.delegate.DiscoveryDelegate;
import id.flutter.background_service.delegate.MtuDelegate;
import id.flutter.background_service.delegate.RssiDelegate;
import id.flutter.background_service.event.AdapterStateStreamHandler;
import id.flutter.background_service.event.CharacteristicsMonitorStreamHandler;
import id.flutter.background_service.event.ConnectionStateStreamHandler;
import id.flutter.background_service.event.RestoreStateStreamHandler;
import id.flutter.background_service.event.ScanningStreamHandler;
import com.polidea.multiplatformbleadapter.BleAdapter;
import com.polidea.multiplatformbleadapter.BleAdapterFactory;
import com.polidea.multiplatformbleadapter.OnErrorCallback;
import com.polidea.multiplatformbleadapter.OnEventCallback;
import com.polidea.multiplatformbleadapter.ScanResult;
import com.polidea.multiplatformbleadapter.errors.BleError;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.UnsatisfiedLinkError;

import io.flutter.FlutterInjector;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.loader.FlutterLoader;
import io.flutter.plugin.common.JSONMethodCodec;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.EventChannel;
import io.flutter.view.FlutterCallbackInformation;
// import io.flutter.view.FlutterMain;

public class BackgroundService extends Service implements MethodChannel.MethodCallHandler {
    private static final String TAG = "BackgroundService";
    private FlutterEngine backgroundEngine;
    private MethodChannel methodChannel;
    private DartExecutor.DartCallback dartCallback;

    String notificationTitle = "자이패스(Guest)";
    String notificationContent = "실행 준비중입니다.";

    private BleAdapter bleAdapter;
    private List<CallDelegate> delegates = new LinkedList<>();

    private AdapterStateStreamHandler adapterStateStreamHandler = new AdapterStateStreamHandler();
    private RestoreStateStreamHandler restoreStateStreamHandler = new RestoreStateStreamHandler();
    private ScanningStreamHandler scanningStreamHandler = new ScanningStreamHandler();
    private ConnectionStateStreamHandler connectionStateStreamHandler = new ConnectionStateStreamHandler();
    private CharacteristicsMonitorStreamHandler characteristicsMonitorStreamHandler = new CharacteristicsMonitorStreamHandler();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void setCallbackDispatcher(Context context, long callbackHandleId, boolean isForeground,
            boolean autoStartOnBoot) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit()
                .putLong("callback_handle", callbackHandleId)
                .putBoolean("is_foreground_mode", isForeground)
                .putBoolean("auto_start_on_boot", autoStartOnBoot)
                .apply();
    }

    public void setAutoStartOnBootMode(boolean value) {
        SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putBoolean("auto_start_on_boot", value).apply();
    }

    public static boolean isAutoStartOnBootMode(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getBoolean("auto_start_on_boot", false);
    }

    public void setForegroundServiceMode(boolean value) {
        SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        pref.edit().putBoolean("is_foreground_mode", value).apply();
    }

    public static boolean isForegroundService(Context context) {
        SharedPreferences pref = context.getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
        return pref.getBoolean("is_foreground_mode", false);
    }

    private void setupAdapter(Context context) {
        bleAdapter = BleAdapterFactory.getNewAdapter(context);
        delegates.add(new DeviceConnectionDelegate(bleAdapter, connectionStateStreamHandler));
        delegates.add(new LogLevelDelegate(bleAdapter));
        delegates.add(new DiscoveryDelegate(bleAdapter));
        delegates.add(new BluetoothStateDelegate(bleAdapter));
        delegates.add(new RssiDelegate(bleAdapter));
        delegates.add(new MtuDelegate(bleAdapter));
        delegates.add(new CharacteristicsDelegate(bleAdapter, characteristicsMonitorStreamHandler));
        delegates.add(new DevicesDelegate(bleAdapter));
        delegates.add(new DescriptorsDelegate(bleAdapter));
    }

    private final BroadcastReceiver mBroadCastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (methodChannel == null) {
                return;
            }
            Map<String, String> obj = new HashMap<>();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                switch (state) {
                    case BluetoothAdapter.STATE_ON:
                        try {
                            obj.put("state", "btON");
                            methodChannel.invokeMethod("onReceiveData", obj);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        try {
                            obj.put("state", "btOFF");
                            methodChannel.invokeMethod("onReceiveData", obj);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        Log.d(TAG, "STATE_TURNING_ON");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d(TAG, "STATE_TURNING_OFF");
                        break;
                }
            } else if (action.equals(LocationManager.PROVIDERS_CHANGED_ACTION)) {
                LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                boolean network_provider_state = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
                boolean gps_state = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                if (gps_state && network_provider_state) {
                    try {
                        obj.put("state", "gpsON");
                        methodChannel.invokeMethod("onReceiveData", obj);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        obj.put("state", "gpsOFF");
                        methodChannel.invokeMethod("onReceiveData", obj);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else if (action.equals(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm.isPowerSaveMode()) {
                    try {
                        obj.put("state", "powersaveON");
                        methodChannel.invokeMethod("onReceiveData", obj);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    try {
                        obj.put("state", "powersaveOFF");
                        methodChannel.invokeMethod("onReceiveData", obj);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        try {
            String packageName = getApplicationContext().getPackageName();
            PackageInfo pm = getPackageManager().getPackageInfo(packageName, 0);
            notificationTitle = pm.applicationInfo.loadLabel(getPackageManager()).toString();
        } catch (Exception e) {
            Log.e(TAG, "Exception : " + e);
        }
        notificationContent = "여기를 누르면 '자이패스(Guest)'가 실행됩니다.";
        updateNotificationInfo();

        IntentFilter intent = new IntentFilter();
        intent.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        intent.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
        intent.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        registerReceiver(mBroadCastReceiver, intent);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mBroadCastReceiver);
        stopForeground(true);
        isRunning.set(false);

        if (backgroundEngine != null) {
            backgroundEngine.getServiceControlSurface().detachFromService();
        }

        methodChannel = null;
        dartCallback = null;
        super.onDestroy();
    }

    private boolean checkPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            notificationTitle = "위치정보 사용권한이 없습니다.";
            notificationContent = "여기를 터치하고 위치정보 사용을 허가해 주세요.";
            return false;
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                    notificationTitle = "신체정보 사용권한이 없습니다.";
                    notificationContent = "여기를 터치하고 신체정보 사용을 허가해 주세요.";
                    return false;
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (ContextCompat.checkSelfPermission(this,
                                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                                || ContextCompat.checkSelfPermission(this,
                                        Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                                || ContextCompat.checkSelfPermission(this,
                                        Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                            notificationTitle = "근처 기기 사용권한이 부족합니다.";
                            notificationContent = "여기를 터치하고 근처 기기 사용 권한을 허용해주세요.";
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "CHANNEL_DEFAULT";
            String description = "실행 알림";

            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel(channelId, description, importance);
            channel.setShowBadge(false);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    protected void updateNotificationInfo() {
        if (isForegroundService(this)) {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean gps_state = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            boolean bt_state = BluetoothAdapter.getDefaultAdapter().isEnabled();
            boolean power_mode = pm.isPowerSaveMode();
            String packageName = getApplicationContext().getPackageName();
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            int resource = R.drawable.noti_not_running;
            if (power_mode) {
                intent = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
            } else if (gps_state && bt_state) {
                if (checkPermissions()) {
                    resource = R.drawable.noti_running;
                }
            } else if (gps_state && !bt_state) {
                intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            } else if (!gps_state && bt_state) {
                intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            } else {
                intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            }

            PendingIntent pi;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                pi = PendingIntent.getActivity(BackgroundService.this, 1, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            } else {
                pi = PendingIntent.getActivity(BackgroundService.this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            }
            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this, "CHANNEL_DEFAULT")
                    .setSmallIcon(resource)
                    .setAutoCancel(true)
                    .setOngoing(true)
                    .setContentTitle(notificationTitle)
                    .setContentText(notificationContent)
                    .setContentIntent(pi);

            startForeground(1, mBuilder.build());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences pref = this.getSharedPreferences("FlutterSharedPreferences", 0);
        String mode = pref.getString("flutter.smartpassmode", "");
        runService();
        return START_STICKY;
    }

    @Override
    public boolean stopService(Intent name) {
        Log.e(TAG, "stopService");
        return super.stopService(name);
    }

    AtomicBoolean isRunning = new AtomicBoolean(false);

    private void runService() {
        try {
            if (isRunning.get() || (backgroundEngine != null && !backgroundEngine.getDartExecutor().isExecutingDart()))
                return;
            updateNotificationInfo();

            SharedPreferences pref = getSharedPreferences("id.flutter.background_service", MODE_PRIVATE);
            long callbackHandle = pref.getLong("callback_handle", 0);

            FlutterCallbackInformation callback = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle);
            if (callback == null) {
                Log.e(TAG, "callback handle not found");
                return;
            }

            isRunning.set(true);
            backgroundEngine = new FlutterEngine(this);
            backgroundEngine.getServiceControlSurface().attachToService(BackgroundService.this, null,
                    isForegroundService(this));

            methodChannel = new MethodChannel(backgroundEngine.getDartExecutor().getBinaryMessenger(),
                    "id.flutter/background_service_bg");

            final EventChannel bluetoothStateChannel = new EventChannel(
                    backgroundEngine.getDartExecutor().getBinaryMessenger(), ChannelName.ADAPTER_STATE_CHANGES);
            final EventChannel restoreStateChannel = new EventChannel(
                    backgroundEngine.getDartExecutor().getBinaryMessenger(), ChannelName.STATE_RESTORE_EVENTS);
            final EventChannel scanningChannel = new EventChannel(
                    backgroundEngine.getDartExecutor().getBinaryMessenger(), ChannelName.SCANNING_EVENTS);
            final EventChannel connectionStateChannel = new EventChannel(
                    backgroundEngine.getDartExecutor().getBinaryMessenger(),
                    ChannelName.CONNECTION_STATE_CHANGE_EVENTS);
            final EventChannel characteristicMonitorChannel = new EventChannel(
                    backgroundEngine.getDartExecutor().getBinaryMessenger(), ChannelName.MONITOR_CHARACTERISTIC);

            methodChannel.setMethodCallHandler(this);

            scanningChannel.setStreamHandler(this.scanningStreamHandler);
            bluetoothStateChannel.setStreamHandler(this.adapterStateStreamHandler);
            restoreStateChannel.setStreamHandler(this.restoreStateStreamHandler);
            connectionStateChannel.setStreamHandler(this.connectionStateStreamHandler);
            characteristicMonitorChannel.setStreamHandler(this.characteristicsMonitorStreamHandler);

            dartCallback = new DartExecutor.DartCallback(getAssets(),
                    FlutterInjector.instance().flutterLoader().findAppBundlePath(), callback);
            backgroundEngine.getDartExecutor().executeDartCallback(dartCallback);
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG,
                    "UnsatisfiedLinkError: After a reboot this may happen for a short period and it is ok to ignore then!"
                            + e.getMessage());
        }
    }

    public void receiveData(MethodCall call) {
        if (methodChannel != null) {
            try {
                methodChannel.invokeMethod("onReceiveData", call.arguments);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void isClientCreated(MethodChannel.Result result) {
        result.success(bleAdapter != null);
    }

    private void createClient(MethodCall call, MethodChannel.Result result) {
        try {
            if (bleAdapter != null) {
                Log.w(TAG,
                        "Overwriting existing native client. Use BleManager#isClientCreated to check whether a client already exists.");
            }
            setupAdapter(getApplicationContext());
            bleAdapter.createClient(call.<String>argument(ArgumentKey.RESTORE_STATE_IDENTIFIER),
                    new OnEventCallback<String>() {
                        @Override
                        public void onEvent(String adapterState) {
                            adapterStateStreamHandler.onNewAdapterState(adapterState);
                        }
                    }, new OnEventCallback<Integer>() {
                        @Override
                        public void onEvent(Integer restoreStateIdentifier) {
                            restoreStateStreamHandler.onRestoreEvent(restoreStateIdentifier);
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }

        result.success(null);
    }

    private void destroyClient(MethodChannel.Result result) {
        if (bleAdapter != null) {
            bleAdapter.destroyClient();
        }
        scanningStreamHandler.onComplete();
        connectionStateStreamHandler.onComplete();
        bleAdapter = null;
        delegates.clear();
        result.success(null);
    }

    private void startDeviceScan(@NonNull MethodCall call, MethodChannel.Result result) {
        List<String> uuids = call.<List<String>>argument(ArgumentKey.UUIDS);
        bleAdapter.startDeviceScan(uuids.toArray(new String[uuids.size()]),
                call.<Integer>argument(ArgumentKey.SCAN_MODE),
                call.<Integer>argument(ArgumentKey.CALLBACK_TYPE),
                new OnEventCallback<ScanResult>() {
                    @Override
                    public void onEvent(ScanResult data) {
                        scanningStreamHandler.onScanResult(data);
                    }
                }, new OnErrorCallback() {
                    @Override
                    public void onError(BleError error) {
                        scanningStreamHandler.onError(error);
                    }
                });
        result.success(null);
    }

    private void stopDeviceScan(MethodChannel.Result result) {
        if (bleAdapter != null) {
            bleAdapter.stopDeviceScan();
        }
        scanningStreamHandler.onComplete();
        result.success(null);
    }

    private void cancelTransaction(MethodCall call, MethodChannel.Result result) {
        try {
            if (bleAdapter != null) {
                bleAdapter.cancelTransaction(call.<String>argument(ArgumentKey.TRANSACTION_ID));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        result.success(null);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        Log.d(TAG, "background onMethodCall : " + call.method);
        String method = call.method;
        for (CallDelegate delegate : delegates) {
            if (delegate.canHandle(call)) {
                delegate.onMethodCall(call, result);
                return;
            }
        }

        try {
            if (method.equalsIgnoreCase(MethodName.CREATE_CLIENT)) {
                createClient(call, result);
                return;
            }
            if (method.equalsIgnoreCase(MethodName.DESTROY_CLIENT)) {
                destroyClient(result);
                return;
            }
            if (method.equalsIgnoreCase(MethodName.START_DEVICE_SCAN)) {
                startDeviceScan(call, result);
                return;
            }
            if (method.equalsIgnoreCase(MethodName.STOP_DEVICE_SCAN)) {
                stopDeviceScan(result);
                return;
            }
            if (method.equalsIgnoreCase(MethodName.CANCEL_TRANSACTION)) {
                cancelTransaction(call, result);
                return;
            }
            if (method.equalsIgnoreCase(MethodName.IS_CLIENT_CREATED)) {
                isClientCreated(result);
                return;
            }
            if (method.equalsIgnoreCase("setNotificationInfo")) {
                notificationTitle = call.<String>argument("title");
                notificationContent = call.<String>argument("content");
                updateNotificationInfo();
                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("setAutoStartOnBootMode")) {
                boolean value = call.<Boolean>argument("value");
                setAutoStartOnBootMode(value);
                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("setForegroundMode")) {
                boolean value = call.<Boolean>argument("value");
                setForegroundServiceMode(value);
                if (value) {
                    updateNotificationInfo();
                } else {
                    stopForeground(true);
                }

                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("stopService")) {
                Intent intent = new Intent(getApplicationContext(), BackgroundService.class);
                stopService(intent);
                result.success(true);
                return;
            }

            if (method.equalsIgnoreCase("sendData")) {
                LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
                Intent intent = new Intent("id.flutter/background_service");
                intent.putExtra("data", call.arguments.toString());
                manager.sendBroadcast(intent);
                result.success(true);
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "exception occured " + e.getMessage());
            e.printStackTrace();
        }

        result.notImplemented();
    }
}
