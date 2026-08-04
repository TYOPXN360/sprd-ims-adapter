package com.android.ims.internal;

public interface IVoWifiCallCallback extends android.os.IInterface {
    void onEvent(String event);

    abstract class Stub extends android.os.Binder implements IVoWifiCallCallback {
        public Stub() { attachInterface(null, "com.android.ims.internal.IVoWifiCallCallback"); }
        public static IVoWifiCallCallback asInterface(android.os.IBinder binder) { return null; }
        @Override public android.os.IBinder asBinder() { return this; }
    }
}
