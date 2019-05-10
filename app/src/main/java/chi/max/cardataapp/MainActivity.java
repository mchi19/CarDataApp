package chi.max.cardataapp;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;


public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // Variables for Hardware Sensors
    SensorManager sensorManager;
    Sensor mAccelerometer, mMagno; //, mTemp;
    private static boolean mIsSensorUpdateEnabled = false;
    private static String driveDataInstance = null;

    // Variables to determine Cardinal Direction
    float[] mGravity;
    float[] mGeomagnetic;
    double azimut;

    // Other Variables
    LocationManager locationManager;
    LocationListener locationListener;
    Location location;
    double latitude;
    double longitude;
    Geocoder geocoder;
    List<Address> addresses;

    // TextViews
    TextView speedView;
    TextView dirView;

    Handler mHandler = new Handler();
    Runnable run = new Runnable() {
        @Override
        public void run() {
            if (mIsSensorUpdateEnabled) {
                if (driveDataInstance != null) {
                    writeToFile(driveDataInstance);
                }
                mHandler.postDelayed(run,5000);
            }
        }
    };

    // Write log files
    public void writeToFile(String context) {
        File fileName = new File(Environment.getDataDirectory() + "/data/chi.max.cardataapp/files/dataLogs.csv");
        if (!fileName.exists()) {
            try {
                fileName.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            FileOutputStream fOut = new FileOutputStream(fileName, true);
            OutputStreamWriter outputWriter = new OutputStreamWriter(fOut);
            //outputWriter.write(context);
            outputWriter.append(context);
            outputWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Function upon hitting 'Start' button
    public void getStart(View view) throws InterruptedException {
        // clear file if it already exists
        File directory = new File(Environment.getDataDirectory() + "/data/chi.max.cardataapp/files/");
        if (!directory.exists()) {
            directory.mkdir();
        }
        File newFile = new File(directory, "dataLogs.csv");
        // if there is already a file made, delete it and start a new one
        if (newFile.exists()) {
            newFile.delete();
        } else {
            try {
                newFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        getData();
        // Execute Sensors data to log to csv file
        mIsSensorUpdateEnabled = true;

        TimeUnit.MILLISECONDS.sleep(5000);
        Toast.makeText(getBaseContext(), "New Trip Started", Toast.LENGTH_SHORT).show();

        // Accelerometer Sensor
        mAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        // Register sensor Listener
        if (mAccelerometer != null) {
            sensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            Log.d("getStart", "Register Linear Accelerometer Listener");

        } else {
            Log.d("getStart", "Linear Accelerometer is not supported");
        }

        // Magnetic Field Sensor
        mMagno = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        // Register sensor
        if (mMagno != null) {
            sensorManager.registerListener(this, mMagno, SensorManager.SENSOR_DELAY_NORMAL);
            Log.d("getStart", "Register Magnetic Field Sensor Listener");
        } else {
            Log.d("getStart", "Magnetic Field Sensor is not supported");
        }

        mHandler.post(run);
    }

    public void getEnd(View view) {
        mIsSensorUpdateEnabled = false;
        getData();
        Toast.makeText(getBaseContext(), "Trip Ended, file saved successfully!", Toast.LENGTH_SHORT).show();
    }

    public void getData() {
        // Get Longitude and Latitude
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        String lat = String.format("%.3f", latitude);
        String lng = String.format("%.3f", longitude);
        Log.i("Lng/Lat", "" + lat + ", " + lng);

        // Get Address
        geocoder = new Geocoder(this, Locale.getDefault());
        try {
            addresses = geocoder.getFromLocation(latitude, longitude, 1);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        String address = addresses.get(0).getAddressLine(0);
        Log.i("Address", address);

        // Get Date and Time
        SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy,HH:mm:ss");
        Date date = new Date();
        String datetime = formatter.format(date);
        Log.d("Date, Time", "" + datetime);

        // Get Weather Conditions and Temperature
        String weatherURL = "https://api.openweathermap.org/data/2.5/weather?lat=" + latitude + "&lon=" + longitude + "&appid=41e6b77b5619382893b2d2f06e326e1c";
        Log.i("weatherURL", weatherURL);

        // Get weather info
        DownloadTask task = new DownloadTask();
        task.execute(weatherURL);

        //String text = lng + ", " + lat + ", \'" + address + "\', " + formatter.format(date) + "\n";
        String text = lng + "," + lat + ",\'" + address + "\'," + formatter.format(date) + ",";
        Log.i("writeToFile", text);
        writeToFile(text);
    }

    // Get JSON weather data from openweaterhmap api and parse for specific data
    public class DownloadTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... urls) {
            String result = "";
            URL url;
            HttpURLConnection urlConnection = null;
            try {
                url = new URL(urls[0]);
                urlConnection = (HttpURLConnection) url.openConnection();
                InputStream in = urlConnection.getInputStream();
                InputStreamReader reader = new InputStreamReader(in);
                int data = reader.read();

                while (data != -1) {
                    char current = (char) data;
                    result += current;
                    data = reader.read();
                }
                return result;

            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            try {
                JSONObject jsonObject = new JSONObject(s);
                JSONObject mainInfo = (JSONObject) jsonObject.get("main");
                double temperature = mainInfo.getDouble("temp");
                String weatherInfo = jsonObject.getString("weather");
                //Log.i("Weather content", weatherInfo);
                JSONArray arr = new JSONArray(weatherInfo);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject jsonPart = arr.getJSONObject(i);
                    Log.i("writeToFile", jsonPart.getString("main"));
                    writeToFile(jsonPart.getString("main"));
                    writeToFile(",");
                    // write to file
                }
                double OutsideTemperature = (temperature - 273) * 1.8 + 32;
                Log.i("writeToFile", "" + Math.round(OutsideTemperature) + "°F");
                // write to file
                writeToFile(Math.round(OutsideTemperature) + "°F,");
                writeToFile("\n");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            }
        }
    }

    // have this start after 'Start' button is pressed and end after 'End' button is pressed
    @Override
    public void onSensorChanged(SensorEvent event) {
        Log.d("isSensorOn",""+mIsSensorUpdateEnabled);
        if (!mIsSensorUpdateEnabled){
            sensorManager.unregisterListener(this,mAccelerometer);
            sensorManager.unregisterListener(this,mMagno);
            Log.d("StopSensors", "Sensors are now disabled");
            mIsSensorUpdateEnabled = false;
            return;
        }
        // get Long and Lat and Speed
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        double velocity = location.getSpeed() * 2.2369;

        String lat = String.format("%.3f", latitude);
        String lng = String.format("%.3f", longitude);
        String speed = String.format("%.3f", velocity);
        String data = null;
        String accel = null;
        String dir = null;

        // Pixel 2 XL does not have built in temperature sensor
        /*
        if (event.sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            double insideTemperature = event.values[0];
            Log.i("InsideTemperature", insideTemperature+ "°C");
        }
        */
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            mGravity = event.values;
/*
            double acceleration = event.values[1] / 0.447;
            accel = String.format("%.3f", acceleration);
            //data += accel + " mph/s,";

            Log.i("Acceleration", accel + " mph/s");
*/
        }
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mGeomagnetic = event.values;
        }
        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];

            if (SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic)) {
                double acceleration = Math.abs(mGravity[1]/0.447); // y axis when the phone is lying flat
                accel = String.format("%.3f", acceleration);
                // orientation contains azimut, pitch and roll
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                azimut = orientation[0];
                float cDegree = (float) (Math.toDegrees(azimut)+360)%360;
                double degree = Math.round(cDegree * 100.0) / 100.0;
                if (degree > 337.5 || degree <= 22.5) {
                    Log.i("Cardinal Direction", degree + "°N");
                    dir = "N,";
                    dirView.setText("N");
                }
                else if (degree > 22.5 && degree <= 67.5) {
                    Log.i("Cardinal Direction", degree + "°NE");
                    dir = "NE,";
                    dirView.setText("NE");
                }
                else if (degree > 67.5 && degree <= 112.5) {
                    Log.i("Cardinal Direction", degree + "°E");
                    dir = "E,";
                    dirView.setText("E");
                }
                else if (degree > 112.5 && degree <= 157.5) {
                    Log.i("Cardinal Direction", degree + "°SE");
                    dir = "SE,";
                    dirView.setText("SE");
                }
                else if (degree > 157.5 && degree <= 202.5) {
                    Log.i("Cardinal Direction", degree + "°S");
                    dir = "S,";
                    dirView.setText("S");
                }
                else if (degree > 202.5 && degree <= 247.5) {
                    Log.i("Cardinal Direction", degree + "°SW");
                    dir = "SW,";
                    dirView.setText("SW");
                }
                else if (degree > 247.5 && degree <= 292.5) {
                    Log.i("Cardinal Direction", degree + "°W");
                    dir = "W,";
                    dirView.setText("W");
                }
                else { //(degree > 292.5 && degree <= 337.5)
                    Log.i("Cardinal Direction", degree + "°NW");
                    dir = "NW,";
                    dirView.setText("NW");
                }
            }
        }
        if (accel != null && dir != null) {
            data = lng + "," + lat + "," + speed + " mph," + accel + " mph/s," + dir + "\n";
            driveDataInstance = data;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // not in use
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Assign the textView Fields
        speedView = (TextView) findViewById(R.id.textView1);
        dirView = (TextView) findViewById(R.id.textView2);

        // Create sensor Manager
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);

        /*
         * Location Functions
         */
        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();
                double velocity = location.getSpeed() * 2.2369;

                Log.i("GPS Coordinates", "lat, lon: " + latitude +", " + longitude);
                Log.i("Speed", "" + velocity + "mph");
                String speed = String.format("%.2f", velocity);
                speedView.setText(speed + " mph");
            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {
                // not in use
            }

            @Override
            public void onProviderEnabled(String s) {
                // not in use
            }

            @Override
            public void onProviderDisabled(String s) {
                // not in use
            }
        };

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},1);
        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }
    }
}
