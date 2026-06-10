package com.suseoaa.locationspoofer.xposed;
import android.net.wifi.WifiInfo;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class FieldTester {
    public static List<String> getWifiInfoFields() {
        List<String> list = new ArrayList<>();
        for (Field f : WifiInfo.class.getDeclaredFields()) {
            list.add(f.getName());
        }
        return list;
    }
}
