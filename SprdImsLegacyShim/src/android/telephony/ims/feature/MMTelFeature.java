package android.telephony.ims.feature;

import android.app.PendingIntent;
import android.os.Message;

/**
 * Android 8 legacy IMS feature ABI used by Spreadtrum ImsServiceImpl.
 * Android 11's ImsServiceControllerCompat consumes this old controller ABI;
 * this type intentionally does not advertise Android 11 AIDL capabilities.
 */
public class MMTelFeature {
    public void addRegistrationListener(com.android.ims.internal.IImsRegistrationListener listener) {}
    public com.android.ims.ImsCallProfile createCallProfile(int sessionId, int callSessionType, int callType) { return null; }
    public com.android.ims.internal.IImsCallSession createCallSession(int sessionId,
            com.android.ims.ImsCallProfile profile,
            com.android.ims.internal.IImsCallSessionListener listener) { return null; }
    public void endSession(int sessionId) {}
    public com.android.ims.internal.IImsConfig getConfigInterface() { return null; }
    public com.android.ims.internal.IImsEcbm getEcbmInterface() { return null; }
    public com.android.ims.internal.IImsMultiEndpoint getMultiEndpointInterface() { return null; }
    public com.android.ims.internal.IImsCallSession getPendingCallSession(int sessionId, String callId) { return null; }
    public com.android.ims.internal.IImsUt getUtInterface() { return null; }
    public int getFeatureState() { return 0; }
    public boolean isConnected(int callSessionType, int callType) { return false; }
    public boolean isOpened() { return false; }
    public void onFeatureRemoved() {}
    public void removeRegistrationListener(com.android.ims.internal.IImsRegistrationListener listener) {}
    public void setUiTTYMode(int uiTtyMode, Message onComplete) {}
    public int startSession(PendingIntent incomingCallIntent,
            com.android.ims.internal.IImsRegistrationListener listener) { return 0; }
    public void turnOffIms() {}
    public void turnOnIms() {}
}
