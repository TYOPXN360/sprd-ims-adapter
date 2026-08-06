package com.vp19.sprdims.adapter.prototype;

import android.os.Bundle;
import android.os.Message;
import android.telephony.ims.ImsCallProfile;
import android.telephony.ims.ImsCallSessionListener;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ImsStreamMediaProfile;
import android.telephony.ims.stub.ImsCallSessionImplBase;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Outgoing IMS call session backed by the Spreadtrum radio HIDL.
 *
 * {@code start(callee, profile)} issues {@code IExtRadio.videoPhoneDial(serial,
 * VideoPhoneDial)} (transaction 0x8c) — the Spreadtrum IMS call entry that
 * stock ims.apk uses. The modem reports progress through
 * {@code IIMSRadioIndication.IMSCallStateChangedInd}; each indication advances
 * the framework session (progressing, then initiated once ringing starts).
 * {@code terminate} issues {@code IExtRadio.notifyIMSCallEnd(serial, 0)}.
 */
final class SprdImsCallSession extends ImsCallSessionImplBase {
    private static final String TAG = "Vp19SprdIms";

    private final int slotId;
    private final Object radio;
    private final Class<?> radioClass;
    private final SprdImsService.CallStateListener stateListener;
    private final boolean incoming;
    private volatile ImsCallSessionListener listener;
    private volatile String callId;
    private volatile String callerNumber;
    private int serialCounter = 10;
    private volatile boolean started;
    private volatile boolean initiated;

    SprdImsCallSession(int slotId, Object radio, Class<?> radioClass) {
        this(slotId, radio, radioClass, false);
    }

    SprdImsCallSession(int slotId, Object radio, Class<?> radioClass, boolean incoming) {
        this.slotId = slotId;
        this.radio = radio;
        this.radioClass = radioClass;
        this.incoming = incoming;
        this.stateListener = new SprdImsService.CallStateListener() {
            @Override
            public void onImsCallStateChanged() {
                onModemCallStateChanged();
            }

            @Override
            public void onImsCurrentCalls(int[] states, boolean[] isMt, String[] numbers) {
                SprdImsCallSession.this.onImsCurrentCalls(states, isMt, numbers);
            }
        };
        SprdImsService.addCallStateListener(stateListener);
    }

    private void onModemCallStateChanged() {
        // IMSCallStateChangedInd alone does not tell whether the call is
        // ringing, active, or ended; re-query the modem call list and let
        // onImsCurrentCalls drive the framework state.
        Log.i(TAG, "modem IMS call state changed -> querying current calls");
    }

    /**
     * Drives framework session state from the modem's current IMS call list.
     * CallState: ACTIVE=0 HOLDING=1 DIALING=2 ALERTING=3 INCOMING=4 WAITING=5.
     */
    private void onImsCurrentCalls(int[] states, boolean[] isMt, String[] numbers) {
        ImsCallSessionListener l = listener;
        if (l == null) {
            return;
        }
        boolean hasActive = false;
        boolean hasRinging = false;
        for (int i = 0; i < states.length; i++) {
            int s = states[i];
            if (s == 0 || s == 1) {
                hasActive = true;
            } else if (s == 3) {
                hasRinging = true;
            }
        }
        if (states.length == 0 || !started) {
            if (started) {
                started = false;
                Log.i(TAG, "modem call list empty -> callSessionTerminated");
                try {
                    l.callSessionTerminated(new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED, 0));
                } catch (Throwable t) {
                    Log.e(TAG, "callSessionTerminated failed", t);
                }
            }
            return;
        }
        if (hasActive) {
            if (!initiated) {
                initiated = true;
                Log.i(TAG, "modem call active -> callSessionInitiated");
                try {
                    l.callSessionInitiated(new ImsCallProfile(
                            ImsCallProfile.SERVICE_TYPE_NORMAL, ImsCallProfile.CALL_TYPE_VOICE));
                } catch (Throwable t) {
                    Log.e(TAG, "callSessionInitiated failed", t);
                }
            }
            // Already initiated: keep ACTIVE, do not send progressing which
            // would roll the UI back to "calling".
        } else if (hasRinging) {
            if (!initiated) {
                initiated = true;
                Log.i(TAG, "modem call ringing -> callSessionProgressing");
                try {
                    l.callSessionProgressing(new ImsStreamMediaProfile());
                } catch (Throwable t) {
                    Log.e(TAG, "callSessionProgressing failed", t);
                }
            }
            // If already initiated, ignore ringing indications (post-accept
            // modem reports may still say ALERTING briefly).
        }
    }

    void setCallerNumber(String number) {
        this.callerNumber = number;
        this.started = true;
    }

    void setCallId(String id) {
        this.callId = id;
    }

    @Override
    public void setListener(ImsCallSessionListener listener) {
        this.listener = listener;
    }

    @Override
    public void start(String callee, ImsCallProfile profile) {
        Log.i(TAG, "start callee=" + callee + " profile.callType=" + (profile != null ? profile.getCallType() : -1));
        started = true;
        initiated = false;
        try {
            int serial = ++serialCounter;
            // Spreadtrum IMS call entry: IExtRadio.videoPhoneDial(serial,
            // VideoPhoneDial). Stock ims.apk uses this for calls; standard
            // IRadio.dial is routed by RILJ (CS domain) and does not establish
            // an IMS call on this device (CS voice is not registered).
            ClassLoader loader = SprdImsCallSession.class.getClassLoader();
            Class<?> vpdClass = Class.forName(
                    "vendor.sprd.hardware.radio.V1_0.VideoPhoneDial", true, loader);
            Object vpd = vpdClass.newInstance();
            vpdClass.getField("address").set(vpd, callee);
            vpdClass.getField("clir").setInt(vpd, 0);
            vpdClass.getField("subAddress").set(vpd, "");

            Method dialMethod = radioClass.getMethod("videoPhoneDial", int.class, vpdClass);
            dialMethod.invoke(radio, serial, vpd);
            callId = String.valueOf(serial);
            Log.i(TAG, "IExtRadio.videoPhoneDial issued serial=" + serial + " callee=" + callee);
        } catch (Throwable error) {
            Log.e(TAG, "IExtRadio.videoPhoneDial failed", error);
            ImsCallSessionListener l = listener;
            if (l != null) {
                l.callSessionInitiatedFailed(new ImsReasonInfo(
                        ImsReasonInfo.CODE_LOCAL_CALL_TERMINATED, 0));
            }
        }
    }

    @Override
    public void terminate(int reason) {
        Log.i(TAG, "terminate reason=" + reason);
        try {
            int serial = ++serialCounter;
            // Hang up the call: IExtRadio.hangup(serial, callIndex)
            // (transaction 0x0d). The modem call index is 1 for a single
            // call; callId is the framework session id, NOT the modem index,
            // so do not parse it here (parsing it produced wrong hangup
            // targets and left the peer still ringing).
            int callIndex = 1;
            Method hangupMethod = radioClass.getMethod("hangup", int.class, int.class);
            hangupMethod.invoke(radio, serial, callIndex);
            Log.i(TAG, "IExtRadio.hangup issued serial=" + serial + " callIndex=" + callIndex);
        } catch (Throwable error) {
            Log.e(TAG, "IExtRadio.hangup failed", error);
        }
        SprdImsService.removeCallStateListener(stateListener);
        started = false;
        ImsCallSessionListener l = listener;
        if (l != null) {
            try {
                l.callSessionTerminated(new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED, 0));
            } catch (Throwable t) {
                Log.e(TAG, "callSessionTerminated failed", t);
            }
        }
    }

    @Override
    public void close() {
        Log.i(TAG, "close slot=" + slotId);
        SprdImsService.removeCallStateListener(stateListener);
        started = false;
        super.close();
    }

    @Override
    public void accept(int callType, ImsStreamMediaProfile profile) {
        Log.i(TAG, "accept callType=" + callType);
        started = true;
        initiated = true;
        try {
            int serial = ++serialCounter;
            // Standard IRadio.acceptCall (transaction 0x27) answers the ringing
            // MT call; the call list then shows ACTIVE.
            Method acceptMethod = radioClass.getMethod("acceptCall", int.class);
            acceptMethod.invoke(radio, serial);
            Log.i(TAG, "IExtRadio.acceptCall issued serial=" + serial);
        } catch (Throwable error) {
            Log.e(TAG, "IExtRadio.acceptCall failed", error);
        }
        ImsCallSessionListener l = listener;
        if (l != null) {
            try {
                l.callSessionInitiated(new ImsCallProfile(
                        ImsCallProfile.SERVICE_TYPE_NORMAL, ImsCallProfile.CALL_TYPE_VOICE));
            } catch (Throwable t) {
                Log.e(TAG, "callSessionInitiated after accept failed", t);
            }
        }
    }

    @Override
    public void reject(int reason) {
        Log.i(TAG, "reject reason=" + reason);
        try {
            int serial = ++serialCounter;
            // Standard IRadio.rejectCall (transaction 0x12).
            Method rejectMethod = radioClass.getMethod("rejectCall", int.class);
            rejectMethod.invoke(radio, serial);
            Log.i(TAG, "IExtRadio.rejectCall issued serial=" + serial);
        } catch (Throwable error) {
            Log.e(TAG, "IExtRadio.rejectCall failed", error);
        }
        SprdImsService.removeCallStateListener(stateListener);
        started = false;
        ImsCallSessionListener l = listener;
        if (l != null) {
            try {
                l.callSessionTerminated(new ImsReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED, 0));
            } catch (Throwable t) {
                Log.e(TAG, "callSessionTerminated failed", t);
            }
        }
    }

    @Override
    public String getCallId() {
        return callId;
    }

    @Override
    public ImsCallProfile getCallProfile() {
        ImsCallProfile profile = new ImsCallProfile(
                ImsCallProfile.SERVICE_TYPE_NORMAL, ImsCallProfile.CALL_TYPE_VOICE);
        if (callerNumber != null) {
            // Framework reads the MT number from EXTRA_OI ("oi"). Assign the
            // public mCallExtras field directly for guaranteed delivery.
            Bundle extras = new Bundle();
            extras.putString(ImsCallProfile.EXTRA_OI, callerNumber);
            extras.putString(ImsCallProfile.EXTRA_OEM_EXTRAS, callerNumber);
            extras.putString(ImsCallProfile.EXTRA_REMOTE_URI, "tel:" + callerNumber);
            profile.mCallExtras = extras;
            Log.i(TAG, "getCallProfile extras set oi=" + callerNumber
                    + " verify=" + profile.getCallExtra(ImsCallProfile.EXTRA_OI, "MISSING"));
        }
        return profile;
    }

    @Override
    public ImsCallProfile getLocalCallProfile() {
        return getCallProfile();
    }

    @Override
    public ImsCallProfile getRemoteCallProfile() {
        return getCallProfile();
    }

    @Override
    public boolean isInCall() {
        return initiated;
    }

    @Override
    public void sendDtmf(char c, Message result) {
        if (result != null) {
            result.sendToTarget();
        }
    }
}
