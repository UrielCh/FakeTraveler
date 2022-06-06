package cl.coders.faketraveler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Handler;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

import static cl.coders.faketraveler.MainActivity.SourceChange.CHANGE_FROM_EDITTEXT;
import static cl.coders.faketraveler.MainActivity.SourceChange.CHANGE_FROM_MAP;
import static cl.coders.faketraveler.MainActivity.SourceChange.NONE;


public class MainActivity extends AppCompatActivity {

    static final String sharedPrefKey = "cl.coders.mockposition.sharedpreferences";
    static final int KEEP_GOING = 0;
    static private int SCHEDULE_REQUEST_CODE = 1;
    public static Intent serviceIntent;
    public static PendingIntent pendingIntent;
    public static AlarmManager alarmManager;
    static Button button0;
    static Button button1;
    static WebView webView;
    static EditText editTextLat;
    static EditText editTextLng;
    static EditText editTextAcc; // editText3
    static Context context;
    static SharedPreferences sharedPref;
    static SharedPreferences.Editor editor;
    static Double lat;
    static Double lng;
    static Float accuracy;
    static int timeInterval;
    static int howManyTimes;
    static long endTime;
    static int currentVersion;
    private static MockLocationProvider mockNetwork;
    private static MockLocationProvider mockGps;

    WebAppInterface webAppInterface;

    public enum SourceChange {
        NONE, CHANGE_FROM_EDITTEXT, CHANGE_FROM_MAP
    }

    static SourceChange srcChange = NONE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = getApplicationContext();
        webView = findViewById(R.id.webView0);
        webAppInterface = new WebAppInterface(this, this);
        alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        sharedPref = context.getSharedPreferences(sharedPrefKey, Context.MODE_PRIVATE);
        editor = sharedPref.edit();

        button0 = (Button) findViewById(R.id.button0);
        button1 = (Button) findViewById(R.id.button1);
        editTextLat = findViewById(R.id.editText0);
        editTextLng = findViewById(R.id.editText1);
        editTextAcc = findViewById(R.id.editText3);
        button0.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                applyLocation();
            }
        });

        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                Intent myIntent = new Intent(getBaseContext(), MoreActivity.class);
                startActivity(myIntent);
            }
        });

        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        webView.addJavascriptInterface(webAppInterface, "Android");
        webView.loadUrl("file:///android_asset/map.html");

        try {
            if (!isMockSettingsON(MainActivity.this)) {
                Toast.makeText(MainActivity.this, "Please define the app as Mock GPS provide", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            PackageInfo pInfo = this.getPackageManager().getPackageInfo(getPackageName(), 0);
            currentVersion = pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        checkSharedPrefs();

        howManyTimes = Integer.parseInt(sharedPref.getString("howManyTimes", "1"));
        timeInterval = Integer.parseInt(sharedPref.getString("timeInterval", "10"));

        try {
            lat = Double.parseDouble(sharedPref.getString("lat", ""));
            lng = Double.parseDouble(sharedPref.getString("lng", ""));
            accuracy = Float.parseFloat(sharedPref.getString("accuracy", ""));

            editTextLat.setText(lat.toString());
            editTextLng.setText(lng.toString());
            editTextAcc.setText(accuracy.toString());
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        editTextLat.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                if (!editTextLat.getText().toString().isEmpty() && !editTextLat.getText().toString().equals("-")) {
                    if (srcChange != CHANGE_FROM_MAP) {
                        lat = Double.parseDouble((editTextLat.getText().toString()));

                        if (lng == null)
                            return;

                        setLatLng(editTextLat.getText().toString(), lng.toString(), accuracy.toString(), CHANGE_FROM_EDITTEXT);
                    }
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
            }
        });

        editTextLng.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                if (!editTextLng.getText().toString().isEmpty() && !editTextLng.getText().toString().equals("-")) {
                    if (srcChange != CHANGE_FROM_MAP) {
                        lng = Double.parseDouble((editTextLng.getText().toString()));

                        if (lat == null)
                            return;

                        setLatLng(lat.toString(), editTextLng.getText().toString(), accuracy.toString(), CHANGE_FROM_EDITTEXT);
                    }
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        editTextAcc.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String accText = editTextAcc.getText().toString();
                if (!accText.isEmpty() && !accText.equals("-")) {
                    if (srcChange != CHANGE_FROM_MAP) { // TODO fix that mess
                        accuracy = Float.valueOf(accText);
                        if (lat == null)
                            return;
                        setLatLng(lat.toString(), editTextLng.getText().toString(), accText, CHANGE_FROM_EDITTEXT);
                    }
                }
            }
        });

        endTime = sharedPref.getLong("endTime", 0);

        if (pendingIntent != null && endTime > System.currentTimeMillis()) {
            changeButtonToStop();
        } else {
            endTime = 0;
            editor.putLong("endTime", 0);
            editor.commit();
        }
        /**
         * Start socket listener
         */
        updateConversationHandler = new Handler();
        this.serverThread = new Thread(new ServerThread());
        this.serverThread.start();
    }

    public static boolean isMockSettingsON(Context context) {
        // returns true if mock location enabled, false if not enabled.
        if (Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ALLOW_MOCK_LOCATION).equals("0"))
            return false;
        else
            return true;
    }

    /**
     * socket part
     */
    private ServerSocket serverSocket;
    Handler updateConversationHandler;
    Thread serverThread = null;
    public static final int SERVERPORT = 6000;

    class ServerThread implements Runnable {
        public void run() {
            Socket socket = null;
            try {
                serverSocket = new ServerSocket(SERVERPORT);
            } catch (IOException e) {
                e.printStackTrace();
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket = serverSocket.accept();
                    CommunicationThread commThread = new CommunicationThread(socket);
                    new Thread(commThread).start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class CommunicationThread implements Runnable {
        private Socket clientSocket;
        private BufferedReader input;
        public CommunicationThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
            try {
                this.input = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String read = input.readLine();
                    updateConversationHandler.post(new updateUIThread(read));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class updateUIThread implements Runnable {
        private String msg;

        public updateUIThread(String str) {
            this.msg = str;
        }
        @Override
        public void run() {
            try {
                if (msg != null) {
                    String[] separated = msg.split(" ");
                    //   if (!separated[0].isEmpty())
                    if (!separated[1].isEmpty())
                        editTextLat.setText(separated[0]);
                    if (!separated[2].isEmpty())
                        editTextLng.setText(separated[1]);
                    if (!separated[3].isEmpty())
                        editTextAcc.setText(separated[2]);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            applyLocation();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        toast(context.getResources().getString(R.string.ApplyMockBroadRec_Closed));
        stopMockingLocation();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isFinishing()) {
            toast(context.getResources().getString(R.string.ApplyMockBroadRec_Closed));
            stopMockingLocation();
        }
    }

    /**
     * Check and reinitialize shared preferences in case of problem.
     */
    static void checkSharedPrefs() {
        int version = sharedPref.getInt("version", 0);
        String lat = sharedPref.getString("lat", "0.00");
        String lng = sharedPref.getString("lng", "0.00");
        String howManyTimes = sharedPref.getString("howManyTimes", "10");
        String timeInterval = sharedPref.getString("timeInterval", "10");
        Long endTime = sharedPref.getLong("endTime", 0);

        if (version != currentVersion) {
            editor.putInt("version", currentVersion);
            editor.commit();
        }

        try {
            Double.parseDouble(lat);
            Double.parseDouble(lng);
            Double.parseDouble(howManyTimes);
            Double.parseDouble(timeInterval);
        } catch (NumberFormatException e) {
            editor.clear();
            editor.putString("lat", lat);
            editor.putString("lng", lng);
            editor.putInt("version", currentVersion);
            editor.putString("howManyTimes", "1");
            editor.putString("timeInterval", "10");
            editor.putLong("endTime", 0);
            editor.commit();
            e.printStackTrace();
        }

    }

    /**
     * Apply a mocked location, and start an alarm to keep doing it if howManyTimes is > 1
     * This method is called when "Apply" button is pressed.
     */
    protected static void applyLocation() {
        if (latIsEmpty() || lngIsEmpty() || accIsEmpty()) {
            toast(context.getResources().getString(R.string.MainActivity_NoLatLong));
            return;
        }

        lat = Double.parseDouble(editTextLat.getText().toString());
        lng = Double.parseDouble(editTextLng.getText().toString());
        accuracy = Float.valueOf(editTextAcc.getText().toString());
        toast(context.getResources().getString(R.string.MainActivity_MockApplied));

        endTime = System.currentTimeMillis() + (howManyTimes - 1) * timeInterval * 1000;
        editor.putLong("endTime", endTime);
        editor.commit();

        changeButtonToStop();

        try {
            mockNetwork = new MockLocationProvider(LocationManager.NETWORK_PROVIDER, context);
            mockGps = new MockLocationProvider(LocationManager.GPS_PROVIDER, context);
        } catch (SecurityException e) {
            e.printStackTrace();
            MainActivity.toast(context.getResources().getString(R.string.ApplyMockBroadRec_MockNotApplied));
            stopMockingLocation();
            return;
        }

        exec(lat, lng, accuracy);

        if (!hasEnded()) {
            toast(context.getResources().getString(R.string.MainActivity_MockLocRunning));
            setAlarm(timeInterval);
        } else {
            stopMockingLocation();
        }
    }

    /**
     * Set a mocked location.
     *
     * @param lat latitude
     * @param lng longitude
     */
    static void exec(double lat, double lng, float accu) {
        try {
            //MockLocationProvider mockNetwork = new MockLocationProvider(LocationManager.NETWORK_PROVIDER, context);
            mockNetwork.pushLocation(lat, lng, accu);
            //MockLocationProvider mockGps = new MockLocationProvider(LocationManager.GPS_PROVIDER, context);
            mockGps.pushLocation(lat, lng, accu);
        } catch (Exception e) {
            toast(context.getResources().getString(R.string.MainActivity_MockNotApplied));
            changeButtonToApply();
            e.printStackTrace();
            return;
        }
    }

    /**
     * Check if mocking location should be stopped
     *
     * @return true if it has ended
     */
    static boolean hasEnded() {
        if (howManyTimes == KEEP_GOING) {
            return false;
        } else if (System.currentTimeMillis() > endTime) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Sets the next alarm accordingly to <seconds>
     *
     * @param seconds number of seconds
     */
    static void setAlarm(int seconds) {
        serviceIntent = new Intent(context, ApplyMockBroadcastReceiver.class);
        pendingIntent = PendingIntent.getBroadcast(context, SCHEDULE_REQUEST_CODE, serviceIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        try {
            if (Build.VERSION.SDK_INT >= 19) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, System.currentTimeMillis() + seconds * 1000, pendingIntent);
                } else {
                    alarmManager.setExact(AlarmManager.RTC, System.currentTimeMillis() + timeInterval * 1000, pendingIntent);
                }
            } else {
                alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + timeInterval * 1000, pendingIntent);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Shows a toast
     */
    static void toast(String str) {
        Toast.makeText(context, str, Toast.LENGTH_LONG).show();
    }

    /**
     * Returns true editTextLat has no text
     */
    static boolean latIsEmpty() {
        return editTextLat.getText().toString().isEmpty();
    }

    /**
     * Returns true editTextLng has no text
     */
    static boolean lngIsEmpty() {
        return editTextLng.getText().toString().isEmpty();
    }

    /**
     * Returns true editTextAcc has no text
     */
    static boolean accIsEmpty() {
        return editTextAcc.getText().toString().isEmpty();
    }

    /**
     * Stops mocking the location.
     */
    protected static void stopMockingLocation() {
        changeButtonToApply();
        editor.putLong("endTime", System.currentTimeMillis() - 1);
        editor.commit();

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            toast(context.getResources().getString(R.string.MainActivity_MockStopped));
        }

        if (mockNetwork != null)
            mockNetwork.shutdown();
        if (mockGps != null)
            mockGps.shutdown();
    }

    /**
     * Changes the button to Apply, and its behavior.
     */
    static void changeButtonToApply() {
        button0.setText(context.getResources().getString(R.string.ActivityMain_Apply));
        button0.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                applyLocation();
            }

        });
    }

    /**
     * Changes the button to Stop, and its behavior.
     */
    static void changeButtonToStop() {
        button0.setText(context.getResources().getString(R.string.ActivityMain_Stop));
        button0.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                stopMockingLocation();
            }

        });
    }

    /**
     * Sets latitude and longitude
     *
     * @param mLat      latitude
     * @param mLng      longitude
     * @param mArc      accuracy
     * @param srcChange CHANGE_FROM_EDITTEXT or CHANGE_FROM_MAP, indicates from where comes the change
     */
    static void setLatLng(String mLat, String mLng, String mArc, SourceChange srcChange) {
        lat = Double.parseDouble(mLat);
        lng = Double.parseDouble(mLng);
        if (!mArc.isEmpty()) {
            accuracy = Float.valueOf(mArc);
        }

        if (srcChange == CHANGE_FROM_EDITTEXT) {
            webView.loadUrl("javascript:setOnMap(" + lat + "," + lng + ");");
        } else if (srcChange == CHANGE_FROM_MAP) {
            MainActivity.srcChange = CHANGE_FROM_MAP;
            editTextLat.setText(mLat);
            editTextLng.setText(mLng);
            MainActivity.srcChange = NONE;
        }

        editor.putString("lat", mLat);
        editor.putString("lng", mLng);
        editor.commit();
    }

    /**
     * returns latitude
     *
     * @return latitude
     */
    static String getLat() {
        return editTextLat.getText().toString();
    }

    /**
     * returns latitude
     *
     * @return latitude
     */
    static String getLng() {
        return editTextLng.getText().toString();
    }
}