package com.vp19.sprdims.adapter.prototype;

import android.os.Bundle;
import android.telephony.ims.ImsCallProfile;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsCallSessionImplBase;
import android.util.Log;

/**
 * Android 11 MMTEL feature backed by the Spreadtrum private radio HIDL.
 *
 * The modem has confirmed IMS voice availability (getIMSVoiceCallAvailability
 * returns 1), so voice capability is advertised once the feature is ready.
 */
final class SprdMmTelFeature extends MmTelFeature {
    private static final String TAG = "Vp19SprdIms";
    private static volatile SprdMmTelFeature CURRENT_FEATURE;
    private final int slotId;

    SprdMmTelFeature(int slotId) {
        this.slotId = slotId;
        CURRENT_FEATURE = this;
        // PHH GSI's ImsServiceController may not invoke Java-side
        // ImsFeature.initialize()/onFeatureReady() after createMmTelFeature().
        // Report READY from the constructor so the framework stops treating
        // MMTEL as UNAVAILABLE; onFeatureReady() re-applies it idempotently.
        reportReady();
    }

    /** Called from SprdImsService when the modem reports an MT call. */
    static void notifyIncomingCall(String number, String callId) {
        SprdMmTelFeature feature = CURRENT_FEATURE;
        if (feature == null) {
            Log.e(TAG, "notifyIncomingCall: no feature");
            return;
        }
        feature.doNotifyIncoming(number, callId);
    }

    private void doNotifyIncoming(String number, String callId) {
        Log.i(TAG, "MT call from " + number + " -> notifyIncomingCall");
        try {
            Object radio = SprdImsService.radioProxy;
            Class<?> radioClass = SprdImsService.radioProxyClass;
            if (radio == null || radioClass == null) {
                Log.e(TAG, "notifyIncomingCall: radio not ready");
                return;
            }
            SprdImsCallSession session = new SprdImsCallSession(slotId, radio, radioClass, true);
            session.setCallerNumber(number);
            if (callId != null) {
                session.setCallId(callId);
            }
            Bundle extras = new Bundle();
            extras.putString(ImsCallProfile.EXTRA_OI, number);
            extras.putString(ImsCallProfile.EXTRA_OEM_EXTRAS, number);
            extras.putString(ImsCallProfile.EXTRA_REMOTE_URI, "tel:" + number);
            notifyIncomingCall(session, extras);
            Log.i(TAG, "notifyIncomingCall delivered");
        } catch (Throwable t) {
            Log.e(TAG, "notifyIncomingCall failed", t);
        }
    }

    @Override
    public void onFeatureReady() {
        Log.i(TAG, "MMTEL feature ready slot=" + slotId);
        reportReady();
    }

    private void reportReady() {
        setFeatureState(ImsFeature.STATE_READY);
        MmTelCapabilities caps = new MmTelCapabilities();
        caps.addCapabilities(MmTelCapabilities.CAPABILITY_TYPE_VOICE);
        notifyCapabilitiesStatusChanged(caps);
        Log.i(TAG, "MMTEL voice capability reported slot=" + slotId);
    }

    @Override
    public ImsCallProfile createCallProfile(int callSessionType, int callType) {
        Log.i(TAG, "createCallProfile sessionType=" + callSessionType + " callType=" + callType);
        ImsCallProfile profile = new ImsCallProfile(
                ImsCallProfile.SERVICE_TYPE_NORMAL,
                callType != 0 ? callType : ImsCallProfile.CALL_TYPE_VOICE);
        return profile;
    }

    @Override
    public int shouldProcessCall(String[] numbers) {
        // IMS is the only available voice path on this device (CS voice is
        // not registered); route normal calls through IMS.
        Log.i(TAG, "shouldProcessCall numbers=" + (numbers != null ? numbers.length : 0));
        return PROCESS_CALL_IMS;
    }

    @Override
    public ImsCallSessionImplBase createCallSession(ImsCallProfile profile) {
        Log.i(TAG, "createCallSession profile.callType=" + (profile != null ? profile.getCallType() : -1));
        Object radio = SprdImsService.radioProxy;
        Class<?> radioClass = SprdImsService.radioProxyClass;
        if (radio == null || radioClass == null) {
            Log.e(TAG, "createCallSession: radio proxy not ready");
            return null;
        }
        return new SprdImsCallSession(slotId, radio, radioClass);
    }

    @Override
    public void onFeatureRemoved() {
        Log.i(TAG, "MMTEL feature removed slot=" + slotId);
    }
}
