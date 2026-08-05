package com.vp19.sprdims.adapter.prototype;

import android.util.Log;

import java.lang.reflect.Method;

/**
 * Wi-Fi calling (VoWiFi) controller for the Spreadtrum modem.
 *
 * The factory VoWifiServiceImpl keeps an entire state machine around the
 * modem's private HIDL commands. This adapter implements the equivalent
 * command surface in a compact, framework-driven form:
 *
 *  - {@code notifyVoWifiEnable(serial, enable)}   enable/disable WFC bearer
 *  - {@code enableWiFiParamReport(serial, enable)} modem WiFi param reporting
 *  - {@code setVoiceDomain(serial, domain)}       voice domain selection
 *  - {@code setSmsBearer(serial, bearer)}         SMS bearer selection
 *
 * The framework drives registration via {@link SprdImsConfigImplBase}
 * (WFC switch). Modem indications (IMSWifiParamInd, IMSNetworkInfoChangedInd)
 * arrive on the Vp19ImsRadioIndication callback and are forwarded here so the
 * adapter can keep the framework registration state in sync.
 */
final class SprdVoWifiController {
    private static final String TAG = "Vp19SprdIms";

    private static volatile boolean wfcEnabled;
    private static volatile boolean modemWfcAvailable;
    private static volatile int currentSerial = 1000;

    private SprdVoWifiController() {}

    /** Framework toggled the WFC switch; tell the modem. */
    static void onWfcEnabledChanged(int slotId, boolean enable) {
        wfcEnabled = enable;
        Log.i(TAG, "VoWiFi " + (enable ? "enable" : "disable") + " slot=" + slotId);
        send(slotId, "notifyVoWifiEnable", enable);
        if (enable) {
            send(slotId, "enableWiFiParamReport", true);
        }
    }

    /** Modem answered getIMSVoiceCallAvailability; bit 0x2 = VoWiFi. */
    static void onVoiceCallAvailability(int slotId, int availability) {
        boolean available = (availability & 0x2) != 0;
        Log.i(TAG, "modem voice call availability=0x" + Integer.toHexString(availability)
                + " (VoWiFi bit=" + available + ")");
        modemWfcAvailable = available;
    }

    /** Modem pushed IMSWiFiParamInd (registered over WiFi). */
    static void onWifiParamIndication(int slotId, java.util.List<?> wifiParams) {
        Log.i(TAG, "IMSWifiParamInd slot=" + slotId + " params=" + (wifiParams == null ? 0 : wifiParams.size()));
        // If the modem reports WiFi params, WFC registration is in progress.
        if (wifiParams != null && !wifiParams.isEmpty()) {
            SprdImsService.reportWfcRegistered();
        }
    }

    /** Modem pushed IMSNetworkInfoChangedInd. */
    static void onNetworkInfoChanged(int slotId, Object networkInfo) {
        Log.i(TAG, "IMSNetworkInfoChangedInd slot=" + slotId + " info=" + networkInfo);
    }

    /**
     * Sends a boolean VoWiFi command to the modem via the shared IExtRadio
     * proxy (reflection, matching the rest of the adapter).
     */
    private static void send(int slotId, String methodName, boolean value) {
        Object radio = SprdImsService.radioProxy;
        Class<?> radioClass = SprdImsService.radioProxyClass;
        if (radio == null || radioClass == null) {
            Log.w(TAG, "VoWiFi send " + methodName + ": radio not ready");
            return;
        }
        try {
            Method m = radioClass.getMethod(methodName, int.class, boolean.class);
            m.invoke(radio, currentSerial++, value);
            Log.i(TAG, "IExtRadio." + methodName + "(" + (currentSerial - 1) + ", " + value + ")");
        } catch (Throwable t) {
            Log.e(TAG, "IExtRadio." + methodName + " failed", t);
        }
    }
}
