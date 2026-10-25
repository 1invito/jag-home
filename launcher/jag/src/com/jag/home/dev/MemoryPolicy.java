package com.jag.home.dev;

/** Fail closed: no public-API evidence proves a third-party process safe to kill. */
final class MemoryPolicy {
    private MemoryPolicy() {}

    static boolean canAutomaticallyStop(String packageName, int api, boolean visibleToCaller) {
        // Empty allowlist on every version, including when a process happens to be visible.
        return false;
    }

    static String explanation(int api) {
        return "Automatic cleanup is unavailable on this device (Android API " + api + "). "
            + "Public APIs cannot reliably identify other apps' playback or protected processes. "
            + "No apps were stopped. Your VPN, playback, launchers and system services are untouched. "
            + "You can review an app in system settings instead. Android manages available memory "
            + "and may restart background processes.";
    }
}
