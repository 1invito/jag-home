package com.jag.home.dev;

/** Fail closed: no public-API evidence proves a third-party process safe to kill. */
final class MemoryPolicy {
    private MemoryPolicy() {}

    static boolean canAutomaticallyStop(String packageName, int api, boolean visibleToCaller) {
        // Empty allowlist on every version, including when a process happens to be visible.
        return false;
    }

    static String explanation(int api) {
        return "Android manages background processes and may restart them when needed. "
            + "No apps were stopped. JAG never kills applications, clears their data or changes their background activity. "
            + "Your VPN, playback, launchers and system services are untouched. "
            + "You can review an app in system settings instead; leave active playback and VPN settings unchanged.";
    }
}
