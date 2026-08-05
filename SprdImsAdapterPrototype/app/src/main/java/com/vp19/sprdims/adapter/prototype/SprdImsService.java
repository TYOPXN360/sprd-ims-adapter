package com.vp19.sprdims.adapter.prototype;

import android.os.HwBinder;
import android.os.IHwBinder;
import android.telephony.ims.ImsService;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Android 11 IMS bridge for the Spreadtrum private radio HIDL.
 *
 * The factory IMS services (android.telephony.ims.ImsService) and MMTEL
 * feature are fully wired to Android 11's ImsResolver. The legacy vendor
 * modem is driven through vendor.sprd.hardware.radio@1.0::IExtRadio/slot1:
 * callbacks are registered, IMS is enabled, and bearer/voice-availability
 * queries confirm the modem has IMS voice. Registration and voice capability
 * are reported to the framework from the HIDL indications.
 */
public final class SprdImsService extends ImsService {
    private static final String TAG = "Vp19SprdIms";
    static final String EXT_RADIO = "vendor.sprd.hardware.radio@1.0::IExtRadio";
    static final String EXT_RADIO_INSTANCE = "slot1";

    private static volatile ImsRegistrationImplBase registration = new ImsRegistrationImplBase();
    private static final java.util.List<CallStateListener> CALL_STATE_LISTENERS =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    /** Radio proxy shared with call sessions; set by the HIDL bridge. */
    static volatile Object radioProxy;
    static volatile Class<?> radioProxyClass;
    private static volatile boolean mtCallNotified;
    private static volatile long mtCallNotifiedAt;
    private static volatile SprdImsConfigImplBase configImpl;

    @Override
    public MmTelFeature createMmTelFeature(int slotId) {
        Log.i(TAG, "createMmTelFeature slot=" + slotId);
        probeSprdImsRadioAsync();
        SprdHidlRegistrationBridge.connectAsync(new RegistrationBridgeListener() {
            @Override
            public void onImsRegistered() {
                Log.i(TAG, "HIDL indication: IMS registered, reporting to framework");
                registration.onRegistered(ImsRegistrationImplBase.REGISTRATION_TECH_LTE);
            }
        });
        return new SprdMmTelFeature(slotId);
    }

    @Override
    public ImsRegistrationImplBase getRegistration(int slotId) {
        Log.i(TAG, "getRegistration slot=" + slotId);
        return registration;
    }

    @Override
    public android.telephony.ims.stub.ImsConfigImplBase getConfig(int slotId) {
        Log.i(TAG, "getConfig slot=" + slotId);
        if (configImpl == null) {
            synchronized (SprdImsService.class) {
                if (configImpl == null) {
                    configImpl = new SprdImsConfigImplBase(slotId);
                }
            }
        }
        return configImpl;
    }

    /** Modem reports WFC registration (IMSWifiParamInd); reflect to framework. */
    static void reportWfcRegistered() {
        Log.i(TAG, "WFC registered, reporting IMS WIFI registration to framework");
        registration.onRegistered(ImsRegistrationImplBase.REGISTRATION_TECH_NW);
    }

    private static void probeSprdImsRadioAsync() {
        new Thread(() -> {
            try {
                IHwBinder service = HwBinder.getService(EXT_RADIO, EXT_RADIO_INSTANCE);
                Log.i(TAG, "IExtRadio/slot1 wait-probe present=" + (service != null));
            } catch (Exception e) {
                Log.e(TAG, "IExtRadio/slot1 wait-probe failed", e);
            }
        }, "vp19-ext-radio-probe").start();
    }

    /** Bridge callback used to drive framework IMS registration state. */
    interface RegistrationBridgeListener {
        void onImsRegistered();
    }

    /** Called when the modem reports an IMS call state change indication. */
    interface CallStateListener {
        void onImsCallStateChanged();

        /** Modem answered getIMSCurrentCalls: per-call state (CallState) and flags. */
        void onImsCurrentCalls(int[] states, boolean[] isMt, String[] numbers);
    }

    /** Modem sent IMSCallStateChangedInd: re-query the call list. */
    static void requestImsCurrentCalls() {
        Object radio = radioProxy;
        Class<?> radioClass = radioProxyClass;
        if (radio == null || radioClass == null) {
            return;
        }
        try {
            Method get = radioClass.getMethod("getIMSCurrentCalls", int.class);
            get.invoke(radio, 100);
            Log.i(TAG, "IExtRadio.getIMSCurrentCalls requested (serial=100)");
        } catch (Throwable t) {
            Log.e(TAG, "IExtRadio.getIMSCurrentCalls failed", t);
        }
    }

    /** Modem answered getIMSCurrentCalls; forward to the active session. */
    static void onImsCurrentCalls(int[] states, boolean[] isMt, String[] numbers) {
        if (CALL_STATE_LISTENERS.isEmpty()) {
            return;
        }
        CallStateListener active = CALL_STATE_LISTENERS.get(CALL_STATE_LISTENERS.size() - 1);
        try {
            active.onImsCurrentCalls(states, isMt, numbers);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Called from the generated smali when the modem answers
     * getIMSCurrentCalls. The ArrayList contains android.hardware.radio.V1_0.Call
     * objects; extract state/isMT/number via reflection and dispatch.
     */
    static void onImsCurrentCallsRaw(java.util.ArrayList<?> calls) {
        int n = calls == null ? 0 : calls.size();
        int[] states = new int[n];
        boolean[] isMt = new boolean[n];
        String[] numbers = new String[n];
        int[] indexes = new int[n];
        for (int i = 0; i < n; i++) {
            Object call = calls.get(i);
            try {
                Object stateVal = call.getClass().getField("state").get(call);
                states[i] = stateVal instanceof Number
                        ? ((Number) stateVal).intValue()
                        : stateVal != null ? stateVal.hashCode() : -1;
                Object mtVal = call.getClass().getField("isMT").get(call);
                isMt[i] = mtVal instanceof Boolean
                        ? (Boolean) mtVal
                        : mtVal instanceof Number && ((Number) mtVal).byteValue() != 0;
                Object num = call.getClass().getField("number").get(call);
                numbers[i] = num == null ? "" : num.toString();
                Object idx = call.getClass().getField("index").get(call);
                indexes[i] = idx instanceof Number ? ((Number) idx).intValue() : i + 1;
            } catch (Throwable t) {
                Log.e(TAG, "onImsCurrentCallsRaw field access failed", t);
            }
        }
        Log.i(TAG, "getIMSCurrentCalls: count=" + n);
        // Detect MT (incoming) calls regardless of session state; notify once
        // per incoming call (debounced) until the call leaves the list.
        boolean hasMt = false;
        for (int i = 0; i < n; i++) {
            int s = states[i];
            if ((s == 4 || s == 5) && isMt[i]) {
                hasMt = true;
                break;
            }
        }
        if (!hasMt) {
            mtCallNotified = false;
        } else {
            // MT call handled here; do not also dispatch to the session (which
            // would create duplicate incoming sessions and confuse hangup).
            // Also debounce: only notify once per incoming call per 8 seconds.
            if (!mtCallNotified || System.currentTimeMillis() - mtCallNotifiedAt > 8000) {
                mtCallNotified = true;
                mtCallNotifiedAt = System.currentTimeMillis();
                String num = numbers[0] != null ? numbers[0] : "";
                String callId = indexes[0] > 0 ? String.valueOf(indexes[0]) : null;
                Log.i(TAG, "modem reports MT call number=" + num + " callId=" + callId);
                SprdMmTelFeature.notifyIncomingCall(num, callId);
            }
            return;
        }
        onImsCurrentCalls(states, isMt, numbers);
    }

    static void addCallStateListener(CallStateListener listener) {
        CALL_STATE_LISTENERS.add(listener);
    }

    static void removeCallStateListener(CallStateListener listener) {
        CALL_STATE_LISTENERS.remove(listener);
    }

    static void notifyCallStateChanged() {
        // Only the most recently registered session listener is active for the
        // current call. Older sessions must not keep responding to modem
        // indications after their call ended.
        if (CALL_STATE_LISTENERS.isEmpty()) {
            return;
        }
        CallStateListener active = CALL_STATE_LISTENERS.get(CALL_STATE_LISTENERS.size() - 1);
        try {
            active.onImsCallStateChanged();
        } catch (Throwable ignored) {
        }
    }
}
