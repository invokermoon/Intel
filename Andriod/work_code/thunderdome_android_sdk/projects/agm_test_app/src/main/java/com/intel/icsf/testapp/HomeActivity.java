package com.intel.icsf.testapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Button;

import com.intel.agm_test_app.BuildConfig;
import com.intel.agm_test_app.R;
import com.intel.icsf.testapp.utils.LogUtils;
import com.intel.icsf.testapp.utils.UIUtils;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class HomeActivity extends Activity {
    private static final String TAG = "";
    @Bind(R.id.find_devices) Button findDevices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        UIUtils.buttonEffect(findDevices);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                String versionName = BuildConfig.VERSION_NAME;
                String message = getString(R.string.app_description)
                        + "\n\n" + "Version: " + versionName;

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(message).setTitle(R.string.action_about);
                AlertDialog dialog = builder.create();
                dialog.show();
                break;
            default:
                LogUtils.LOGW(TAG, "Menu ID is unknown: " + item.getItemId());
        }
        return true;
    }
    @OnClick(R.id.find_devices)
    public void startScanActivity() {
        Intent intent = new Intent(HomeActivity.this, ScanActivity.class);
        startActivity(intent);
    }

}
