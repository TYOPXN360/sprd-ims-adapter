package com.android.ims.internal;

public interface IVoWifiUTCallback extends android.os.IInterface {
    void onEvent(String event);

    abstract class Stub extends android.os.Binder implements IVoWifiUTCallback {
        public Stub() { attachInterface(null, "com.android.ims.internal.IVoWifiUTCallback"); }
        public static IVoWifiUTCallback asInterface(android.os.IBinder binder) { return null; }
        @Override public android.os.IBinder asBinder() { return this; }
    }
}
