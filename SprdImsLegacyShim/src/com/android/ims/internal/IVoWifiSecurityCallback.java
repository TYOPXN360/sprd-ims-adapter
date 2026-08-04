package com.android.ims.internal;

public interface IVoWifiSecurityCallback extends android.os.IInterface {
    void onS2bStateChanged(String state);

    abstract class Stub extends android.os.Binder implements IVoWifiSecurityCallback {
        public Stub() { attachInterface(null, "com.android.ims.internal.IVoWifiSecurityCallback"); }
        public static IVoWifiSecurityCallback asInterface(android.os.IBinder binder) { return null; }
        @Override public android.os.IBinder asBinder() { return this; }
    }
}
