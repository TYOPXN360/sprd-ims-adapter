package com.android.ims.internal;

public interface IVoWifiRegister extends android.os.IInterface {
    int cliLogin(boolean isSos, boolean isHandover, String localAddress, String pcscfAddress,
            String imei, int subId, String imsi, int type, boolean isRoaming);
    int cliLogout();
    int cliOpen(int subId);
    int cliRefresh(int type, String info);
    int cliReset();
    int cliStart();
    int cliUpdateSettings(boolean enabled);
    void registerCallback(IVoWifiRegisterCallback callback);
    void unregisterCallback(IVoWifiRegisterCallback callback);

    abstract class Stub extends android.os.Binder implements IVoWifiRegister {
        public Stub() { attachInterface(null, "com.android.ims.internal.IVoWifiRegister"); }
        public static IVoWifiRegister asInterface(android.os.IBinder binder) { return null; }
        @Override public android.os.IBinder asBinder() { return this; }
    }
}
