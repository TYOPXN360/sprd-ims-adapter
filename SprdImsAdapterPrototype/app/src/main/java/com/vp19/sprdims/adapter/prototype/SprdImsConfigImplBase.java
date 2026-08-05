package com.vp19.sprdims.adapter.prototype;

import android.telephony.ims.stub.ImsConfigImplBase;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Android 11 ImsConfig bridge for the Spreadtrum modem.
 *
 * The Android 11 framework asks the IMS service for a per-slot
 * {@link ImsConfigImplBase}. This implementation answers the Wi-Fi calling
 * (WFC / VoWiFi) configuration items using values cached from the modem and
 * forwards framework set requests down to the modem via
 * {@code vendor.sprd.hardware.radio@1.0::IExtRadio} (notifyVoWifiEnable,
 * setVoiceDomain, setSmsBearer, enableWiFiParamReport).
 *
 * Mirrors the role of the factory {@code com.spreadtrum.ims.ImsConfigImpl}
 * inside the stock ims.apk, without the full VoWifiServiceImpl state machine:
 * registration is driven by the framework WFC switch, and the actual modem
 * commands are sent through the same HIDL proxy the rest of this adapter
 * uses.
 */
final class SprdImsConfigImplBase extends ImsConfigImplBase {
    private static final String TAG = "Vp19SprdIms";

    private final int slotId;
    private volatile boolean wfcEnabled;
    private volatile int wfcMode = 0; // WFC_MODE_WIFI_ONLY default

    // ImsConfig.ConfigConstants (framework hidden API; mirrored here).
    static final int CONFIG_VOICE_OVER_WIFI_SETTING = 0;
    static final int CONFIG_VOICE_OVER_WIFI_MODE = 1;
    static final int CONFIG_VOICE_OVER_WIFI_ROAMING = 2;
    static final int CONFIG_VOICE_OVER_WIFI_SETTING_STATUS = 3;

    SprdImsConfigImplBase(int slotId) {
        this.slotId = slotId;
    }

    boolean isWfcEnabled() {
        return wfcEnabled;
    }

    int getWfcMode() {
        return wfcMode;
    }

    void setWfcEnabled(boolean enabled) {
        if (wfcEnabled == enabled) {
            return;
        }
        wfcEnabled = enabled;
        Log.i(TAG, "WFC " + (enabled ? "enabled" : "disabled") + " slot=" + slotId);
        SprdVoWifiController.onWfcEnabledChanged(slotId, enabled);
        notifyConfigChanged(CONFIG_VOICE_OVER_WIFI_SETTING, enabled ? 1 : 0);
    }

    @Override
    public int getConfigInt(int item) {
        switch (item) {
            case CONFIG_VOICE_OVER_WIFI_SETTING:
            case CONFIG_VOICE_OVER_WIFI_SETTING_STATUS:
                return wfcEnabled ? 1 : 0;
            case CONFIG_VOICE_OVER_WIFI_MODE:
                return wfcMode;
            case CONFIG_VOICE_OVER_WIFI_ROAMING:
                return wfcEnabled ? 1 : 0;
            default:
                Log.d(TAG, "getConfigInt unknown item=" + item);
                return 0;
        }
    }

    @Override
    public String getConfigString(int item) {
        Log.d(TAG, "getConfigString item=" + item);
        return null;
    }

    @Override
    public int setConfig(int item, int value) {
        Log.i(TAG, "setConfig item=" + item + " value=" + value);
        switch (item) {
            case CONFIG_VOICE_OVER_WIFI_SETTING:
            case CONFIG_VOICE_OVER_WIFI_SETTING_STATUS:
                setWfcEnabled(value != 0);
                return CONFIG_RESULT_SUCCESS;
            case CONFIG_VOICE_OVER_WIFI_MODE:
                wfcMode = value;
                return CONFIG_RESULT_SUCCESS;
            case CONFIG_VOICE_OVER_WIFI_ROAMING:
                return CONFIG_RESULT_SUCCESS;
            default:
                return CONFIG_RESULT_UNKNOWN;
        }
    }

    @Override
    public int setConfig(int item, String value) {
        Log.i(TAG, "setConfig item=" + item + " value=" + value);
        return CONFIG_RESULT_UNKNOWN;
    }

    /**
     * Queries the modem's current WFC-capable state and reflects it to the
     * framework. Called once IMS registration is up.
     */
    void refreshFromModem() {
        // The modem reports WFC capability through
        // getIMSVoiceCallAvailability (bit mask). Bit 0x2 = VoWiFi.
        Object radio = SprdImsService.radioProxy;
        Class<?> radioClass = SprdImsService.radioProxyClass;
        if (radio == null || radioClass == null) {
            return;
        }
        try {
            Method avail = radioClass.getMethod("getIMSVoiceCallAvailability", int.class);
            avail.invoke(radio, 10 + slotId);
            Log.i(TAG, "IExtRadio.getIMSVoiceCallAvailability requested for WFC (serial=" + (10 + slotId) + ")");
        } catch (Throwable t) {
            Log.e(TAG, "WFC availability query failed", t);
        }
    }
}
