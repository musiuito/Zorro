package net.servusrobotics.kickstudio;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Environment;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.support.v7.app.AppCompatActivity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static net.servusrobotics.kickstudio.MainActivity.*;

    public class PlayActivity extends AppCompatActivity implements SensorEventListener {
	private static final String Tag = "KickPlay";
	byte[] sensorRead = new byte[20];
	int torque = 0;
	int torqueDuration = 0;
	int transfer = 0;
	int countLeft = 0;
	int countRight = 0;
	EditText setTorque;
    boolean exit =false;
	boolean canWrite2File = false;
    File experimentDir, myExp;
	String timeStampFn;
	FileWriter saveToFile = null;//if not declared gives null pointer exeption on first write
	boolean fileExists;
	int sampleTime = 50;
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	public float[] accel = new float[3];
	float aMod;
	double aModSq;
	Long accelTime;



	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        Log.d(Tag, "entering onCreate");
		setContentView(R.layout.activity_play);
		timeStampFn = makeTimeStamp();
		Log.d(Tag, "timeStamp:"+ timeStampFn);
		if(isExternalStorageWritable()){
			experimentDir = getDataStorageDir("Experiments");
			Log.d(Tag, "directory:" + experimentDir);
		}
	}
	


	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.play, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// This ID represents the Home or Up button. In the case of this
			// activity, the Up button is shown. Use NavUtils to allow users
			// to navigate up one level in the application structure. For
			// more details, see the Navigation pattern on Android Design:
			//
			// http://developer.android.com/design/patterns/navigation.html#up-vs-back
			//
			NavUtils.navigateUpFromSameTask(this);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

		@Override
		public void onSensorChanged(SensorEvent event) {
//        super.onSensorChanged(event);

			accel[0] = event.values[0];
			accel[1] = event.values[1];
			accel[2] = event.values[2];
			accelTime = event.timestamp;

		}

		@Override
		public void onAccuracyChanged(Sensor sensor,int accuracy) {
//        super.onAccuracyChanged(sensor, accuracy);
			int temp =0;
		}


		@Override
	protected void onResume() {
		super.onResume();
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_UI);


		myExp = new File(experimentDir,"Ex"+timeStampFn);
		Log.d(Tag, myExp + " is a file:" + myExp.isFile());
		Log.d(Tag, myExp + " is a directory:" + myExp.isDirectory());
		Log.d(Tag, myExp + " exists:" + myExp.exists());
//		try {
//			fileExists = myExp.createNewFile();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//		Log.d(Tag, myExp + "file created:" + fileExists);
		Log.d(Tag, myExp + " is a file:" + myExp.isFile());
		Log.d(Tag, myExp + " can write to:" + myExp.canWrite());
		try {
			saveToFile = new FileWriter(myExp, true);
		} catch (IOException e) {
			e.printStackTrace();
		}
		Log.d(Tag, "FileWriter:" + saveToFile);

		//  Zorro mobile base has to be connected
		if(zorroFound) {

			EditText setTorque = (EditText) findViewById(R.id.getTorque);
			setTorque.setText("" + torque, TextView.BufferType.EDITABLE);
// Get the duration when the NEXT key is touched to finish editing the torque  field
			setTorque.setOnEditorActionListener(new OnEditorActionListener() {
				@Override
				public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
					boolean handled = false;
					if (actionId == EditorInfo.IME_ACTION_NEXT) {
						torque = Integer.parseInt(v.getText().toString());
						handled = true;
					}
					return handled;
				}

			});
			EditText setTorqueTime = (EditText) findViewById(R.id.getTorqueDuration);
            setTorqueTime.setText("" + torqueDuration, TextView.BufferType.EDITABLE);
			// Do the dragrun() when the DONE key is touched to finish editing the torque duration field
            setTorqueTime.setOnEditorActionListener(new OnEditorActionListener() {
				@Override
				public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
					boolean handled = false;
					if (actionId == EditorInfo.IME_ACTION_DONE) {
						torqueDuration = Integer.parseInt(v.getText().toString());
						handled = true;
					}
					Log.d(Tag, "handled:" + handled);
					Log.d(Tag, "duration:" + torqueDuration);
					dragrun();
					return handled;
				}


			});

        }
	}
	
   	public void dragrun(){
		// Applies a fixed torque for the torqueDuration (in miliseconds) and reads the counters
		// until the robot stops. displays the initial values of the counters and the clock and the  difference with
		// the final values.
   		int transfer = 0;
   		int startCountLeft = 0;
		int startCountRight =0;
		int prevCountLeft = 0; 
		int prevCountRight = 0; 
		int distLeft, distRight;
		long startTime = 0; 
		long time;
		long duration; 
		int leftTorque = 0;
		int rightTorque = 0;
		byte[] outBuffer = new  byte[7];
		boolean stopped = false;
		String dataString;
		
    	leftTorque = rightTorque = torque;
			Log.d(Tag, "start transfer:"+transfer);
    	// Read the sensors to extract the initial wheel counter values
		transfer = mConnection.bulkTransfer(mEndpointIn1,sensorRead,sensorRead.length,100);
		if(transfer>=0){
			startCountLeft = ((sensorRead[1] & 0xFF) | (sensorRead[0] << 8));
			startCountRight = ((sensorRead[3] & 0xFF) | (sensorRead[2] << 8));
			startTime = SystemClock.elapsedRealtime();
			// Display counters and start time
			EditText showLeft = (EditText)findViewById(R.id.displayLeftCounter);
			showLeft.setText("Start left:" + startCountLeft, TextView.BufferType.EDITABLE);
			EditText showRight = (EditText)findViewById(R.id.displayRightCounter);
			showRight.setText("Start right:" + startCountRight, TextView.BufferType.EDITABLE);
			EditText showStartTime = (EditText)findViewById(R.id.displayStartTime);
			showStartTime.setText("Start Time:" + startTime, TextView.BufferType.EDITABLE);
		}
		// now set the motors to run at given torque for given time
		outBuffer[0] = (byte)0xAA; //protocol byte
		outBuffer[1] = 0x03;       //motor direction byte: set both motors forward
		// for negative torques change direction bits 
		if(leftTorque < 0){
			outBuffer[1] = (byte)(outBuffer[1]|0x02); // set bit 2 to zero
			leftTorque = -leftTorque;
		}
		if(rightTorque < 0){
			outBuffer[1] = (byte)(outBuffer[1]|0x01); // set  bit 1 to zero
			rightTorque = - rightTorque;
		}
		outBuffer[2] = (byte) (leftTorque >> 8);  // high byte
		outBuffer[3] = (byte) (leftTorque & 0xFF); // low byte
		outBuffer[4] = (byte) (rightTorque >> 8);  // high byte
		outBuffer[5] = (byte) (rightTorque & 0xFF); // low byte
		outBuffer[6] = (byte) (torqueDuration & 0xFF); //low byte => max duration is 255. Ignored by firmware.
		transfer = mConnection.bulkTransfer(mEndpointOut,outBuffer,outBuffer.length,0);
		Log.d(Tag, "out transfer:"+transfer);
		prevCountLeft = startCountLeft;
		prevCountRight = startCountRight;
		// here write startTime, prevCountLeft, prevCountRight to file
		dataString = Long.toString(startTime)+" "+ Integer.toString(prevCountLeft)+" "+Integer.toString(prevCountRight);
		dataString = dataString +" "+ Float.toString(accel[2])+" "+ Long.toString(accelTime) + "\n";
		Log.d(Tag, dataString);
		try {
			saveToFile.write(dataString);
		} catch (IOException e) {
			e.printStackTrace();
		}
// Read counters every 50ms until stopped		
		while (!stopped){
			try {
				Thread.sleep(sampleTime);
				} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				}
//	Stop powering motors when time limit reached
			time = SystemClock.elapsedRealtime();
			if (time >= (startTime + torqueDuration)){
				motorsStop();
			}
			transfer = mConnection.bulkTransfer(mEndpointIn1,sensorRead,sensorRead.length,100);
			Log.d(Tag, "loop transfer:"+transfer);
			if(transfer>=0){
				countLeft = ((sensorRead[1] & 0xFF) | (sensorRead[0] << 8));
				countRight = ((sensorRead[3] & 0xFF) | (sensorRead[2] << 8));		
				if((countLeft == prevCountLeft) && (countRight == prevCountRight)){
					stopped = true;
				}
			}

			prevCountLeft = countLeft;
			prevCountRight = countRight;
//			aModSq = (double) (accel[0]*accel[0]+accel[1]*accel[1]+accel[2]*accel[2]);
//			aMod =  (float) Math.sqrt(aModSq);
			Log.d(Tag, "accel[2]: "+accel[2]);
			// here write time, prevCountLeft, prevCountRight to file
			dataString = Long.toString(time)+" "+ Integer.toString(prevCountLeft)+" "+Integer.toString(prevCountRight);
			dataString = dataString +" "+ Float.toString(accel[2])+" "+ Long.toString(accelTime) + "\n";
			Log.d(Tag, dataString);
			try {
				saveToFile.write(dataString);
			} catch (IOException e) {
				e.printStackTrace();
			}

			Log.d(Tag, "stopped:"+stopped);
		}
		// calculate and display distance moved and time taken
		time = SystemClock.elapsedRealtime();
		distLeft = countLeft - startCountLeft;
		distRight = countRight - startCountRight;
		duration = time - startTime;
		EditText showDistLeft = (EditText)findViewById(R.id.displayDistLeft);
		showDistLeft.setText("distance left:" + distLeft, TextView.BufferType.EDITABLE);
		EditText showDistRight = (EditText)findViewById(R.id.displayDistRight);
		showDistRight.setText("distance right:" + distRight, TextView.BufferType.EDITABLE);
		EditText showDuration = (EditText)findViewById(R.id.displayDuration);
		showDuration.setText("Duration:" + duration, TextView.BufferType.EDITABLE);
//		myCart.setTorque(20,20);
//			}
   	}	
		
 // stop the motors
 	public void motorsStop(){
 		byte[] outBuffer = new  byte[7];
 		
 		outBuffer[0] = (byte)0xAA;  //protocol byte
 		outBuffer[1] = 0x03;        //motor direction byte: set both motors forward
 		outBuffer[2] = 0; 		    // high byte
 		outBuffer[3] = 0; 		    // low byte
 		outBuffer[4] = 0;  			// high byte
 		outBuffer[5] = 0;			// low byte
 		outBuffer[6] = 0;           // duration ignored, PWM remains on
 		transfer = mConnection.bulkTransfer(mEndpointOut,outBuffer,outBuffer.length,0);
 	}

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(Tag, "stopped");
        exit = true;
    }

	@Override
	protected void onPause() {
		super.onPause();
		mSensorManager.unregisterListener(this);
		if (saveToFile != null) {
			try {
				saveToFile.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			;
			exit = true;
		}
	}

	String  makeTimeStamp(){
		//generates a string for use in a time stamp filename
		Date myTime = new Date();
		//use DataFormat to get a suitable date-time string
		DateFormat timeStamp =  DateFormat.getDateTimeInstance();
		String timeString = timeStamp.format(myTime);
		if (timeString.length()==19){      // in case DAY is only on digit make it 2 digits
			timeString = "0"+timeString;
		}
		Log.d(Tag, "timeString:"+ timeString);
		//extract date/time elements and reassemble
		String sy,sm,sd,sh,smn,ss;
		sy = timeString.substring(7,11);
		sm = timeString.substring(3,6);
		sd = timeString.substring(0,2);
		sh = timeString.substring(12,14);
		smn = timeString.substring(15,17);
		ss =  timeString.substring(18,20);
		return sy + sm + sd + "-" + sh + smn + ss;
	}


    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
            }
        return false;
        }

    public File getDataStorageDir(String dataName) {
     // Get the directory for experiment data
        File file = new File(Environment.getExternalStoragePublicDirectory(
				Environment.DIRECTORY_DOCUMENTS), dataName);
		if (!file.mkdirs()) {
			Log.d(Tag, "Directory " + dataName +" already exists");
		}
		return file;
	}




        class Avoiding implements Runnable {
		private static final String Tag = "KickAvoid";
		int transfer;
		int i=0;
		int accum =0;
		int mean = 0;
    	int countRight=0;
		int countLeft=0;
		byte[] sensorRead = new byte[20];
		int irSensors[] = new int[8];
		
	    @Override
		public void run() {
	    	boolean move = true;
	    	
	    	while (move){
			try {
				Thread.sleep(sampleTime);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			transfer = mConnection.bulkTransfer(mEndpointIn1, sensorRead, sensorRead.length, 100);
			if(transfer>=0){
				Log.d(Tag, "transfer:" + transfer);
				countLeft = ((sensorRead[1] & 0xFF) | (sensorRead[0] << 8));
				countRight = ((sensorRead[3] & 0xFF) | (sensorRead[2] << 8));
//				Log.d(Tag, "countLeft:" + countLeft);	
//				for (i=0; i<=7; i=i++){
//					irSensors[i] = ((sensorRead[2*i+5] & 0xFF) | (sensorRead[2*i+4] << 8));
//				irSensors[1] = ((sensorRead[7] & 0xFF) | (sensorRead[6] << 8));
//					Log.d(Tag, "ir"+i+":" + irSensors[1]);
//					Log.d(Tag, "ir"+i+":" );
//				}
				irSensors[0] = ((sensorRead[5] & 0xFF) | (sensorRead[4] << 8));
				irSensors[1] = ((sensorRead[7] & 0xFF) | (sensorRead[6] << 8));
				irSensors[2] = ((sensorRead[9] & 0xFF) | (sensorRead[8] << 8));
				irSensors[3] = ((sensorRead[11] & 0xFF) | (sensorRead[10] << 8));
				irSensors[4] = ((sensorRead[13] & 0xFF) | (sensorRead[12] << 8));				
				irSensors[5] = ((sensorRead[15] & 0xFF) | (sensorRead[14] << 8));
				irSensors[6] = ((sensorRead[17] & 0xFF) | (sensorRead[16] << 8));
				irSensors[7] = ((sensorRead[19] & 0xFF) | (sensorRead[18] << 8));
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
				});	  // runOnUIThread
			}			
	    }  	
	    }    
	} // Avoiding

}



// string dataset ="dragRace";
//	public File getDataStorageDir(String dataset) {
// Get the directory for the user's public pictures directory. 
//File file = new File(Environment.getExternalStoragePublicDirectory(
//        Environment.DIRECTORY_DOCUMENTS), dataSet);
//if (!file.mkdirs()) {
//    Log.e(LOG_TAG, "Directory not created");
//}
//    return file;
//}
