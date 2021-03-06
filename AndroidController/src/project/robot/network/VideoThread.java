package project.robot.network;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;

import project.robot.MotionDetector;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;

/*
 * Handles streaming of video to specified host
 * It sends video using UDP Protocol and compress each image
 * to JPEG format
 */
public class VideoThread {
	Camera camera;
	Sender sender;
	Thread t;
	String ip;
	boolean isVideoOn;
	Bitmap bitmap;
	int imgWidth, imgHeight;
	int[] rgbData;
	
	MotionDetector detector;
	boolean msgFlag;	//Motion detection flag
	public boolean videoStream;	//Video Stream Flag
	
	public VideoThread(String ip1) {
		sender = null;
		camera = null;
		ip = ip1;
		detector = new MotionDetector();
		msgFlag = true;
		videoStream = false;
	}
	
	private void sendSMSAlert() {
		try {
			URLConnection conn = new URL("http://home.iitb.ac.in/~saifhhasan/sms.php?alert=Motion%20activity%20detected%20by%20your%20android%20phone").openConnection();
			BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line;
			System.out.println("VideoThread: Printing response got from server");
			while((line = in.readLine()) != null) {
				System.out.println(line);
			}
			in.close();
		} catch (Exception e) {
			System.out.println("VideoThread: Error while sending sms alert");
			e.printStackTrace();
		}
	}
	
	public void stopVideo() {
		isVideoOn = false;
		if(camera!=null) {
			camera.stopPreview();
			camera.setPreviewCallback(null);
			camera.release();
			camera = null;
			if(sender!=null)
				sender.close();
			sender = null;
		}
	}
	
	public void startVideo() throws UnknownHostException, SocketException {
		sender = new Sender(ip, 8656);
		camera = Camera.open();
		Camera.Parameters parameters = camera.getParameters(); 
		parameters.setPreviewSize(480, 320);
		parameters.setPreviewFrameRate(10);
		//parameters.setPictureFormat(ImageFormat.JPEG);
		parameters.setSceneMode(Camera.Parameters.SCENE_MODE_SPORTS);
		parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
		parameters.setColorEffect(Camera.Parameters.EFFECT_NONE);
		camera.setParameters(parameters);		
		camera.setPreviewCallback(new camPreviewCallback());           
		camera.startPreview();
		isVideoOn = true;
		parameters = camera.getParameters();
		imgWidth = parameters.getPreviewSize().width;
		imgHeight = parameters.getPreviewSize().height;
		System.out.println("Camera enabled . . started recording :)");
		rgbData = new int[imgWidth * imgHeight];
		bitmap = Bitmap.createBitmap(imgWidth, imgHeight, Bitmap.Config.RGB_565);
	}
	
	class camPreviewCallback implements PreviewCallback {
		
		//Called every time whenever a new frame is generated
		public void onPreviewFrame(byte[] arg0, Camera arg1) {
			if(isVideoOn == false) {
				System.out.println("camPreviewClallback: camera is disabled");
				return;
			}
			else {
				//Convert byte array into RGB Color values
				decodeYUV420SP(rgbData, arg0, imgWidth, imgHeight);
				//Sets up bitmap with data
				bitmap.setPixels(rgbData, 0, imgWidth, 0, 0, imgWidth, imgHeight);
				
				if(videoStream) {
					ByteArrayOutputStream stream = new ByteArrayOutputStream();
					//Apply compression
					bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream);
					
					//Gets bytes from bitmap
					sender.sendPacket(stream.toByteArray());
				}
				
				//This is used for motion detection
				if(msgFlag && detector.detectMotion(rgbData)) {
					//Sending sms alert in thread so video streaming is not affected
					new Thread() {
						public void run() {
							sendSMSAlert();
						}
					}.start();
					msgFlag = false;
					System.out.println("VideoThread: sms sent");
				}
			}
		}
	}
	
	
	/* function converting image to RGB format taken from project: ViewfinderEE368  
	 * http://www.stanford.edu/class/ee368/Android/ViewfinderEE368/
	 * 
	 * Copyright (C) 2007 The Android Open Source Project
	 *
	 * Licensed under the Apache License, Version 2.0 (the "License");
	 * you may not use this file except in compliance with the License.
	 * You may obtain a copy of the License at
	 *
	 *      http://www.apache.org/licenses/LICENSE-2.0
	 *
	 * Unless required by applicable law or agreed to in writing, software
	 * distributed under the License is distributed on an "AS IS" BASIS,
	 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	 * See the License for the specific language governing permissions and
	 * limitations under the License.
	 */
	static public void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width, int height) 
	{
		final int frameSize = width * height;

		for (int j = 0, yp = 0; j < height; j++) {
			int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
			for (int i = 0; i < width; i++, yp++) {
				int y = (0xff & ((int) yuv420sp[yp])) - 16;
				if (y < 0) y = 0;
				if ((i & 1) == 0) {
					v = (0xff & yuv420sp[uvp++]) - 128;
					u = (0xff & yuv420sp[uvp++]) - 128;
				}
				int y1192 = 1192 * y;
				int r = (y1192 + 1634 * v);
				int g = (y1192 - 833 * v - 400 * u);
				int b = (y1192 + 2066 * u);

				if (r < 0) r = 0; else if (r > 262143) r = 262143;
				if (g < 0) g = 0; else if (g > 262143) g = 262143;
				if (b < 0) b = 0; else if (b > 262143) b = 262143;

				rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
			}
		}
	}
}