package com.suseoaa.locationspoofer.xposed;
import android.net.wifi.WifiInfo;
import java.lang.reflect.Field;
public class TestField {
    public static void printFields() {
        for (Field f : WifiInfo.class.getDeclaredFields()) {
            System.out.println("WifiInfo field: " + f.getName());
        }
    }
}
