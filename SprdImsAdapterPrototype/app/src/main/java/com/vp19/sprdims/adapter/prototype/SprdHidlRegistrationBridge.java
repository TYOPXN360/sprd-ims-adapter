package com.vp19.sprdims.adapter.prototype;

import android.util.Log;

import java.lang.reflect.Method;

/**
 * Drives the factory Spreadtrum IMS radio over HIDL.
 *
 * Callbacks are installed with setIMSResponseFunctions, IMS is enabled, and
 * state queries confirm the modem. Registration indications received on the
 * callback objects are forwarded through {@link SprdImsService.RegistrationBridgeListener}
 * so the Android 11 framework can report IMS registered / VoLTE voice ready.
 */
final class SprdHidlRegistrationBridge {
    private static final String TAG = "Vp19SprdIms";
    private static final String EXT_RADIO = "vendor.sprd.hardware.radio.V1_0.IExtRadio";
    private static final String RESPONSE = "vendor.sprd.hardware.radio.V1_0.IIMSRadioResponse";
    private static final String INDICATION = "vendor.sprd.hardware.radio.V1_0.IIMSRadioIndication";

    private SprdHidlRegistrationBridge() {}

    static void connectAsync(SprdImsService.RegistrationBridgeListener listener) {
        new Thread(() -> connect(listener), "vp19-ext-radio-register").start();
    }

    private static void connect(SprdImsService.RegistrationBridgeListener listener) {
        try {
            ClassLoader loader = SprdHidlRegistrationBridge.class.getClassLoader();
            Class<?> radioClass = Class.forName(EXT_RADIO, true, loader);
            Object radio = radioClass.getMethod("getService", String.class).invoke(null, "slot1");
            if (radio == null) {
                Log.w(TAG, "IExtRadio/slot1 unavailable");
                return;
            }
            SprdImsService.radioProxy = radio;
            SprdImsService.radioProxyClass = radioClass;
            Object response = Class.forName(
                    "com.vp19.sprdims.adapter.prototype.Vp19ImsRadioResponse", true, loader)
                    .getConstructor().newInstance();
            Object indication = Class.forName(
                    "com.vp19.sprdims.adapter.prototype.Vp19ImsRadioIndication", true, loader)
                    .getConstructor().newInstance();
            if (listener != null) {
                response.getClass().getMethod("setListener",
                        SprdImsService.RegistrationBridgeListener.class).invoke(null, listener);
                indication.getClass().getMethod("setListener",
                        SprdImsService.RegistrationBridgeListener.class).invoke(null, listener);
            }
            // Also register the standard radio callbacks so that IRadio.dial /
            // hangup responses (dialResponse, callStateChanged) reach this
            // adapter. IExtRadio implements android.hardware.radio V1_1, whose
            // setResponseFunctions takes the V1_0 standard callbacks.
            try {
                Object stdResponse = Class.forName(
                        "com.vp19.sprdims.adapter.prototype.Vp19StdRadioResponse", true, loader)
                        .getConstructor().newInstance();
                Object stdIndication = Class.forName(
                        "com.vp19.sprdims.adapter.prototype.Vp19StdRadioIndication", true, loader)
                        .getConstructor().newInstance();
                Method setStd = radioClass.getMethod("setResponseFunctions",
                        Class.forName("android.hardware.radio.V1_0.IRadioResponse", true, loader),
                        Class.forName("android.hardware.radio.V1_0.IRadioIndication", true, loader));
                setStd.invoke(radio, stdResponse, stdIndication);
                Log.i(TAG, "IExtRadio/slot1 standard radio callbacks registered");
            } catch (Throwable t) {
                Log.e(TAG, "IExtRadio/slot1 standard radio callback registration failed", t);
            }
            Method setCallbacks = radioClass.getMethod("setIMSResponseFunctions",
                    Class.forName(RESPONSE, true, loader), Class.forName(INDICATION, true, loader));
            setCallbacks.invoke(radio, response, indication);
            Log.i(TAG, "IExtRadio/slot1 IMS callbacks registered");
            enableIms(radioClass, radio);
        } catch (Throwable error) {
            Log.e(TAG, "IExtRadio/slot1 callback registration failed", error);
        }
    }

    /**
     * Requests the modem to start IMS registration. Mirrors stock
     * {@code ImsRIL.enableIMS(int serial)} (HIDL transaction 0xd1). The serial
     * only correlates the async response; modem behaviour does not depend on
     * its value. Result arrives on {@code IIMSRadioResponse.enableIMSResponse}.
     */
    private static void enableIms(Class<?> radioClass, Object radio) {
        try {
            Method enable = radioClass.getMethod("enableIMS", int.class);
            enable.invoke(radio, 1);
            Log.i(TAG, "IExtRadio/slot1 enableIMS requested (serial=1)");
            Method bearer = radioClass.getMethod("getIMSBearerState", int.class);
            bearer.invoke(radio, 2);
            Log.i(TAG, "IExtRadio/slot1 getIMSBearerState requested (serial=2)");
            Method avail = radioClass.getMethod("getIMSVoiceCallAvailability", int.class);
            avail.invoke(radio, 3);
            Log.i(TAG, "IExtRadio/slot1 getIMSVoiceCallAvailability requested (serial=3)");
            Method regAddr = radioClass.getMethod("getImsRegAddress", int.class);
            regAddr.invoke(radio, 4);
            Log.i(TAG, "IExtRadio/slot1 getImsRegAddress requested (serial=4)");
        } catch (Throwable error) {
            Log.e(TAG, "IExtRadio/slot1 enableIMS failed", error);
        }
    }
}
