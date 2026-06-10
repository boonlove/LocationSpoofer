package com.suseoaa.locationspoofer.xposed;
import android.net.wifi.WifiInfo;
import java.lang.reflect.Method;
public class TestBuilder {
    public static void printMethods() {
        try {
            Class<?> builderClass = Class.forName("android.net.wifi.WifiInfo$Builder");
            for (Method m : builderClass.getDeclaredMethods()) {
                System.out.println("Builder method: " + m.getName());
            }
        } catch (Exception e) {}
    }
}
