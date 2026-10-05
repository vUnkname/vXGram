package org.telegram.messenger;

import android.text.TextUtils;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class XraySubscriptionWorkScheduler {

    private static final String WORK_PREFIX = "xray_sub_update_";
    private static final int MIN_INTERVAL_MINUTES = 15;

    private XraySubscriptionWorkScheduler() {
    }

    public static void syncAll() {
        if (ApplicationLoader.applicationContext == null) {
            return;
        }
        for (XraySubscriptionStore.Entry entry : XraySubscriptionStore.getAll()) {
            if (entry == null || TextUtils.isEmpty(entry.url)) {
                continue;
            }
            if (entry.autoUpdate) {
                schedule(entry);
            } else {
                cancel(entry.url);
            }
        }
    }

    public static void schedule(XraySubscriptionStore.Entry entry) {
        if (entry == null || TextUtils.isEmpty(entry.url) || !entry.autoUpdate) {
            return;
        }
        int interval = entry.updateIntervalMinutes > 0 ? entry.updateIntervalMinutes : 1440;
        if (interval < MIN_INTERVAL_MINUTES) {
            interval = MIN_INTERVAL_MINUTES;
        }
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        Data input = new Data.Builder()
                .putString(XraySubscriptionUpdateWorker.KEY_URL, entry.url)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                XraySubscriptionUpdateWorker.class,
                interval,
                TimeUnit.MINUTES
        )
                .setConstraints(constraints)
                .setInputData(input)
                .build();
        WorkManager.getInstance(ApplicationLoader.applicationContext).enqueueUniquePeriodicWork(
                workName(entry.url),
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );
    }

    public static void cancel(String url) {
        if (ApplicationLoader.applicationContext == null || TextUtils.isEmpty(url)) {
            return;
        }
        WorkManager.getInstance(ApplicationLoader.applicationContext).cancelUniqueWork(workName(url));
    }

    private static String workName(String url) {
        return WORK_PREFIX + XraySubscriptionStore.normalizeUrl(url).hashCode();
    }
}
