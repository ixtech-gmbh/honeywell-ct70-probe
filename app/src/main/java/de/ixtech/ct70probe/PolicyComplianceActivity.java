package de.ixtech.ct70probe;

import android.os.Bundle;

/** Runs the test in the setup wizard's admin policy compliance step, right after the device owner is set. */
public class PolicyComplianceActivity extends MainActivity {

    @Override
    protected boolean showContinue() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            TokenTest.run(this, "DURING SETUP (admin policy compliance step)");
        }
        super.onCreate(savedInstanceState);
    }
}
