package com.simswitch;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

public class MainActivity extends Activity {
    private TextView status;
    private int pendingSlot = -1;

    private final Shizuku.OnRequestPermissionResultListener permListener = (code, result) -> {
        if (result == PackageManager.PERMISSION_GRANTED && pendingSlot >= 0) switchTo(pendingSlot);
        else setStatus("لازم توافق على صلاحية Shizuku");
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(48, 48, 48, 48);

        status = new TextView(this);
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        status.setTextColor(Color.BLACK);

        root.addView(makeButton("SIM 1", 0));
        root.addView(makeButton("SIM 2", 1));
        root.addView(status);
        setContentView(root);

        Shizuku.addRequestPermissionResultListener(permListener);
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, 100);
    }

    @Override protected void onResume() { super.onResume(); showCurrent(); }

    @Override protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener);
        super.onDestroy();
    }

    private Button makeButton(String text, int slot) {
        Button bt = new Button(this);
        bt.setText(text);
        bt.setTextSize(24);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 300);
        lp.setMargins(0, 0, 0, 32);
        bt.setLayoutParams(lp);
        bt.setOnClickListener(v -> switchTo(slot));
        return bt;
    }

    private void setStatus(String s) { status.setText(s); }

    private void showCurrent() {
        try {
            int cur = SubscriptionManager.getDefaultDataSubscriptionId();
            SubscriptionManager sm = getSystemService(SubscriptionManager.class);
            for (int slot = 0; slot < 2; slot++) {
                SubscriptionInfo i = sm.getActiveSubscriptionInfoForSimSlotIndex(slot);
                if (i != null && i.getSubscriptionId() == cur) { setStatus("النت الحالي: SIM " + (slot + 1)); return; }
            }
        } catch (Throwable ignored) { }
        setStatus("اختار الشريحة");
    }

    private void switchTo(int slot) {
        pendingSlot = slot;
        try {
            if (!Shizuku.pingBinder()) { setStatus("شغّل تطبيق Shizuku الأول"); return; }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(1);
                return;
            }
            SubscriptionManager sm = getSystemService(SubscriptionManager.class);
            SubscriptionInfo info = sm.getActiveSubscriptionInfoForSimSlotIndex(slot);
            if (info == null) { setStatus("مفيش شريحة في SIM " + (slot + 1)); return; }

            HiddenApiBypass.addHiddenApiExemptions("L");
            IBinder binder = new ShizukuBinderWrapper(SystemServiceHelper.getSystemService("isub"));
            Class<?> stub = Class.forName("com.android.internal.telephony.ISub$Stub");
            Object isub = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
            isub.getClass().getMethod("setDefaultDataSubId", int.class).invoke(isub, info.getSubscriptionId());
            setStatus("تم: النت على SIM " + (slot + 1));
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            setStatus("فشل: " + c);
        }
    }
}
