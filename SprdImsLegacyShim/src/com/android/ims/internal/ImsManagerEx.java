package com.android.ims.internal;

/**
 * Declaration-only Android 8 Spreadtrum helper. Static methods return safe
 * defaults during host-only class linkage verification.
 */
public final class ImsManagerEx {
    private ImsManagerEx() {}

    public static IImsServiceEx getIImsServiceEx() { return null; }
    public static IImsUtEx getIImsUtEx() { return null; }
    public static boolean isDualLteModem() { return false; }
    public static boolean isDualVoLTEActive() { return false; }
    public static boolean isDualVoLTERegistered() { return false; }
    public static boolean isEnhancedDualVolteOn() { return false; }
    public static boolean isImsRegisteredForPhone(int phoneId) { return false; }
    public static boolean isReadyForDualActiveCall() { return false; }
    public static boolean isVoLTERegisteredForPhone(int phoneId) { return false; }
    public static void notifyVideoCapabilityChange() {}
}
