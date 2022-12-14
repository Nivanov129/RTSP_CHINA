/*
	Copyright (c) 2012-2017 EasyDarwin.ORG.  All rights reserved.
	Github: https://github.com/EasyDarwin
	WEChat: EasyDarwin
	Website: http://www.easydarwin.org
*/

package org.easydarwin.easyscreenlive.screen_live;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;


import org.easydarwin.easyscreenlive.screen_live.base.EasyVideoStreamCallback;
import org.easydarwin.easyscreenlive.screen_live.base.EasyVideoSource;
import org.easydarwin.easyscreenlive.screen_live.hw.EncoderDebugger;
import org.easydarwin.easyscreenlive.screen_live.hw.NV21Convertor;
import org.easydarwin.easyscreenlive.ui.ScreenLiveActivity;
import org.easydarwin.easyscreenlive.screen_live.utils.Util;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

class EasyCameraCap extends EasyVideoSource {
    static final String TAG = "EasyCameraCap";
    private int width = 640, height = 480;
    private int framerate = 25;
    private int bitrate = 0;
    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private MediaCodec mMediaCodec;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private Camera mCamera;
    private NV21Convertor mConvertor;

    private Consumer mConsumer;
    private boolean isCameraBack = true;
    private int mDgree;
    private Context mApplicationContext;
    private boolean codecAvailable = false;

    private boolean mPortraitScreen = true;
    private String MIMETYPE = MediaFormat.MIMETYPE_VIDEO_AVC;

    public EasyCameraCap(Context context, SurfaceView mSurfaceView, int cameraId) {
        mApplicationContext = context;
        this.mSurfaceView = mSurfaceView;
        mSurfaceHolder = mSurfaceView.getHolder();
        mCameraId = cameraId;
    }

    public void setDgree(int dgree) {
        mDgree = dgree;
    }

    /**
     * ????????????
     */
    public void changeScreenOrientation(){
        mPortraitScreen = !mPortraitScreen;
    }
    public boolean getScreenOrientation(){
        return mPortraitScreen;
    }

    /**
     * ???????????????
     */
    public void updateResolution(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * ????????????
     */
    public void reStartStream() {
        if (mCamera == null) return;
        stopStream();
        uninit();
        init(MIMETYPE, width, height, framerate, bitrate, easyVideoStreamCallback);
        stopStream();
    }

    public static int[] determineMaximumSupportedFramerate(Camera.Parameters parameters) {
        int[] maxFps = new int[]{0, 0};
        List<int[]> supportedFpsRanges = parameters.getSupportedPreviewFpsRange();
        for (Iterator<int[]> it = supportedFpsRanges.iterator(); it.hasNext(); ) {
            int[] interval = it.next();
            if (interval[1] > maxFps[1] || (interval[0] > maxFps[0] && interval[1] == maxFps[1])) {
                maxFps = interval;
            }
        }
        return maxFps;
    }


    private boolean createCamera() {
        if (mCamera != null) {
            destroyCamera();
        }
        try {
            mCamera = Camera.open(mCameraId);
            Camera.Parameters parameters = mCamera.getParameters();
            int[] max = determineMaximumSupportedFramerate(parameters);
            Camera.CameraInfo camInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(mCameraId, camInfo);
            int cameraRotationOffset = camInfo.orientation;
            if (mCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT)
                cameraRotationOffset += 180;
            int rotate = (360 + cameraRotationOffset - mDgree) % 360;
            parameters.setRotation(rotate);

            parameters.setPreviewFormat(ImageFormat.NV21);
            List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
            parameters.setPreviewSize(width, height);
            parameters.setPreviewFrameRate(20);
            mCamera.setParameters(parameters);
            int displayRotation = (cameraRotationOffset - mDgree + 360) % 360;

            mCamera.setDisplayOrientation(displayRotation);

            mCamera.setPreviewDisplay(mSurfaceHolder);
            return true;
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String stack = sw.toString();
            destroyCamera();
            e.printStackTrace();

            return false;
        }
    }


    private class TimedBuffer {
        byte[] buffer;
        long time;

        public TimedBuffer(byte[] data) {
            buffer = data;
            time = System.currentTimeMillis();
        }
    }

    private ArrayBlockingQueue<TimedBuffer> yuvs = new ArrayBlockingQueue<TimedBuffer>(5);
    private ArrayBlockingQueue<byte[]> yuv_caches = new ArrayBlockingQueue<byte[]>(10);

    class Consumer extends Thread {
        ByteBuffer[] inputBuffers;
        ByteBuffer[] outputBuffers;
        byte[] mPpsSps = new byte[0];
        int keyFrmHelperCount = 0;
        private long timeStamp = System.currentTimeMillis();

        public Consumer() {
            super("Consumer");
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            Camera.Size previewSize = getCamera().getParameters().getPreviewSize();
            byte[] h264 = new byte[(int) (previewSize.width * previewSize.height * 3/2)];
            try {
                startMediaCodec();
                while (mConsumer != null && codecAvailable) {
                    TimedBuffer tb;
                    tb = yuvs.take();
                    if(!codecAvailable){
                        break;
                    }

                    byte[] data = tb.buffer;
                    long stamp = tb.time;
                    int[] outLen = new int[1];
                    if (mDgree == 0 && mPortraitScreen) {
                        Camera.CameraInfo camInfo = new Camera.CameraInfo();
                        Camera.getCameraInfo(mCameraId, camInfo);

                        int cameraRotationOffset = camInfo.orientation;
                        if (cameraRotationOffset == 90) {
                            data =  Util.rotateNV21Degree90(data, previewSize.width, previewSize.height);
                        } else if (cameraRotationOffset == 270) {
                            data =  Util.rotateNV21Negative90(data, previewSize.width, previewSize.height);
                        }
                    }
                    {
                        inputBuffers = mMediaCodec.getInputBuffers();
                        outputBuffers = mMediaCodec.getOutputBuffers();
                        int bufferIndex = mMediaCodec.dequeueInputBuffer(5000);
                        if (bufferIndex >= 0) {
                            inputBuffers[bufferIndex].clear();
                            mConvertor.convert(data, inputBuffers[bufferIndex]);
                            mMediaCodec.queueInputBuffer(bufferIndex, 0, inputBuffers[bufferIndex].position(), stamp* 1000, 0);

                            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                            int outputBufferIndex = 0;
                            do {
                                final int tmpIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                                 outputBufferIndex = tmpIndex;
                                if (outputBufferIndex<0){
                                    break;
                                }
                                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                                //??????pps???sps
                                if (MIMETYPE.equals(MediaFormat.MIMETYPE_VIDEO_AVC)) {
                                    int type = outputBuffer.get(4) & 0x1F;
                                    Log.d(TAG, String.format("type is %d", type));
                                    if (type == 7 || type == 8) {
                                        byte[] outData = new byte[bufferInfo.size];
                                        outputBuffer.get(outData, 0, bufferInfo.size);
                                        mPpsSps = outData;
                                    } else if (type == 5) {
                                        //????????????????????????pps???sps??????
                                        System.arraycopy(mPpsSps, 0, h264, 0, mPpsSps.length);
                                        outputBuffer.get(h264, mPpsSps.length, bufferInfo.size);
                                        if (easyVideoStreamCallback != null) {
                                            easyVideoStreamCallback.videoDataBack(bufferInfo.presentationTimeUs / 1000, h264, 0, mPpsSps.length + bufferInfo.size);
                                        }
                                    } else {
                                        outputBuffer.get(h264, 0, bufferInfo.size);
                                        if (System.currentTimeMillis() - timeStamp >= 3000) {
                                            timeStamp = System.currentTimeMillis();
                                            if (Build.VERSION.SDK_INT >= 23) {
                                                Bundle params = new Bundle();
                                                params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
                                                mMediaCodec.setParameters(params);
                                            }
                                        }
                                        if (easyVideoStreamCallback != null) {
                                            easyVideoStreamCallback.videoDataBack(bufferInfo.presentationTimeUs / 1000, h264, 0, bufferInfo.size);
                                        }
//                                    Log.i(TAG, "---len:"+ bufferInfo.size);
                                    }
                                } else {
                                    //int type = (outputBuffer.get(4)>>1) & 0x3f;
                                    int type =  (outputBuffer.get(4) & 0x7E)>>1;
                                    Log.i(TAG, "---type:"+ type);
                                    if (type == 32 || type == 33 || type == 34) {
                                        byte[] outData = new byte[bufferInfo.size];
                                        outputBuffer.get(outData, 0, bufferInfo.size);
                                        mPpsSps = outData;
                                    } else if (type >= 16 && type <=21) {
                                        //????????????????????????pps???sps??????
                                        System.arraycopy(mPpsSps, 0, h264, 0, mPpsSps.length);
                                        outputBuffer.get(h264, mPpsSps.length, bufferInfo.size);
                                        if (easyVideoStreamCallback != null) {
                                            easyVideoStreamCallback.videoDataBack(bufferInfo.presentationTimeUs / 1000, h264, 0, mPpsSps.length + bufferInfo.size);
                                        }
                                    } else {
                                        outputBuffer.get(h264, 0, bufferInfo.size);
                                        if (System.currentTimeMillis() - timeStamp >= 3000) {
                                            timeStamp = System.currentTimeMillis();
                                            if (Build.VERSION.SDK_INT >= 23) {
                                                Bundle params = new Bundle();
                                                params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
                                                mMediaCodec.setParameters(params);
                                            }
                                        }
                                        if (easyVideoStreamCallback != null) {
                                            easyVideoStreamCallback.videoDataBack(bufferInfo.presentationTimeUs / 1000, h264, 0, bufferInfo.size);
                                        }
                                    }
                                }

                                mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                            }
                            while(outputBufferIndex >= 0);
                        }
                    }
                    yuv_caches.offer(data);

                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }finally {
                if (mMediaCodec != null) {
                    mMediaCodec.stop();
                    mMediaCodec.release();
                    mMediaCodec = null;
                }
            }
        }
    }

    /**
     * ????????????
     */
    public synchronized void startPreview() {
        if (mCamera != null) {
            yuv_caches.clear();
            yuvs.clear();
            mCamera.startPreview();
            try {
                mCamera.autoFocus(null);
            } catch (Exception e) {
                Log.i(TAG, "auto foucus fail");
            }

            int previewFormat = mCamera.getParameters().getPreviewFormat();
            Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
            int size = previewSize.width * previewSize.height
                    * ImageFormat.getBitsPerPixel(previewFormat)
                    / 8;
            mCamera.addCallbackBuffer(new byte[size]);
            mCamera.setPreviewCallbackWithBuffer(previewCallback);
        }
    }

    Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
        @Override
        public synchronized void onPreviewFrame(byte[] data, Camera camera) {
            if(data == null || camera == null){
                return;
            }

            if (!codecAvailable || mConsumer == null) {
                camera.addCallbackBuffer(data);
                return;
            }

            Camera.Size previewSize = camera.getParameters().getPreviewSize();
            if (data.length != previewSize.width * previewSize.height * 3 / 2) {
                camera.addCallbackBuffer(data);
                return;
            }

            byte[] buffer = yuv_caches.poll();
            if (buffer == null || buffer.length != data.length) {
                buffer = new byte[data.length];
            }
            yuvs.offer(new TimedBuffer(data));
            camera.addCallbackBuffer(buffer);
        }
    };


    /**
     * ????????????
     */
    private synchronized void stopPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallbackWithBuffer(null);
        }
    }

    public synchronized Camera getCamera() {
        return mCamera;
    }

    /**
     * ?????????????????????
     */
    public void switchCamera() {
        int cameraCount = 0;
        if (isCameraBack) {
            isCameraBack = false;
        } else {
            isCameraBack = true;
        }
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();//????????????????????????
        for (int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, cameraInfo);//?????????????????????????????????
            if (mCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                //?????????????????????????????????
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {//???????????????????????????CAMERA_FACING_FRONT??????      CAMERA_FACING_BACK??????
                    destroyCamera();
                    mCamera = null;//?????????????????????
                    mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
                    createCamera();
                    startPreview();
                    break;
                }
            } else {
                //?????????????????? ???????????????
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {//???????????????????????????CAMERA_FACING_FRONT??????      CAMERA_FACING_BACK??????
                    destroyCamera();
                    mCamera = null;//?????????????????????
                    mCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
                    createCamera();
                    startPreview();
                    break;
                }
            }
        }
    }

    /**
     * ??????Camera
     */
    private synchronized void destroyCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            try {
                mCamera.release();
            } catch (Exception e) {

            }
            mCamera = null;
        }
    }


    /**
     * ??????????????????
     */
    private void startMediaCodec() {
        EncoderDebugger debugger;
        if(mPortraitScreen)
            debugger = EncoderDebugger.buildDebug(mApplicationContext, MIMETYPE, true, width, height, framerate);//width, height
        else
            debugger = EncoderDebugger.buildDebug(mApplicationContext, MIMETYPE, true, height, width, framerate);//width, height
        mConvertor = debugger.getNV21Convertor();

        try {
            Log.i(TAG, "gavin codec name" + debugger.getEncoderName());
            mMediaCodec = MediaCodec.createByCodecName(debugger.getEncoderName());
            MediaFormat mediaFormat;
            if (mDgree == 0 && mPortraitScreen) {
                mediaFormat = MediaFormat.createVideoFormat(MIMETYPE, height, width);
            } else {
                mediaFormat = MediaFormat.createVideoFormat(MIMETYPE, width, height);
            }
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,debugger.getEncoderColorFormat());
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();
            codecAvailable = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public  int init(String mimeType, int w, int h, int fps, int bitRate, EasyVideoStreamCallback cb){
        MIMETYPE = mimeType;
        this.width = w;
        this.height = h;
        this.framerate = fps;
        this.bitrate = bitRate;
        this.easyVideoStreamCallback = cb;
        setDgree(ScreenLiveActivity.mDgree);

        createCamera();
        startPreview();
        return 0;
    }

    @Override
    public  int uninit(){
        stopStream();
        destroyCamera();
        return 0;
    }

    @Override
    public  int startStream() {
        yuv_caches.clear();
        yuvs.clear();

        Thread t = mConsumer;
        if (t != null) {
            mConsumer = null;
            t.interrupt();
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mConsumer = new Consumer();
        mConsumer.start();
        return 0;
    }

    @Override
    public  int stopStream() {
        codecAvailable = false;
        Thread t = mConsumer;
        if (t != null) {
            t.interrupt();
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mConsumer = null;
        }
        return 0;
    }
}
