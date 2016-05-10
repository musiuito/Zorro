package net.servusrobotics.kickstudio;

        import android.app.PendingIntent;
        import android.content.BroadcastReceiver;
        import android.content.Context;
        import android.content.Intent;
        import android.content.IntentFilter;
        import android.hardware.usb.UsbConstants;
        import android.hardware.usb.UsbDevice;
        import android.hardware.usb.UsbDeviceConnection;
        import android.hardware.usb.UsbEndpoint;
        import android.hardware.usb.UsbInterface;
        import android.hardware.usb.UsbManager;
        import android.os.Bundle;
        import android.support.v7.app.AppCompatActivity;
        import android.util.Log;
        import android.view.Menu;
        import android.view.MenuItem;
        import android.widget.EditText;
        import android.widget.TextView;
        import android.widget.Toast;
        import java.util.HashMap;
        import java.util.Iterator;


public class MainActivity extends AppCompatActivity {
    // this activity provides the opening screen for the application.
// It checks whether the Zorro base is active and ask for permission to use the USB. If so
// it opens the communication and displays the wheel counters and proximity sensors
// The activity defines 2 buttons in the Action bar to start other activities
    private static final String Tag = "KickStudio";
    private static final String ACTION_USB_PERMISSION = "net.servusrobotics.kick.USB_PERMISSION";
    IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
    PendingIntent mPermissionIntent;
    UsbManager mUsbManager;
    UsbDevice mDevice, tDevice = null;
    static boolean zorroFound = false;
    int zorroProdId = 8211;
    int zorroVendId = 4866;
    HashMap<String, UsbDevice> attachedUsbDevices;
    static UsbDeviceConnection mConnection;
    static UsbEndpoint mEndpointOut, mEndpointIn1, mEndpointIn2;
 //   Thread ReadCountersTask;
    //	Runnable ReadCountersTask(){return null;};  //experimenting
    byte[] mBuffer = new byte[20];
    boolean readCounters = false;

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                     if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)){
                         Log.d(Tag, "permission granted for device " + mDevice);
                         //call method to set up device communication
                         setDevice(mDevice);
                     }
                     else{
                        Log.d(Tag, "permission denied for device " + mDevice);
                    }
                }
            }
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(Tag, "entering onCreate");
        setContentView(R.layout.activity_main);
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        zorroFound = zorroIsAttached();
        if (zorroFound) {
            Toast.makeText(getBaseContext(), "Zorro attached", Toast.LENGTH_SHORT).show();
            mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
            registerReceiver(mUsbReceiver, filter);
            mUsbManager.requestPermission(mDevice, mPermissionIntent);
        }
        else {
            Toast.makeText(getBaseContext(), "Zorro not connected", Toast.LENGTH_SHORT).show();
            Toast.makeText(getBaseContext(), "Can only watch", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.vision_only:
                watchOnly();
                return true;
            case R.id.play:
                kickBall();
                return true;


            default:
                return super.onOptionsItemSelected(item);
        }
    }
    public void watchOnly() {
        // Only look at the scene. Can be done without being attached to the mobile base
        Intent intent = new Intent(this, WatchActivity.class);
        startActivity(intent);
    }

    public void kickBall() {
        // Play now
        Intent intent = new Intent(this, PlayActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(Tag, "entering onResume");
        if (zorroFound){   // make sure the device was found
            Log.d(Tag, "Zorro present");
            if(mUsbManager.hasPermission(mDevice)){
                Log.d(Tag, "you have permission");
                readCounters = true;
                new Thread(new ReadCountersTask()).start();
            }

        }
    }


    private boolean zorroIsAttached() {
//      returns true if Zorro mobility module attached
        boolean zorroAttached = false;
        attachedUsbDevices = mUsbManager.getDeviceList();
        if (!attachedUsbDevices.isEmpty()) {
            Iterator<UsbDevice> deviceIterator = attachedUsbDevices.values().iterator();
            while (deviceIterator.hasNext()) {
                tDevice = deviceIterator.next();
                if ((tDevice.getProductId() == zorroProdId) && (tDevice.getVendorId() == zorroVendId)) {
                    mDevice = tDevice;
                    Log.d(Tag,"tDevice: " + tDevice);
                    zorroAttached = true;
                    break;    /* in case there are more devices */

                }
            }

        }
        return zorroAttached;
    }

    private void setDevice(UsbDevice device) {
        int numEndp, i;

        Toast.makeText(getBaseContext(),"Setting device",
                Toast.LENGTH_SHORT).show();
// set interface
        UsbInterface intf = device.getInterface(0);
//		Log.d(Tag, "Interface:" + intf);
// get number of endpoints
        numEndp = intf.getEndpointCount();
//		Log.d(Tag, "Number of Endpoints:" + numEndp);
// indentify endpoints
        for(i=0; i< numEndp; i++){
            UsbEndpoint mEndpoint = intf.getEndpoint(i);
//			Log.d(Tag, "Endpoint:" + i + mEndpoint);
            if(mEndpoint.getType()== UsbConstants.USB_ENDPOINT_XFER_BULK){
                if(mEndpoint.getDirection()==UsbConstants.USB_DIR_OUT){
                    mEndpointOut = mEndpoint;
                    Log.d(Tag, "Out Endpoint :" + i + mEndpoint);
                }else if (mEndpointIn1==null){
                    mEndpointIn1 = mEndpoint;
                    Log.d(Tag, "In Endpoint 1:" + i + mEndpointIn1);
                }else {
                    mEndpointIn2 = mEndpoint;
                    Log.d(Tag, "In Endpoint 2:" + i + mEndpointIn2);
                }
            }
        }
// Connect to device
        UsbDeviceConnection connection = mUsbManager.openDevice(device);
        Log.d(Tag, "connection:"+ connection);
        if (connection != null && connection.claimInterface(intf, true)){
            mConnection = connection;
            Log.d(Tag, "mConnection:" + mConnection);
        }else{
            mConnection = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(Tag, "paused");
    }


    @Override
    protected void onStop() {
        super.onStop();
        Log.d(Tag, "stopped");
        if (zorroFound) {
            unregisterReceiver(mUsbReceiver);
            readCounters = false; // Stop the ReadCounterTask thread
        }
    }

    class ReadCountersTask implements Runnable {
        int transfer;
        int i=0;
        int accum =0;
        int mean = 0;
        int countRight=0;
        int countLeft=0;
        int irSensors[] = new int[8];

        @Override
        public void run() {
            while (readCounters){
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                transfer = mConnection.bulkTransfer(mEndpointIn1, mBuffer, mBuffer.length, 100);
                if(transfer>=0){
                    Log.d(Tag, "transfer:" + transfer);
                    countLeft = ((mBuffer[1] & 0xFF) | (mBuffer[0] << 8));
                    countRight = ((mBuffer[3] & 0xFF) | (mBuffer[2] << 8));
//				Log.d(Tag, "countLeft:" + countLeft);
//				for (i=0; i<=7; i=i++){
//					irSensors[i] = ((mBuffer[2*i+5] & 0xFF) | (mBuffer[2*i+4] << 8));
//				irSensors[1] = ((mBuffer[7] & 0xFF) | (mBuffer[6] << 8));
//					Log.d(Tag, "ir"+i+":" + irSensors[1]);
//					Log.d(Tag, "ir"+i+":" );
//				}
                    irSensors[0] = ((mBuffer[5] & 0xFF) | (mBuffer[4] << 8));
                    irSensors[1] = ((mBuffer[7] & 0xFF) | (mBuffer[6] << 8));
                    irSensors[2] = ((mBuffer[9] & 0xFF) | (mBuffer[8] << 8));
                    irSensors[3] = ((mBuffer[11] & 0xFF) | (mBuffer[10] << 8));
                    irSensors[4] = ((mBuffer[13] & 0xFF) | (mBuffer[12] << 8));
                    irSensors[5] = ((mBuffer[15] & 0xFF) | (mBuffer[14] << 8));
                    irSensors[6] = ((mBuffer[17] & 0xFF) | (mBuffer[16] << 8));
                    irSensors[7] = ((mBuffer[19] & 0xFF) | (mBuffer[18] << 8));
                    accum = accum + irSensors[0];
                    if(i<50){
                        i=i+1;
                        mean = accum/i;
                    }

                    runOnUiThread(new Runnable(){
                        @Override
                        public void run(){
                            EditText showLeft = (EditText)findViewById(R.id.displayLeftCounter);
                            showLeft.setText("Left:" + countLeft, TextView.BufferType.EDITABLE);
                            EditText showRight = (EditText)findViewById(R.id.displayRightCounter);
                            showRight.setText("Right:" + countRight, TextView.BufferType.EDITABLE);
                            EditText irLeft1 = (EditText)findViewById(R.id.displayLeftIr1);
                            irLeft1.setText("left 1:" + irSensors[4], TextView.BufferType.EDITABLE);
                            EditText irLeft2 = (EditText)findViewById(R.id.displayLeftIr2);
                            irLeft2.setText("left 2:" + irSensors[7], TextView.BufferType.EDITABLE);
                            EditText irLeft3 = (EditText)findViewById(R.id.displayLeftIr3);
                            irLeft3.setText("left 3:" + irSensors[6], TextView.BufferType.EDITABLE);
                            EditText irLeft4 = (EditText)findViewById(R.id.displayLeftIr4);
                            irLeft4.setText("left 4:" + irSensors[5], TextView.BufferType.EDITABLE);
//	right 1 changed to display average over first 50 readings
                            EditText irRight1 = (EditText)findViewById(R.id.displayRightIr1);
                            irRight1.setText("right 1:" + mean, TextView.BufferType.EDITABLE);
                            EditText irRight2 = (EditText)findViewById(R.id.displayRightIr2);
                            irRight2.setText("right 2:" + irSensors[1], TextView.BufferType.EDITABLE);
                            EditText irRight3 = (EditText)findViewById(R.id.displayRightIr3);
                            irRight3.setText("right 3:" + irSensors[2], TextView.BufferType.EDITABLE);
                            EditText irRight4 = (EditText)findViewById(R.id.displayRightIr4);
                            irRight4.setText("right 4:" + irSensors[3], TextView.BufferType.EDITABLE);
                        }
                    });
                }
            }
        }
    }

}
