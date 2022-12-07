package org.easydarwin.easyscreenlive.screen_live;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import org.easydarwin.easyscreenlive.screen_live.base.EasyVideoSource;
import org.easydarwin.easyscreenlive.screen_live.base.EasyVideoStreamCallback;
import org.easydarwin.easyscreenlive.screen_live.hw.CodecInfo;
import org.easydarwin.rtspservice.JniLibYuv;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import static android.content.Context.MEDIA_PROJECTION_SERVICE;
import static org.easydarwin.easyscreenlive.screen_live.hw.HWConsumer.listEncoders;

/**
 * Created by gavin on 2017/12/30.
 */

class EasyScreenCap extends EasyVideoSource {
    private final static String TAG = "EasyScreenCap";

    public Context mContext;
    private int windowWidth = 1920;
    private int windowHeight = 1080;
    private int mFrameRate = 30;
    private int mBitRate = 4000 * 1000;
    private int mKeyFrameTimeMs = 3*1000;

    private int screenDensity;
    private MediaProjectionManager mMpmngr;
    private MediaCodec mMediaCodec;
    private Surface mSurface;
    private ImageReader mImageReader;
    private byte[] mPpsSps;
    private MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private MediaProjection mMpj;
    private VirtualDisplay mVirtualDisplay;
    private long lastKeyFrmaeTime;

    private boolean mediaCodecRunning = false;

    private EncodecThread encodecThread = null;
    private boolean isUsedCaptureImageReader = false;
    private CodecInfo codecInfo = new CodecInfo();
    private String MIMETYPE = MediaFormat.MIMETYPE_VIDEO_AVC;

    public EasyScreenCap(Context context) {
        SOURCE_TYPE = SOURCE_TYPE_SCREEN;
        mContext = context;
        if (EasyScreenLiveAPI.liveRtspConfig.frameRate != 0) {
            isUsedCaptureImageReader = true;
            mFrameRate = EasyScreenLiveAPI.liveRtspConfig.frameRate;
        } else {
            isUsedCaptureImageReader = false;
        }
    }

    @Override
    public synchronized int init(String mimeType, int w, int h, int fps, int bitRate, EasyVideoStreamCallback easyVideoStreamCallback) {
        if (mimeType != null) {
            MIMETYPE = mimeType;
        }
        if (EasyScreenLiveAPI.liveRtspConfig.pushdev == 0 && w < h) { // 横屏
            windowWidth     = h;
            windowHeight    = w;
        } else if(EasyScreenLiveAPI.liveRtspConfig.pushdev == 1 && w > h){ // 竖屏
            windowWidth     = h;
            windowHeight    = w;
        } else {
            windowWidth     = w;
            windowHeight    = h;
        }

        // 宽 64 位对齐
        if (w % 64 != 0) {
            windowWidth = 64*((windowWidth/64) +1);
        }
        if (w % 64 != 0) {
            windowHeight = 64*((windowHeight/64) +1);
        }
        Log.i(TAG, "w:" + windowWidth + " h:" +windowHeight);

        mFrameRate = fps;
        mBitRate = bitRate;
        this.easyVideoStreamCallback = easyVideoStreamCallback;

        WindowManager wm;
        wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(displayMetrics);
        screenDensity = displayMetrics.densityDpi;
        mMpmngr = (MediaProjectionManager) mContext.getApplicationContext().getSystemService(MEDIA_PROJECTION_SERVICE);
        mMpj = mMpmngr.getMediaProjection(EasyScreenLiveAPI.liveRtspConfig.capScreenCode,
                EasyScreenLiveAPI.liveRtspConfig.capScreenIntent);
        if (mMpj == null) {
            mMpmngr = null;
            return -1;
        }
        return 0;
    }

    @Override
    public synchronized int uninit() {
        stopStream();
        if (mMpj != null) {
            mMpj.stop();
        }
        mMpj = null;
        mMpmngr = null;
        return 0;
    }

    @Override
    public int startStream(){
        synchronized(this) {
            if (EasyScreenLiveAPI.liveRtspConfig.pushdev == 0 && windowWidth < windowHeight) { // 横屏
                int tmp = windowWidth;
                windowWidth     = windowHeight;
                windowHeight    = tmp;
            } else if(EasyScreenLiveAPI.liveRtspConfig.pushdev == 1 && windowWidth > windowHeight){ // 竖屏
                int tmp = windowWidth;
                windowWidth     = windowHeight;
                windowHeight    = tmp;
            }
            Log.e(TAG, "res:"+windowWidth+" * " +windowHeight);
            return startMediaCodec();
        }

    }

    @Override
    public int stopStream(){
        synchronized(this) {
            return stopMediaCodec();
        }
    }


    private int startMediaCodec() {
        if (mediaCodecRunning) {
            Log.e(TAG, "alread startMediaCodec");
            return -1;
        }
        lastKeyFrmaeTime = System.currentTimeMillis();

        ArrayList<CodecInfo> infos = listEncoders(MIMETYPE);
        if (infos.size() > 0) {
            CodecInfo ci = infos.get(0);
            codecInfo.mMimeType = ci.mMimeType;
            codecInfo.mCodecOutName = ci.mCodecOutName;
            codecInfo.mInColorFormat = ci.mInColorFormat;
        }

        try {
            mMediaCodec = MediaCodec.createEncoderByType(MIMETYPE);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }

        MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIMETYPE, windowWidth, windowHeight);
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mFrameRate*3);
        mediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
        if (isUsedCaptureImageReader) {
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, codecInfo.mInColorFormat);
        } else {
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            mediaFormat.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000/mFrameRate);
        }

        mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        if (isUsedCaptureImageReader) {
            mImageReader = ImageReader.newInstance(windowWidth,windowHeight, PixelFormat.RGBA_8888, 1);
            mSurface = mImageReader.getSurface();
        } else {
            mSurface = mMediaCodec.createInputSurface();
        }

        mVirtualDisplay = mMpj.createVirtualDisplay("record_screen", windowWidth, windowHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC |
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION, mSurface, null, null);

        mMediaCodec.start();
        mediaCodecRunning = true;
        encodecThread = new EncodecThread();
        encodecThread.start();

        return 0;
    }

    /**
     * 停止编码并释放编码资源占用
     */
    private int stopMediaCodec() {
        Log.i(TAG, "stopMediaCodec  1");
        if (!mediaCodecRunning) {
            return -1;
        }
        Thread t = encodecThread;
        if (t != null) {
            encodecThread = null;
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mMediaCodec = null;
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        if (isUsedCaptureImageReader) {
            mImageReader.close();
            mImageReader = null;
        }

        mediaCodecRunning = false;
        return 0;
    }

    private void requestKeyFram() {
        if (mMediaCodec != null) {
            Bundle param = new Bundle();
            param.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
            mMediaCodec.setParameters(param);
        }
    }

    class EncodecThread extends Thread{
        ByteBuffer[] inputBuffers;
        byte[] argbFrame = new byte[windowWidth * windowHeight * 4];
        byte[] frameBuffer=new byte[windowWidth*windowHeight*3/2];
        byte[] nv21Buffer=new byte[windowWidth*windowHeight*3/2];
        long lastFrameTime = 0;

        public void run() {
            Log.i(TAG, "startPush thread");
            while (encodecThread != null) {
                long systemNow = System.currentTimeMillis();

                setVideoInData(systemNow);

                byte[] outData = getVideoOutData(systemNow);
                if(easyVideoStreamCallback != null && outData != null ) {
                    easyVideoStreamCallback.videoDataBack(System.currentTimeMillis(),
                            outData, 0, outData.length);
                } else {
                    if (System.currentTimeMillis() - systemNow < 10) {
                        try {
                            Thread.sleep(10);
                        }catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                }
            }
            Log.i(TAG, "stop thread");
        }

        private void setVideoInData(long systemTimeNow) {
            if (isUsedCaptureImageReader && mImageReader != null) {
                Image image = mImageReader.acquireLatestImage();
                if (image != null) {
                    lastFrameTime = systemTimeNow;
                    ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                    byteBuffer.get(argbFrame);

                    JniLibYuv.argbtoi420(argbFrame,frameBuffer,windowWidth,windowHeight);

                    if (codecInfo.mInColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                        JniLibYuv.yuvConvert(frameBuffer, nv21Buffer, windowWidth, windowHeight, JniLibYuv.I420_TO_YV21);
                    } else if (codecInfo.mInColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar) {
                        JniLibYuv.yuvConvert(frameBuffer, nv21Buffer, windowWidth, windowHeight, JniLibYuv.I420_TO_YV21);
                    } else if (codecInfo.mInColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                        ;
                    }

                    inputBuffers = mMediaCodec.getInputBuffers();

                    int bufferIndex = mMediaCodec.dequeueInputBuffer(5000);
                    if (bufferIndex >= 0) {
                        inputBuffers[bufferIndex].clear();

                        int min = inputBuffers[bufferIndex].capacity() < frameBuffer.length
                                ? inputBuffers[bufferIndex].capacity() : frameBuffer.length;
                        inputBuffers[bufferIndex].put(nv21Buffer, 0, min);

                        mMediaCodec.queueInputBuffer(bufferIndex, 0, inputBuffers[bufferIndex].position(),
                                System.currentTimeMillis() * 1000, 0);
                    }
                    image.close();
                } else {
                    if (lastFrameTime != 0 && systemTimeNow -lastFrameTime > 1000/mFrameRate*2) {
                        lastFrameTime = systemTimeNow;
                        inputBuffers = mMediaCodec.getInputBuffers();
                        int bufferIndex = mMediaCodec.dequeueInputBuffer(5000);
                        if (bufferIndex >= 0) {
                            inputBuffers[bufferIndex].clear();

                            int min = inputBuffers[bufferIndex].capacity() < frameBuffer.length
                                    ? inputBuffers[bufferIndex].capacity() : frameBuffer.length;
                            inputBuffers[bufferIndex].put(nv21Buffer, 0, min);

                            mMediaCodec.queueInputBuffer(bufferIndex, 0, inputBuffers[bufferIndex].position(),
                                    System.currentTimeMillis() * 1000, 0);
                        }
                    }
                }
            }
        }

        private byte[] getVideoOutData(long systemTimeNow) {
            if (mediaCodecRunning) {
                int index = mMediaCodec.dequeueOutputBuffer(mBufferInfo, 10000);
                if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {//请求超时
                    return null;
                } else if (index >= 0) {//有效输出
                    ByteBuffer outputBuffer = mMediaCodec.getOutputBuffer(index);
                    byte[] outData = new byte[mBufferInfo.size];
                    outputBuffer.get(outData);

                    if (MIMETYPE.equals(MediaFormat.MIMETYPE_VIDEO_AVC)) {
                        //记录pps和sps
                        int type = outData[4] & 0x07;
                        if (type == 7 || type == 8) {
                            mPpsSps = new byte[outData.length];
                            mPpsSps = outData;
                        } else if (type == 5) {
                            //在关键帧前面加上pps和sps数据
                            if (mPpsSps != null) {
                                byte[] iframeData = new byte[mPpsSps.length + outData.length];
                                System.arraycopy(mPpsSps, 0, iframeData, 0, mPpsSps.length);
                                System.arraycopy(outData, 0, iframeData, mPpsSps.length, outData.length);
                                outData = iframeData;
                            }
//                       收到一个关键帧，重置关键帧时间
                            lastKeyFrmaeTime = systemTimeNow;
                        }
                    } else {
                        int type =  (outData[4] & 0x7E)>>1;
                        if (type == 32 || type == 33 || type == 34) {
                            mPpsSps = new byte[outData.length];
                            mPpsSps = outData;
                        } else if (type >= 16 && type <=21) {
                            //在关键帧前面加上pps和sps数据
                            if (mPpsSps != null) {
                                byte[] iframeData = new byte[mPpsSps.length + outData.length];
                                System.arraycopy(mPpsSps, 0, iframeData, 0, mPpsSps.length);
                                System.arraycopy(outData, 0, iframeData, mPpsSps.length, outData.length);
                                outData = iframeData;
                            }
//                       收到一个关键帧，重置关键帧时间
                            lastKeyFrmaeTime = systemTimeNow;
                        } else {
                        }
                    }

//                  每秒过一秒申请一个关键帧
                    if (System.currentTimeMillis() - lastKeyFrmaeTime > mKeyFrameTimeMs) {
                        requestKeyFram();
                        lastKeyFrmaeTime = systemTimeNow;
                    }
                    mMediaCodec.releaseOutputBuffer(index, false);
                    return outData;
                }
            }
            return null;
        }

    }
}
