package com.android.ims.internal;

/** Android 8 Spreadtrum IMS extension API, reconstructed from stock VDEX. */
public interface IImsUtEx extends android.os.IInterface {
    int changeBarringPassword(int phoneId, String facility, String oldPassword, String newPassword);
    int getCallForwardingOption(int phoneId, int action, int reason, String number);
    int queryFacilityLock(int phoneId, String facility, String password, int serviceClass);
    int setCallForwardingOption(int phoneId, int action, int reason, int serviceClass,
            String number, int timeSeconds, String ruleSet);
    int setFacilityLock(int phoneId, String facility, boolean lockState, String password,
            int serviceClass);
    void setListenerEx(int phoneId, IImsUtListenerEx listener);

    abstract class Stub extends android.os.Binder implements IImsUtEx {
        public Stub() { attachInterface(null, "com.android.ims.internal.IImsUtEx"); }
        public static IImsUtEx asInterface(android.os.IBinder binder) { return null; }
        @Override public android.os.IBinder asBinder() { return this; }
    }
}
