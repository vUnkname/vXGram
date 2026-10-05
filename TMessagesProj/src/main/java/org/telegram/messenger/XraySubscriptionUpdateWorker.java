package org.telegram.messenger;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import tw.nekomimi.nekogram.utils.ProxyUtil;

public class XraySubscriptionUpdateWorker extends Worker {

    public static final String KEY_URL = "subscription_url";

    public XraySubscriptionUpdateWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String url = getInputData().getString(KEY_URL);
        if (TextUtils.isEmpty(url)) {
            return Result.failure();
        }
        XraySubscriptionStore.Entry entry = XraySubscriptionStore.getByUrl(url);
        if (entry == null || !entry.autoUpdate) {
            return Result.success();
        }
        boolean ok = ProxyUtil.refreshSubscriptionUrlSync(url);
        return ok ? Result.success() : Result.retry();
    }
}
