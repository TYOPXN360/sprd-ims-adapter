package com.android.ims.internal;

public interface IVoWifiRegisterCallback extends android.os.IInterface {
    void onRegisterStateChanged(String state);

    abstract class Stub extends android.os.Binder implements IVoWifiRegisterCallback {
        public Stub() { attachInterface(null, "com.android.ims.internal.IVoWifiRegisterCallback"); }
        public static IVoWifiRegisterCallback asInterface(android.os.IBinder binder) { return null; }
        @Override public android.os.IBinder asBinder() { return this; }
    }
}
