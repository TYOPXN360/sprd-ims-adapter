package com.android.ims.internal;

/** Android 8 Spreadtrum IMS extension API, reconstructed from stock VDEX. */
public interface IImsServiceListenerEx {
    void imsCallEnd(int type);
    void imsPdnStateChange(int state);
    void onDPDDisconnected();
    void onMediaQualityChanged(boolean isVideo, int loss, int jitter, int rtt);
    void onNoRtpReceived(boolean isVideo);
    void onRtpReceived(boolean isVideo);
    void onSetVowifiRegister(int state);
    void onSrvccFaild();
    void onVideoStateChanged(int state);
    void onVoWiFiError(int errorCode);
    void operationFailed(int type, String reason, int errorCode);
    void operationSuccessed(int type, int result);
}
