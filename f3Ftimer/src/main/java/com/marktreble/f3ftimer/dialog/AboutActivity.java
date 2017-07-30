/*
 * About Activity
 * Software Version/credits
 */
package com.marktreble.f3ftimer.dialog;
import com.marktreble.f3ftimer.R;

import android.os.Bundle;
import android.text.util.Linkify;
import android.widget.TextView;
import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;

public class AboutActivity extends Activity {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about);
	
		PackageInfo pInfo;
		String v = "";
		String b = "";
		try {
			pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			v = pInfo.versionName;
			b = String.format("%d", pInfo.versionCode);
		} catch (NameNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		TextView version = (TextView) findViewById(R.id.version);
		version.setText(String.format("%s %s (Build %s)", getString(R.string.version), v, b));

	}
	
}
