package com.android.ims.internal;

public interface IVoWifiSecurity extends android.os.IInterface {
    boolean deleteTunelIpsec(int type);
    int getState(int type);
    void registerCallback(IVoWifiSecurityCallback callback);
    int start(int type, int subId);
    void startMobike(int type);
    int startWithAddr(boolean handover, int type, int subId, String address);
    void stop(int type, boolean handover);
    boolean switchLoginIpVersion(int type, int version);
    void unregisterCallback(IVoWifiSecurityCallback callback);

    abstract class Stub extends android.os.Binder implements IVoWifiSecurity {
        public Stub() { attachInterface(null, "com.android.ims.internal.IVoWifiSecurity"); }
        public static IVoWifiSecurity asInterface(android.os.IBinder binder) { return null; }
        @Override public android.os.IBinder asBinder() { return this; }
    }
}
