package com.example

/** Switches for features that are built but not shown yet. */
object FeatureFlags {
    /**
     * SOS (one-tap SOS, power-button SOS, 1 km Bluetooth safety network, SOS alerts) is hidden
     * for now: no tab, no buttons, no background service, no SOS notifications. The code stays;
     * set this to true to bring all of it back.
     */
    const val SOS_ENABLED = false
}
