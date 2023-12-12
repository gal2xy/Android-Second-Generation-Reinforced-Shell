package com.example.sourceapk;

import android.app.Application;
import android.util.Log;

public class MyApplication extends Application {
    String TAG = "demo";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "SourceApk Application onCreate: " + this);
    }
}
