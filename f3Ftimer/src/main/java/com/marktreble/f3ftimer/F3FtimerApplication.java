package com.marktreble.f3ftimer;

/**
 * Created by marktreble on 03/02/15.
 */
import android.app.Application;
import android.os.Handler;

import org.acra.*;
import org.acra.annotation.*;

@ReportsCrashes(formKey = "", // will not be used
        mailTo = "mark.treble@marktreble.co.uk",
        mode = ReportingInteractionMode.SILENT)
public class F3FtimerApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // The following line triggers the initialization of ACRA
        ACRA.init(this);
    }



}
