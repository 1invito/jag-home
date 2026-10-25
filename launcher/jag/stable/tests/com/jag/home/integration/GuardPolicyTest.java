package com.jag.home.integration;

public final class GuardPolicyTest {
    private static int count;
    private static void check(boolean value, String name) { count++; if (!value) throw new AssertionError(name); }
    public static void main(String[] args) {
        String home = "com.amazon.tv.launcher.ui.HomeActivity_vNext";
        GuardPolicy policy = new GuardPolicy();
        check(GuardPolicy.compatible(28, "Amazon"), "target compatible");
        check(!GuardPolicy.compatible(25, "Amazon") && !GuardPolicy.compatible(30, "Amazon"), "unknown Fire OS refused");
        check(!GuardPolicy.compatible(28, "Other"), "non-Amazon refused");
        check(GuardPolicy.oldGuardEnabled("reader/.Service:tv.dumbtv.home/.Guard"), "old abbreviated component detected");
        check(GuardPolicy.oldGuardEnabled("tv.dumbtv.home/tv.dumbtv.home.Guard"), "old full component detected");
        check(!GuardPolicy.oldGuardEnabled("tv.dumbtv.home/.GuardOther"), "similar names do not count");
        check(!policy.request(false, home, "com.amazon.tv.launcher", home, 0), "disarmed/competing Guard cannot launch");
        check(!policy.request(true, home, "com.android.systemui", home, 0), "long-HOME system panel ignored");
        check(!policy.request(true, home, "com.amazon.tv.launcher", "settings.Other", 0), "Amazon settings not HOME");
        check(!policy.request(true, home, "com.netflix.ninja", home, 0), "streaming app untouched");
        check(policy.request(true, home, "com.amazon.tv.launcher", home, 0), "verified HOME redirects");
        check(!policy.request(true, home, "com.amazon.tv.launcher", home, 20), "duplicate pending event ignored");
        policy.arrived();
        check(!policy.request(true, home, "com.amazon.tv.launcher", home, 100), "duplicate event after arrival throttled");
        policy.leftForApp();
        check(policy.request(true, home, "com.amazon.tv.launcher", home, 110), "a new HOME return from streaming is immediate");
        policy.arrived();
        check(policy.request(true, home, "com.amazon.tv.launcher", home, 1000), "later HOME press accepted");
        System.out.println("GuardPolicy: " + count + " assertions passed");
    }
}
