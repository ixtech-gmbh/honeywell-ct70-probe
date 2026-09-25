package de.ixtech.ct70probe;

import android.os.Bundle;

/** Runs the test again after setup has finished (started via adb). */
public class TokenTestActivity extends MainActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            TokenTest.run(this, "AFTER SETUP");
        }
        super.onCreate(savedInstanceState);
    }
}
