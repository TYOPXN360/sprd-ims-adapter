package com.android.ims.internal;

public interface IVoWifiUT extends android.os.IInterface {
    int queryCallBarring(int type);
    int queryCallForward();
    int queryCallWaiting();
    void registerCallback(IVoWifiUTCallback callback);
    void unregisterCallback(IVoWifiUTCallback callback);
    int updateCLIR(boolean enabled);
    int updateCallBarring(int type, boolean enabled, String[] numbers, int serviceClass);
    int updateCallForward(int action, int reason, String number, int timeSeconds, int serviceClass);
    int updateCallWaiting(boolean enabled);
    boolean updateIPAddr(String localAddress, String pcscfAddress);

    abstract class Stub extends android.os.Binder implements IVoWifiUT {
        public Stub() { attachInterface(null, "com.android.ims.internal.IVoWifiUT"); }
        public static IVoWifiUT asInterface(android.os.IBinder binder) { return null; }
        @Override public android.os.IBinder asBinder() { return this; }
    }
}
