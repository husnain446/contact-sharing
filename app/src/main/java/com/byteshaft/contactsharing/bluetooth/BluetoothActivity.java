package com.byteshaft.contactsharing.bluetooth;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.byteshaft.contactsharing.MainActivity;
import com.byteshaft.contactsharing.R;
import com.byteshaft.contactsharing.utils.AppGlobals;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class BluetoothActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener {

    public static final int REQUEST_ENABLE_BT = 1;
    private BluetoothAdapter mBluetoothAdapter;
    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 0;
    private ArrayList<DeviceData> bluetoothDeviceArrayList;
    private HashMap<String, String> bluetoothMacAddress;
    public ViewHolder holder;
    public ListView listView;
    private MenuItem refreshItem;
    private String dataToBeSent = "";
    private Switch discoverSwitch;
    private TextView currentState;
    private JSONObject jsonObject;
    private int isImageShare = 0;
    private String filePath = "";
    private String jsonPictureData = "";
    private final String TAG = BluetoothActivity.this.getClass().getSimpleName();
    public ProgressDialog progressDialog;
    private static BluetoothActivity sInstance;

    public static BluetoothActivity getInstance() {
        return sInstance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bluetooth_activity);
        sInstance = this;
        dataToBeSent = getIntent().getStringExtra(AppGlobals.DATA_TO_BE_SENT);
        Log.i("TAG", "" + dataToBeSent);
        if (dataToBeSent != null) {
            try {
                jsonObject = new JSONObject(dataToBeSent);
                if (jsonObject.getInt(AppGlobals.IS_IMAGE_SHARE) == 1) {
                    isImageShare = jsonObject.getInt(AppGlobals.IS_IMAGE_SHARE);
                    filePath = jsonObject.getString(AppGlobals.IMG_URI);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        listView = (ListView) findViewById(R.id.devicesList);
        currentState = (TextView) findViewById(R.id.current_state);
        discoverSwitch = (Switch) findViewById(R.id.discovery_switch);
        discoverSwitch.setOnCheckedChangeListener(this);
        bluetoothDeviceArrayList = new ArrayList<>();
        bluetoothMacAddress = new HashMap<>();
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                DeviceData deviceData = bluetoothDeviceArrayList.get(i);
                for (BluetoothDevice device : AppGlobals.adapter.getBondedDevices()) {
                    if (device.getAddress().contains(deviceData.getValue())) {
                        Log.v(TAG, "Starting client thread");
                        if (AppGlobals.clientThread != null) {
                            AppGlobals.clientThread.cancel();
                        }
                        AppGlobals.sBluetoothDevice = device;
                        AppGlobals.clientThread = new ClientThread(device, AppGlobals.clientHandler);
                        AppGlobals.clientThread.start();
                    }
                }
            }
        });
        if (checkDeviceDiscoverState()) {
            discoverSwitch.setChecked(true);
        } else {
            discoverSwitch.setChecked(false);
        }

        AppGlobals.clientHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MessageType.READY_FOR_DATA: {
                        if (!AppGlobals.sIncomingImage) {
                            Message msg = new Message();
                            String data = jsonObject.toString();
                            msg.obj = data.getBytes();
                            try {
                                if (jsonObject.getInt("is_image_share") == 1) {
                                    AppGlobals.sIncomingImage = true;
                                }
                                if (!jsonObject.getString(AppGlobals.KEY_LOGO).trim().isEmpty()) {
                                    AppGlobals.logoPath = jsonObject.getString(AppGlobals.KEY_LOGO);
                                    AppGlobals.sInComingLogo = true;
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            AppGlobals.clientThread.incomingHandler.sendMessage(msg);
                        } else if (AppGlobals.sInComingLogo) {
                            File file = new File(AppGlobals.logoPath);
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inSampleSize = 2;
                            Bitmap image = BitmapFactory.decodeFile(file.getAbsolutePath(), options);

                            ByteArrayOutputStream compressedImageStream = new ByteArrayOutputStream();
                            image.compress(Bitmap.CompressFormat.JPEG, AppGlobals.IMAGE_QUALITY, compressedImageStream);
                            byte[] compressedImage = compressedImageStream.toByteArray();
                            Log.v(TAG, "Compressed image size: " + compressedImage.length);

                            // Invoke client thread to send
                            Message imageCard = new Message();
                            imageCard.obj = compressedImage;
                            AppGlobals.clientThread.incomingHandler.sendMessage(imageCard);
                            AppGlobals.sInComingLogo = false;
                        }

                        else {
                            File file = new File(filePath.replaceAll("_", "/"));
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inSampleSize = 2;
                            Bitmap image = BitmapFactory.decodeFile(file.getAbsolutePath(), options);

                            ByteArrayOutputStream compressedImageStream = new ByteArrayOutputStream();
                            image.compress(Bitmap.CompressFormat.JPEG, AppGlobals.IMAGE_QUALITY, compressedImageStream);
                            byte[] compressedImage = compressedImageStream.toByteArray();
                            Log.v(TAG, "Compressed image size: " + compressedImage.length);

                            // Invoke client thread to send
                            Message imageCard = new Message();
                            imageCard.obj = compressedImage;
                            AppGlobals.clientThread.incomingHandler.sendMessage(imageCard);
                            AppGlobals.sIncomingImage = false;
                        }
                        break;
                    }

                    case MessageType.COULD_NOT_CONNECT: {
                        if (progressDialog != null) {
                            progressDialog.dismiss();
                            progressDialog = null;
                        }
                        Toast.makeText(BluetoothActivity.this, "Could not connect to the paired device", Toast.LENGTH_SHORT).show();
                        break;
                    }

                    case MessageType.SENDING_DATA: {
                        progressDialog = new ProgressDialog(BluetoothActivity.this);
                        progressDialog.setMessage("Sending ...");
                        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                        progressDialog.show();
                        break;
                    }

                    case MessageType.DATA_SENT_OK: {
                        if (progressDialog != null) {
                            progressDialog.dismiss();
                            progressDialog = null;
                        }
//                        Toast.makeText(BluetoothActivity.this, "Business card was sent successfully", Toast.LENGTH_SHORT).show();
                        if (AppGlobals.sIncomingImage || AppGlobals.sInComingLogo) {
                            AppGlobals.clientThread = new ClientThread(AppGlobals.sBluetoothDevice,
                                    AppGlobals.clientHandler);
                            AppGlobals.clientThread.start();
                        }
                        break;
                    }

                    case MessageType.DIGEST_DID_NOT_MATCH: {
                        Toast.makeText(BluetoothActivity.this, "Business card was sent, but didn't go through correctly", Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
            }
        };

        AppGlobals.serverHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MessageType.DATA_RECEIVED: {
                        if (progressDialog != null) {
                            progressDialog.dismiss();
                            progressDialog = null;
                        }
                        if (AppGlobals.sIncomingImage) {
                            showNotification();
                        }
                        break;
                    }

                    case MessageType.DIGEST_DID_NOT_MATCH: {
                        if (!AppGlobals.sIncomingImage) {
                            Toast.makeText(BluetoothActivity.this, "Business card was received, but didn't come through correctly", Toast.LENGTH_SHORT).show();
                        }
                        break;
                    }

                    case MessageType.DATA_PROGRESS_UPDATE: {
                        // some kind of update
                        AppGlobals.progressData = (ProgressData) message.obj;
                        double pctRemaining = 100 - (((double) AppGlobals.progressData.remainingSize / AppGlobals.progressData.totalSize) * 100);
                        if (progressDialog == null) {
                            progressDialog = new ProgressDialog(BluetoothActivity.this);
                            progressDialog.setMessage("Receiving Business card...");
                            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                            progressDialog.setProgress(0);
                            progressDialog.setMax(100);
                            progressDialog.show();
                        }
                        progressDialog.setProgress((int) Math.floor(pctRemaining));
                        break;
                    }

                    case MessageType.INVALID_HEADER: {
                        Toast.makeText(BluetoothActivity.this, "Business card was sent, but the header was formatted incorrectly", Toast.LENGTH_SHORT).show();
                        break;
                    }
                }
            }
        };

        if (AppGlobals.pairedDevices != null) {
            if (AppGlobals.serverThread == null) {
                Log.v(TAG, "Starting server thread.  Able to accept Business cards.");
                AppGlobals.serverThread = new ServerThread(AppGlobals.adapter, AppGlobals.serverHandler);
                AppGlobals.serverThread.start();
            }
        }

        if (AppGlobals.pairedDevices != null) {
            ArrayList<DeviceData> deviceDataList = new ArrayList<DeviceData>();
            for (BluetoothDevice device : AppGlobals.pairedDevices) {
                deviceDataList.add(new DeviceData(device.getName(), device.getAddress()));
            }
        } else {
            Toast.makeText(this, "Bluetooth is not enabled or supported on this device", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.refresh, menu);
        refreshItem = menu.findItem(R.id.action_refresh);
        refresh();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_refresh) {
            refresh();
            discoverDevices();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        AppGlobals.sIncomingImage = false;
        AppGlobals.sInComingLogo = false;
        AppGlobals.logoPath = "";
        if (mBluetoothAdapter.isEnabled()) {
            mBluetoothAdapter.disable();
        }
        finish();
    }

    public void refresh() {
     /* Attach a rotating ImageView to the refresh item as an ActionView */
        LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        ImageView iv = (ImageView) inflater.inflate(R.layout.refresh_image, null);
        Animation rotation = AnimationUtils.loadAnimation(this, R.anim.refresh_animation);
        rotation.setRepeatCount(Animation.INFINITE);
        iv.startAnimation(rotation);
        refreshItem.setActionView(iv);

        //TODO trigger loading
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        Log.i("TAG", "" + checkDeviceDiscoverState() + "  button" + compoundButton.isChecked());
        switch (compoundButton.getId()) {
            case R.id.discovery_switch:
                if (compoundButton.isChecked()) {
                    if (checkDeviceDiscoverState()) {
                        compoundButton.setChecked(true);
                    } else {
                        makeDiscoverAble();
                        compoundButton.setChecked(false);
                    }

                } else {
                    if (checkDeviceDiscoverState()) {
                        compoundButton.setChecked(true);
                    } else {
                        compoundButton.setChecked(false);
                    }

                }
        }
    }

    public void completeRefresh() {
        if (refreshItem.getActionView() != null) {
            refreshItem.getActionView().clearAnimation();
            refreshItem.setActionView(null);
        }
    }

    private boolean checkDeviceDiscoverState() {
        BluetoothAdapter bAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            // device is discoverable & connectable
            return true;
        } else {
            // device is not discoverable & connectable
            return false;
        }
    }

    private String saveImage(Bitmap finalBitmap, String name) {

        String internalFolder = Environment.getExternalStorageDirectory() +
                File.separator + "Android/data" + File.separator + AppGlobals.getContext().getPackageName();
        File myDir = new File(internalFolder);
        File file = new File (myDir, name);
        if (file.exists ()) file.delete ();
        try {
            FileOutputStream out = new FileOutputStream(file);
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return file.getAbsolutePath();
    }

    public void showNotification() {
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_cards)
                        .setContentTitle("New Card Received")
                        .setAutoCancel(true)
                        .setContentText("New Business Card Received. Click to open");
        mBuilder.getNotification().flags |= Notification.FLAG_AUTO_CANCEL;

        Intent resultIntent = new Intent(this, MainActivity.class);
        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        resultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );
        mBuilder.setContentIntent(resultPendingIntent);
// Sets an ID for the notification
        int mNotificationId = 2112;
// Gets an instance of the NotificationManager service
        NotificationManager mNotifyMgr =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
// Builds the notification and issues it.
        mNotifyMgr.notify(mNotificationId, mBuilder.build());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i("TAG", "Permission granted");
                    checkBluetoothAndEnable();
                } else {
                    Toast.makeText(getApplicationContext(), "Permission denied!"
                            , Toast.LENGTH_SHORT).show();
                    finish();
                }
                return;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        listView.setAdapter(null);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            checkBluetoothAndEnable();
        } else {
            if (ContextCompat.checkSelfPermission(BluetoothActivity.this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(BluetoothActivity.this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            } else {
                checkBluetoothAndEnable();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBluetoothAdapter != null) {
            if (mBluetoothAdapter.isEnabled()) {
                mBluetoothAdapter.disable();
            }
        }
        try {
            unregisterReceiver(mReceiver);
        } catch (IllegalArgumentException e) {

        }
    }

    private void checkBluetoothAndEnable() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Device does not support Bluetooth");
            return;
            // Device does not support Bluetooth
        }
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {

            }
            discoverDevices();
        }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_OK) {
                Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        discoverDevices();
                    }
                }, 3000);
            } else {
                finish();
            }
        }
    }

    private void discoverDevices() {
        mBluetoothAdapter.startDiscovery();
        Log.i(TAG, "Discover");
        bluetoothDeviceArrayList = new ArrayList<>();
        bluetoothMacAddress = new HashMap<>();
        listView.setAdapter(null);
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_UUID);
        registerReceiver(mReceiver, filter);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "receiver");
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Add the name and address to an array adapter to show in a ListView
                if (!mBluetoothAdapter.getAddress().equals(device.getAddress())) {
                    if (!bluetoothDeviceArrayList.contains(device.getName()) && device.getName() != null) {
                        bluetoothDeviceArrayList.add(new DeviceData(device.getName(), device.getAddress()));
                        bluetoothMacAddress.put(device.getName(), device.getAddress());
                        Log.i(TAG, device.getName() + "\n" + device.getAddress());
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                completeRefresh();
            }
            Adapter adapter = new Adapter(getApplicationContext(), R.layout.bluetooth_delegate,
                    bluetoothDeviceArrayList);
            listView.setAdapter(adapter);
        }
    };

    private void makeDiscoverAble() {
        Intent discoverableIntent = new
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivity(discoverableIntent);
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (checkDeviceDiscoverState()) {
                    discoverSwitch.setChecked(true);
                } else {
                    discoverSwitch.setChecked(false);
                }
            }
        }, 3000);

    }

    private void connectDevice(String macAddress, boolean secure) {
        // Get the device MAC address
        //macAddress
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(macAddress);
        // Attempt to connect to the device
        mBluetoothAdapter.cancelDiscovery();
//        mService.connect(device, secure);
    }

    class Adapter extends ArrayAdapter<String> {

        private ArrayList<DeviceData> list;
        private Context mContext;

        public Adapter(Context context, int resource, ArrayList<DeviceData> list) {
            super(context, resource);
            this.list = list;
            mContext = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(R.layout.bluetooth_delegate, parent, false);
                holder = new ViewHolder();
                holder.bluetoothName = (TextView) convertView.findViewById(R.id.textView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            holder.bluetoothName.setText(list.get(position).toString());
            return convertView;
        }

        @Override
        public int getCount() {
            return list.size();
        }
    }

    static class ViewHolder {
        public TextView bluetoothName;
    }

    class BitmapCreationTask extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... strings) {
            Log.i("TAG", filePath);
            Bitmap icon = BitmapFactory.decodeFile(filePath.replaceAll("_", "/"));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            icon.compress(Bitmap.CompressFormat.JPEG, 60, bytes);
            byte[] image = bytes.toByteArray();
            Log.i("TAG", String.valueOf(bytes));

            JSONObject toBeSentJson  = new JSONObject();
            try {
                toBeSentJson.put(AppGlobals.IS_IMAGE_SHARE, 1);
                toBeSentJson.put(AppGlobals.NAME, jsonObject.getString(AppGlobals.NAME));
                String encodedImage = Base64.encodeToString(image, Base64.DEFAULT);
                toBeSentJson.put(AppGlobals.IMAGE, encodedImage);
                Log.i("TAG", String.valueOf(toBeSentJson));
            } catch (JSONException e) {
                e.printStackTrace();
            }
            jsonPictureData = toBeSentJson.toString();
            return null;
        }
    }

    class DeviceData {
        public DeviceData(String spinnerText, String value) {
            this.spinnerText = spinnerText;
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public String toString() {
            return spinnerText;
        }

        String spinnerText;
        String value;
    }

}