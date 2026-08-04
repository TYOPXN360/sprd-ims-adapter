package com.android.ims.internal;

import java.util.List;

/**
 * Android 8 Spreadtrum IMS extension API reconstructed from factory
 * boot-framework.vdex. Declarations only: this local shim deliberately has
 * no Binder implementation and must not be deployed as a call-enabling fix.
 */
public interface IImsServiceEx extends android.os.IInterface {
    void addImsPdnStateListener(int phoneId, IImsPdnStateListener listener);
    int cancelCurrentRequest();
    int getAliveCallJitter();
    int getAliveCallLose();
    int getAliveCallRtt();
    int getCLIRStatus(int phoneId);
    int getCallType();
    void getCallWaitingStatus(int phoneId);
    String getCurLocalAddress();
    String getCurPcscfAddress();
    int getCurrentImsFeature();
    int getCurrentImsVideoState();
    void getImsCNIInfor();
    String getImsPcscfAddress();
    String getImsRegAddress();
    int getVolteRegisterState();
    boolean isSupportMobike();
    void notifyNetworkUnavailable();
    void notifySrvccCallInfos(List infos);
    void notifyVideoCapabilityChange();
    void registerforImsRegisterStateChanged(IImsRegisterListener listener);
    int releaseVoWifiResource();
    void removeImsPdnStateListener(int phoneId, IImsPdnStateListener listener);
    void setImsServiceListener(IImsServiceListenerEx listener);
    void setMonitorPeriodForNoData(int period);
    int setVoWifiUnavailable(int phoneId, boolean unavailable);
    void setVowifiRegister(int state);
    void showVowifiNotification();
    int startHandover(int type);
    void startMobike();
    int switchImsFeature(int feature);
    void terminateCalls(int phoneId);
    void unregisterforImsRegisterStateChanged(IImsRegisterListener listener);
    int updateCLIRStatus(int phoneId);

    abstract class Stub extends android.os.Binder implements IImsServiceEx {
        public Stub() { attachInterface(null, "com.android.ims.internal.IImsServiceEx"); }
        public static IImsServiceEx asInterface(android.os.IBinder binder) { return null; }
        @Override public android.os.IBinder asBinder() { return this; }
    }
}
