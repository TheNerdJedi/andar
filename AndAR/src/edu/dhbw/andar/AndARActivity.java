/**
	Copyright (C) 2009,2010  Tobias Domhan

    This file is part of AndOpenGLCam.

    AndObjViewer is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    AndObjViewer is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with AndObjViewer.  If not, see <http://www.gnu.org/licenses/>.
 
 */
package edu.dhbw.andar;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;


import edu.dhbw.andar.exceptions.AndARRuntimeException;
import edu.dhbw.andar.interfaces.OpenGLRenderer;
import edu.dhbw.andar.util.IO;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.SurfaceHolder.Callback;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

public abstract class AndARActivity extends Activity implements Callback, UncaughtExceptionHandler{
	private GLSurfaceView glSurfaceView;
	private Camera camera;
	private AndARRenderer renderer;
	private Resources res;
	private CameraPreviewHandler cameraHandler;
	private boolean mPausing = false;
	private ARToolkit artoolkit;
	private CameraStatus camStatus = new CameraStatus();
	private boolean surfaceCreated = false;
	private SurfaceHolder mSurfaceHolder = null;

	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.currentThread().setUncaughtExceptionHandler(this);
        res = getResources();
        
        artoolkit = new ARToolkit(res, getFilesDir());
        setFullscreen();
        disableScreenTurnOff();
        //orientation is set via the manifest
        
        try {
			IO.transferFilesToPrivateFS(getFilesDir(),res);
		} catch (IOException e) {
			e.printStackTrace();
			throw new AndARRuntimeException(e.getMessage());
		}
		FrameLayout frame = new FrameLayout(this);
		Preview preview = new Preview(this);
				
        glSurfaceView = new GLSurfaceView(this);
		renderer = new AndARRenderer(res, artoolkit, this);
		cameraHandler = new CameraPreviewHandler(glSurfaceView, renderer, res, artoolkit, camStatus);
        glSurfaceView.setRenderer(renderer);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        glSurfaceView.getHolder().addCallback(this);
        //setContentView(glSurfaceView);
        //addContentView(glSurfaceView, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
        frame.addView(glSurfaceView);
        frame.addView(preview);
        
        setContentView(frame);
        //if(Config.DEBUG)
        //	Debug.startMethodTracing("AndAR");
    }
    
    
    /**
     * Set a renderer that draws non AR stuff. Optional, may be set to null or omited.
     * and setups lighting stuff.
     * @param customRenderer
     */
    public void setNonARRenderer(OpenGLRenderer customRenderer) {
		renderer.setNonARRenderer(customRenderer);
	}

    /**
     * Avoid that the screen get's turned off by the system.
     */
	public void disableScreenTurnOff() {
    	getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
    			WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    
	/**
	 * Set's the orientation to landscape, as this is needed by AndAR.
	 */
    public void setOrientation()  {
    	setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }
    
    /**
     * Maximize the application.
     */
    public void setFullscreen() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }
   
    public void setNoTitle() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
    } 
    
    @Override
    protected void onPause() {
    	mPausing = true;
        this.glSurfaceView.onPause();
        super.onPause();
        finish();
        if(cameraHandler != null)
        	cameraHandler.stopThreads();
    }
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();    	
    	System.runFinalization();
    	//if(Config.DEBUG)
    	//	Debug.stopMethodTracing();
    }
    
    

    @Override
    protected void onResume() {
    	mPausing = false;
    	glSurfaceView.onResume();
        super.onResume();
    }
    
    /* (non-Javadoc)
     * @see android.app.Activity#onStop()
     */
    @Override
    protected void onStop() {
    	super.onStop();
    }
    
    private void openCamera()  {
    	if (camera == null) {
	    	//camera = Camera.open();
    		camera = CameraHolder.instance().open();
    		
    		try {
				camera.setPreviewDisplay(mSurfaceHolder);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
    		
	        Parameters params = camera.getParameters();
	        //reduce preview frame size for performance reasons
	        params.setPreviewSize(240,160);
	        try {
	        	camera.setParameters(params);
	        } catch(RuntimeException ex) {
	        	ex.printStackTrace();
	        }
	        
	        params = camera.getParameters();
	        //try to set the preview format
	        params.setPreviewFormat(PixelFormat.YCbCr_420_SP);
	        try {
	        	camera.setParameters(params);
	        } catch(RuntimeException ex) {
	        	ex.printStackTrace();
	        }	        
	        if(!Config.USE_ONE_SHOT_PREVIEW) {
	        	camera.setPreviewCallback(cameraHandler);	 
	        } 
			try {
				cameraHandler.init(camera);
			} catch (Exception e) {
				e.printStackTrace();
			}			
    	}
    }
    
    private void closeCamera() {
        if (camera != null) {
        	CameraHolder.instance().keep();
        	CameraHolder.instance().release();
        	camera = null;
        	camStatus.previewing = false;
        }
    }
    
    /**
     * Open the camera and start detecting markers.
     */
    public void startPreview() {
    	if(!surfaceCreated) return;
    	if(mPausing || isFinishing()) return;
    	if (camStatus.previewing) stopPreview();
    	openCamera();
		camera.startPreview();
		camStatus.previewing = true;
    }
    
    /**
     * Close the camera and stop detecting markers.
     */
    public void stopPreview() {
    	if (camera != null && camStatus.previewing ) {
    		camStatus.previewing = false;
            camera.stopPreview();
         }
    	
    }

	/* The GLSurfaceView changed
	 * @see android.view.SurfaceHolder.Callback#surfaceChanged(android.view.SurfaceHolder, int, int, int)
	 */
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		/*if (Integer.parseInt(Build.VERSION.SDK) < 4) {
        	//for android 1.5 compatibilty reasons:
			 try {
				 if(holder != null && camera != null)
					 camera.setPreviewDisplay(holder);
			 } catch (IOException e1) {
			        e1.printStackTrace();
			 }
        }*/
		// We need to save the holder for later use, even when the mCameraDevice
        // is null. This could happen if onResume() is invoked after this
        // function.
        //mSurfaceHolder = holder;
		
	}

	/* The GLSurfaceView was created
	 * The camera will be opened and the preview started 
	 * @see android.view.SurfaceHolder.Callback#surfaceCreated(android.view.SurfaceHolder)
	 */
	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		surfaceCreated = true;			
	}

	/* GLSurfaceView was destroyed
	 * The camera will be closed and the preview stopped.
	 * @see android.view.SurfaceHolder.Callback#surfaceDestroyed(android.view.SurfaceHolder)
	 */
	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {

	}
	
	/**
	 * @return  a the instance of the ARToolkit.
	 */
	public ARToolkit getArtoolkit() {
		return artoolkit;
	}	
	
	/**
	 * Take a screenshot. Must not be called from the GUI thread, e.g. from methods like
	 * onCreateOptionsMenu and onOptionsItemSelected. You have to use a asynctask for this purpose.
	 * @return the screenshot
	 */
	public Bitmap takeScreenshot() {
		return renderer.takeScreenshot();
	}
	
	/**
	 * 
	 * @return the OpenGL surface.
	 */
	public SurfaceView getSurfaceView() {
		return glSurfaceView;
	}
	
	class Preview extends SurfaceView implements SurfaceHolder.Callback {
	    SurfaceHolder mHolder;
	    Camera mCamera;
	    
	    Preview(Context context) {
	        super(context);
	        
	        // Install a SurfaceHolder.Callback so we get notified when the
	        // underlying surface is created and destroyed.
	        mHolder = getHolder();
	        mHolder.addCallback(this);
	        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	    }

	    public void surfaceCreated(SurfaceHolder holder) {
	    }

	    public void surfaceDestroyed(SurfaceHolder holder) {
	        // Surface will be destroyed when we return, so stop the preview.
	        // Because the CameraDevice object is not a shared resource, it's very
	        // important to release it when the activity is paused.
	        stopPreview();
	        closeCamera();
	        mSurfaceHolder = null;
	    }

	    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
	    	mSurfaceHolder = holder;
	    	startPreview();
	    }

	}
}