package net.servusrobotics.kickstudio;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;


public class WatchActivity extends Activity {

	private static final String  TAG = "WatchActivity";
	public CameraBridgeViewBase mOpenCvCameraView;

	public WatchActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_watch); 
		mOpenCvCameraView = (CameraBridgeViewBase)findViewById(R.id.activity_watch);
    //    mOpenCvCameraView.setCvCameraViewListener((CvCameraViewListener2) this);
		
	}


	
	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			switch (status) {
				case LoaderCallbackInterface.SUCCESS:
				{
					Log.i(TAG, "OpenCV loaded successfully");
				    mOpenCvCameraView.enableView();
	//			    mOpenCvCameraView.setOnTouchListener(net.servusrobotics.kick.WatchActivity.this);
				} break;
				default:
				{
					super.onManagerConnected(status);
				} break;
			}
		}
	};

		
	@Override
	public void onResume()
	{
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
	}
	
//	@Override
//	public void onDestroy() {
//		super.onDestroy();
//		if 	(mOpenCvCameraView != null)
 //       mOpenCvCameraView.disableView();
//	}
}



