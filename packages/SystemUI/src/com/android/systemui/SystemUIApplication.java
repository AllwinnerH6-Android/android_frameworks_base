/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.systemui;

import android.app.ActivityThread;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.net.wifi.WifiManager;
import android.os.Process;
import android.os.SystemProperties;
import android.os.Trace;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Log;
import android.util.TimingsTraceLog;

import com.android.systemui.plugins.OverlayPlugin;
import com.android.systemui.plugins.PluginListener;
import com.android.systemui.plugins.PluginManager;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.statusbar.phone.StatusBarWindowManager;
import com.android.systemui.util.NotificationChannels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// AW:Added for BOOTEVENT
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Application class for SystemUI.
 */
public class SystemUIApplication extends Application implements SysUiServiceProvider {

    public static final String TAG = "SystemUIService";
    private static final boolean DEBUG = false;

    /**
     * Hold a reference on the stuff we start.
     */
    private SystemUI[] mServices;
    private String[] mNames;
    private boolean mServicesStarted;
    private boolean mBootCompleted;
    private final Map<Class<?>, Object> mComponents = new HashMap<>();
    private final List<String> mBaseStartServices = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        // Set the application theme that is inherited by all services. Note that setting the
        // application theme in the manifest does only work for activities. Keep this in sync with
        // the theme set there.
        setTheme(R.style.Theme_SystemUI);

        SystemUIFactory.createFromConfig(this);
        mBaseStartServices.add("com.android.systemui.Dependency");
        mBaseStartServices.add("com.android.systemui.statusbar.CommandQueue$CommandQueueStart");
        mBaseStartServices.add("com.android.systemui.keyguard.KeyguardViewMediator");
        mBaseStartServices.add("com.android.systemui.SystemBars");
        //mBaseStartServices.add("com.android.systemui.util.NotificationChannels");
        //mBaseStartServices.add("com.android.systemui.recents.Recents");
        //mBaseStartServices.add("com.android.systemui.volume.VolumeUI");
        //mBaseStartServices.add("com.android.systemui.stackdivider.Divider");
        //mBaseStartServices.add("com.android.systemui.usb.StorageNotification");
        //mBaseStartServices.add("com.android.systemui.power.PowerUI");
        //mBaseStartServices.add("com.android.systemui.media.RingtonePlayer");
        //mBaseStartServices.add("com.android.systemui.keyboard.KeyboardUI");
        //mBaseStartServices.add("com.android.systemui.pip.PipUI");
        //mBaseStartServices.add("com.android.systemui.shortcut.ShortcutKeyDispatcher");
        //mBaseStartServices.add("com.android.systemui.VendorServices");
        //mBaseStartServices.add("com.android.systemui.util.leak.GarbageMonitor$Service");
        //mBaseStartServices.add("com.android.systemui.LatencyTester");
        //mBaseStartServices.add("com.android.systemui.globalactions.GlobalActionsComponent");
        //mBaseStartServices.add("com.android.systemui.ScreenDecorations");
        //mBaseStartServices.add("com.android.systemui.fingerprint.FingerprintDialogImpl");
        //mBaseStartServices.add("com.android.systemui.SliceBroadcastRelayHandler");


        if (Process.myUserHandle().equals(UserHandle.SYSTEM)) {
            IntentFilter bootCompletedFilter = new IntentFilter(Intent.ACTION_BOOT_COMPLETED);
            bootCompletedFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
            registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (mBootCompleted) return;

                    if (DEBUG) Log.v(TAG, "BOOT_COMPLETED received");
                    unregisterReceiver(this);
                    mBootCompleted = true;
                    if (mServicesStarted) {
                        final int N = mServices.length;
                        for (int i = 0; i < N; i++) {
                            long ti = System.currentTimeMillis();
                            if (mServices[i] == null) {
                                String clsName = mNames[i];
                                if (DEBUG) Log.d(TAG, "loading: " + clsName);
                                Class cls;
                                try {
                                    cls = Class.forName(clsName);
                                    mServices[i] = (SystemUI) cls.newInstance();
                                } catch(ClassNotFoundException ex){
                                    throw new RuntimeException(ex);
                                } catch (IllegalAccessException ex) {
                                    throw new RuntimeException(ex);
                                } catch (InstantiationException ex) {
                                    throw new RuntimeException(ex);
                                }

                                mServices[i].mContext = SystemUIApplication.this;
                                mServices[i].mComponents = mComponents;
                                if (DEBUG) Log.d(TAG, "running: " + mServices[i]);
                                mServices[i].start();
                            }
                            mServices[i].onBootCompleted();
                            ti = System.currentTimeMillis() - ti;
                            Log.d("SystemUIBootTiming", "SystemUIService: bootcomplete " + mServices[i].getClass().getName() + " took " + ti + " ms");
                        }
                    }


                }
            }, bootCompletedFilter);

            IntentFilter localeChangedFilter = new IntentFilter(Intent.ACTION_LOCALE_CHANGED);
            registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (Intent.ACTION_LOCALE_CHANGED.equals(intent.getAction())) {
                        if (!mBootCompleted) return;
                        // Update names of SystemUi notification channels
                        NotificationChannels.createAll(context);
                    }
                }
            }, localeChangedFilter);
        } else {
            // We don't need to startServices for sub-process that is doing some tasks.
            // (screenshots, sweetsweetdesserts or tuner ..)
            String processName = ActivityThread.currentProcessName();
            ApplicationInfo info = getApplicationInfo();
            if (processName != null && processName.startsWith(info.processName + ":")) {
                return;
            }
            // For a secondary user, boot-completed will never be called because it has already
            // been broadcasted on startup for the primary SystemUI process.  Instead, for
            // components which require the SystemUI component to be initialized per-user, we
            // start those components now for the current non-system user.
            startSecondaryUserServicesIfNeeded();
        }

        IntentFilter bootToostFilter = new IntentFilter();
        bootToostFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        bootToostFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
        bootToostFilter.addAction(Intent.ACTION_SHUTDOWN);
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                    boolean enable = Prefs.getBoolean(context, "wifiEnabled", false);
                    if (enable) {
                        wm.setWifiEnabled(true);
                        Prefs.remove(context, "wifiEnabled");
                    }
                } else if (Intent.ACTION_SHUTDOWN.equals(intent.getAction())) {
                    if (wm.isWifiEnabled()) {
                        Prefs.putBoolean(context, "wifiEnabled", true);
                        wm.setWifiEnabled(false);
                    }
                }
            }
        }, bootToostFilter);
    }

    // AW:Added for BOOTEVENT
    private static boolean sBootEventenable = SystemProperties.getBoolean("persist.sys.bootevent", true);
    static void logBootEvent(String bootevent) {
        if (!sBootEventenable) {
            return ;
        }
        FileOutputStream fos =null;
        try {
            fos = new FileOutputStream("/proc/bootevent");
            fos.write(bootevent.getBytes());
            fos.flush();
        } catch (FileNotFoundException e) {
            Log.e("BOOTEVENT","Failure open /proc/bootevent,not found!",e);
        } catch (java.io.IOException e) {
            Log.e("BOOTEVENT","Failure open /proc/bootevent entry",e);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e ("BOOTEVENT","Failure close /proc/bootevent entry",e);
                }
            }
        }
    }

    /**
     * Makes sure that all the SystemUI services are running. If they are already running, this is a
     * no-op. This is needed to conditinally start all the services, as we only need to have it in
     * the main process.
     * <p>This method must only be called from the main thread.</p>
     */

    public void startServicesIfNeeded() {
        String[] names = getResources().getStringArray(R.array.config_systemUIServiceComponents);
        startServicesIfNeeded(names);
    }

    /**
     * Ensures that all the Secondary user SystemUI services are running. If they are already
     * running, this is a no-op. This is needed to conditinally start all the services, as we only
     * need to have it in the main process.
     * <p>This method must only be called from the main thread.</p>
     */
    void startSecondaryUserServicesIfNeeded() {
        String[] names =
                  getResources().getStringArray(R.array.config_systemUIServiceComponentsPerUser);
        startServicesIfNeeded(names);
    }

    private void startServicesIfNeeded(String[] services) {
        if (mServicesStarted) {
            return;
        }
        mNames = services;
        mServices = new SystemUI[services.length];

        if (!mBootCompleted) {
            // check to see if maybe it was already completed long before we began
            // see ActivityManagerService.finishBooting()
            if ("1".equals(SystemProperties.get("sys.boot_completed"))) {
                mBootCompleted = true;
                if (DEBUG) Log.v(TAG, "BOOT_COMPLETED was already sent");
            }
        }

        // AW:Added for BOOTEVENT
        logBootEvent("SystemUIService:Starting SystemUI services");
        Log.v(TAG, "Starting SystemUI services for user " +
                Process.myUserHandle().getIdentifier() + ".");
        TimingsTraceLog log = new TimingsTraceLog("SystemUIBootTiming",
                Trace.TRACE_TAG_APP);
        log.traceBegin("StartServices");
        final int N = services.length;
        for (int i = 0; i < N; i++) {
            String clsName = services[i];
            if (!mBootCompleted && !mBaseStartServices.contains(clsName)) {
                continue;
            }
            if (DEBUG) Log.d(TAG, "loading: " + clsName);
            log.traceBegin("StartServices" + clsName);
            long ti = System.currentTimeMillis();
            Class cls;
            try {
                cls = Class.forName(clsName);
                mServices[i] = (SystemUI) cls.newInstance();
            } catch(ClassNotFoundException ex){
                throw new RuntimeException(ex);
            } catch (IllegalAccessException ex) {
                throw new RuntimeException(ex);
            } catch (InstantiationException ex) {
                throw new RuntimeException(ex);
            }

            mServices[i].mContext = this;
            mServices[i].mComponents = mComponents;
            if (DEBUG) Log.d(TAG, "running: " + mServices[i]);
            mServices[i].start();
            log.traceEnd();

            // Warn if initialization of component takes too long
            ti = System.currentTimeMillis() - ti;
            if (ti > 1000) {
                Log.w(TAG, "Initialization of " + cls.getName() + " took " + ti + " ms");
            }
            if (ti > 30) {
                // AW:Added for BOOTEVENT
                logBootEvent("SystemUIService: running " + cls.getName() + " took " + ti + " ms");
            }
            if (mBootCompleted) {
                mServices[i].onBootCompleted();
            }
        }
        log.traceEnd();
        Dependency.get(PluginManager.class).addPluginListener(
                new PluginListener<OverlayPlugin>() {
                    private ArraySet<OverlayPlugin> mOverlays;

                    @Override
                    public void onPluginConnected(OverlayPlugin plugin, Context pluginContext) {
                        StatusBar statusBar = getComponent(StatusBar.class);
                        if (statusBar != null) {
                            plugin.setup(statusBar.getStatusBarWindow(),
                                    statusBar.getNavigationBarView());
                        }
                        // Lazy init.
                        if (mOverlays == null) mOverlays = new ArraySet<>();
                        if (plugin.holdStatusBarOpen()) {
                            mOverlays.add(plugin);
                            Dependency.get(StatusBarWindowManager.class).setStateListener(b ->
                                    mOverlays.forEach(o -> o.setCollapseDesired(b)));
                            Dependency.get(StatusBarWindowManager.class).setForcePluginOpen(
                                    mOverlays.size() != 0);

                        }
                    }

                    @Override
                    public void onPluginDisconnected(OverlayPlugin plugin) {
                        mOverlays.remove(plugin);
                        Dependency.get(StatusBarWindowManager.class).setForcePluginOpen(
                                mOverlays.size() != 0);
                    }
                }, OverlayPlugin.class, true /* Allow multiple plugins */);

        mServicesStarted = true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (mServicesStarted) {
            int len = mServices.length;
            for (int i = 0; i < len; i++) {
                if (mServices[i] != null) {
                    mServices[i].onConfigurationChanged(newConfig);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getComponent(Class<T> interfaceType) {
        return (T) mComponents.get(interfaceType);
    }

    public SystemUI[] getServices() {
        return mServices;
    }
}
