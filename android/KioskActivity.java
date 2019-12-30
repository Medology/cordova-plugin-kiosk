package jk.cordova.plugin.kiosk;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import org.apache.cordova.*;
import android.util.Log;
import android.view.Window;
import android.view.View;
import android.view.WindowManager;
import android.view.KeyEvent;
import android.view.ViewGroup.LayoutParams;
import android.widget.*;
import java.lang.Integer;
import java.util.Collections;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class KioskActivity extends CordovaActivity {

    public static volatile boolean running = false;
    public static volatile Set<Integer> allowedKeys = Collections.EMPTY_SET;

    private StatusBarOverlay statusBarOverlay = null;

    @Override
    protected void onStart() {
        super.onStart();
        System.out.println("KioskActivity started");
        running = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        System.out.println("KioskActivity stopped");
        running = false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        super.init();

        if (running) {
            finish(); // prevent more instances of kiosk activity
        }

        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler(this));
        // if app restarted due to crash, add toast to notify users why the app restarted
        if (this.getIntent().getBooleanExtra("crash", false)) {
            Toast.makeText(this, "Raven is restarting due to a critical error.", Toast.LENGTH_LONG).show();
        }

        loadUrl(launchUrl);

        // https://github.com/apache/cordova-plugin-statusbar/blob/master/src/android/StatusBar.java
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // https://github.com/hkalina/cordova-plugin-kiosk/issues/14
        View decorView = getWindow().getDecorView();
        // Hide the status bar.
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        // Remember that you should never show the action bar if the
        // status bar is hidden, so hide that too if necessary.
        ActionBar actionBar = getActionBar();
        if (actionBar != null) actionBar.hide();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (statusBarOverlay != null) {
            statusBarOverlay.destroy(this);
            statusBarOverlay = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        System.out.println("onKeyDown event: keyCode = " + event.getKeyCode());
        return ! allowedKeys.contains(event.getKeyCode()); // prevent event from being propagated if not allowed
    }

    public class UncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

        private Activity activity;

        public UncaughtExceptionHandler(Activity a) {
            activity = a;
        }

        /**
        * Uncaught exceptions cause the app to crash, exposing customers to the Android system.
        * This is something we want to avoid at all costs, so this uncaught exception handler is used
        * to catch all uncaught exceptions and automatically restart the application.
        *
        * Implementation from https://medium.com/@ssaurel/how-to-auto-restart-an-android-application-after-a-crash-or-a-force-close-error-1a361677c0ce
        */
        @Override
        public void uncaughtException(Thread thread, Throwable ex) {
            Log.d("RAVEN", "******** UNCAUGHT EXCEPTION, APP CRASHED ********");
            if (ex != null) {
                ex.printStackTrace();
                if (ex.getMessage() != null) Log.d("RAVEN", ex.getMessage());
            }

            Intent intent = activity.getPackageManager().getLaunchIntentForPackage("raven.scanner.app");
            intent.putExtra("crash", true);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(activity.getApplicationContext(), 666, intent, PendingIntent.FLAG_ONE_SHOT);
            AlarmManager mgr = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() +  500, pendingIntent);
            running = false;
            activity.finish();
            System.exit(2);
        }
    }
}

