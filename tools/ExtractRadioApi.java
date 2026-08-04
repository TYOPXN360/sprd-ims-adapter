// Reflectively list the abstract methods of android.hardware.radio.V1_0
// IRadioResponse / IRadioIndication so gen_std_radio_callbacks.py can emit
// no-op overrides. Run with the Android 11 framework on the classpath.
//
//   javac -cp android-all-11.jar ExtractRadioApi.java
//   java -cp .:android-all-11.jar ExtractRadioApi > radio-api.txt
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class ExtractRadioApi {
    private static final String[] SKIP = {
        "asBinder", "interfaceChain", "interfaceDescriptor", "getDebugInfo",
        "getHashChain", "linkToDeath", "ping", "setHALInstrumentation",
        "unlinkToDeath", "notifySyspropsChanged", "debug"};

    private static boolean skip(String n) {
        for (String s : SKIP) {
            if (s.equals(n)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] a) throws Exception {
        for (String cn : new String[]{"android.hardware.radio.V1_0.IRadioResponse",
                "android.hardware.radio.V1_0.IRadioIndication"}) {
            Class<?> c = Class.forName(cn);
            System.out.println("CLASS " + cn);
            for (Method m : c.getDeclaredMethods()) {
                if (!Modifier.isAbstract(m.getModifiers()) || skip(m.getName())) {
                    continue;
                }
                StringBuilder sb = new StringBuilder(m.getName()).append('(');
                Class<?>[] pt = m.getParameterTypes();
                for (int i = 0; i < pt.length; i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(pt[i].getName().replace('$', '.'));
                }
                sb.append(')').append(m.getReturnType().getName());
                System.out.println(sb);
            }
        }
    }
}
