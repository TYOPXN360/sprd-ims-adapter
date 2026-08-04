package com.android.ims.internal;

/**
 * Link-only Android 8 VoWiFi contract. No provider is installed, so
 * asInterface always returns null and the stock IMS code retains its
 * disconnected VoWiFi path.
 */
public interface IVoWifiCall extends android.os.IInterface {
    abstract class Stub extends android.os.Binder implements IVoWifiCall {
        public Stub() { attachInterface(null, "com.android.ims.internal.IVoWifiCall"); }
        public static IVoWifiCall asInterface(android.os.IBinder binder) { return null; }
        @Override public android.os.IBinder asBinder() { return this; }
    }
}
