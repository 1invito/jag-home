package com.jag.home.integration;

/** Pure event gate: exact HOME class, explicit arming, no competing old Guard, no key interception. */
public final class GuardPolicy {
    private long lastRequest = -1000;
    private boolean pending;

    public static boolean compatible(int sdk, String manufacturer) {
        return sdk == 28 && manufacturer != null && manufacturer.startsWith("Amazon");
    }

    public static boolean oldGuardEnabled(String services) {
        if (services == null) return false;
        for (String component : services.split(":")) {
            if (component.equals("tv.dumbtv.home/.Guard") || component.equals("tv.dumbtv.home/tv.dumbtv.home.Guard")) return true;
        }
        return false;
    }

    public boolean request(boolean eligible, String expectedClass, String pkg, String cls, long now) {
        if (!eligible || !"com.amazon.tv.launcher".equals(pkg) || !expectedClass.equals(cls)
                || expectedClass.isEmpty() || pending || now - lastRequest < 750) return false;
        pending = true; lastRequest = now; return true;
    }

    public void arrived() { pending = false; }
    public void leftForApp() { pending = false; lastRequest = -1000; }
}
