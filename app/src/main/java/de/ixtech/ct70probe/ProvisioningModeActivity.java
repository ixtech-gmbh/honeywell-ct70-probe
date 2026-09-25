package de.ixtech.ct70probe;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;

/** Answers the provisioning flow with "fully managed device". */
public class ProvisioningModeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent result = new Intent();
        result.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE);
        setResult(RESULT_OK, result);
        finish();
    }
}
