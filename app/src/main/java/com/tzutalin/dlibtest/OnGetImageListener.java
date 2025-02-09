/*
 * Copyright 2016-present Tzutalin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tzutalin.dlibtest;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Handler;
import android.os.Trace;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.PedestrianDet;
import com.tzutalin.dlib.VisionDetRet;

import junit.framework.Assert;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Class that takes in preview frames and converts the image to Bitmaps to process with dlib lib.
 */
public class OnGetImageListener implements OnImageAvailableListener {
  private static final boolean SAVE_PREVIEW_BITMAP = false;

  private static final int INPUT_SIZE = 224;
  private static final String TAG = "OnGetImageListener";

  private int mScreenRotation = 90;

  private Activity mActivity;

  private int mPreviewWidth = 0;
  private int mPreviewHeight = 0;
  private byte[][] mYUVBytes;
  private int[] mRGBBytes = null;
  private Bitmap mRGBFrameBitmap = null;
  private Bitmap mCroppedBitmap = null;
  private Bitmap mCroppedBitmapFace = null;

  private boolean mIsComputing = false;
  private Handler mInferenceHandler;

  private Context mContext;
  private FaceDet mFaceDet;
  private PedestrianDet mPedestrianDet;
  private TrasparentTitleView mTransparentTitleView;
  private FloatingCameraWindow mWindow;
  private Paint mFaceLandmarkPaint;

  private OnFaceDetectedListener mFaceDetectedListener;

  public OnGetImageListener(Activity activity, OnFaceDetectedListener faceDetectedListener) {
    mActivity = activity;
    mFaceDetectedListener = faceDetectedListener;
  }

  public void initialize(
      final Context context,
      final AssetManager assetManager,
      final TrasparentTitleView scoreView,
      final Handler handler) {
    this.mContext = context;
    this.mTransparentTitleView = scoreView;
    this.mInferenceHandler = handler;
    mFaceDet = new FaceDet(Constants.getFaceShapeModelPath(mContext));
    mPedestrianDet = new PedestrianDet();
    mWindow = new FloatingCameraWindow(mContext);

    mFaceLandmarkPaint = new Paint();
    mFaceLandmarkPaint.setColor(Color.GREEN);
    mFaceLandmarkPaint.setStrokeWidth(2);
    mFaceLandmarkPaint.setStyle(Paint.Style.STROKE);
  }

  public void deInitialize() {
    synchronized (OnGetImageListener.this) {
      if (mFaceDet != null) {
        mFaceDet.release();
      }

      if (mPedestrianDet != null) {
        mPedestrianDet.release();
      }

      if (mWindow != null) {
        mWindow.release();
      }
    }
  }

  private void drawResizedBitmap(final Bitmap src, final Bitmap dst) {
    Display getOrient = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
    int orientation = Configuration.ORIENTATION_UNDEFINED;
    Point point = new Point();
    getOrient.getSize(point);
    int screen_width = point.x;
    int screen_height = point.y;
    Log.d(TAG, String.format("screen size (%d,%d)", screen_width, screen_height));
    if (screen_width < screen_height) {
      orientation = Configuration.ORIENTATION_PORTRAIT;
      mScreenRotation = 90;
    } else {
      orientation = Configuration.ORIENTATION_LANDSCAPE;
      mScreenRotation = 0;
    }

    Assert.assertEquals(dst.getWidth(), dst.getHeight());
    final float minDim = Math.min(src.getWidth(), src.getHeight());

    final Matrix matrix = new Matrix();

    // We only want the center square out of the original rectangle.
    final float translateX = -Math.max(0, (src.getWidth() - minDim) / 2);
    final float translateY = -Math.max(0, (src.getHeight() - minDim) / 2);
    matrix.preTranslate(translateX, translateY);

    final float scaleFactor = dst.getHeight() / minDim;
    matrix.postScale(scaleFactor, scaleFactor);

    // Rotate around the center if necessary.
    if (mScreenRotation != 0) {
      matrix.postTranslate(-dst.getWidth() / 2.0f, -dst.getHeight() / 2.0f);
      matrix.postRotate(mScreenRotation);
      matrix.postTranslate(dst.getWidth() / 2.0f, dst.getHeight() / 2.0f);
    }

    final Canvas canvas = new Canvas(dst);
    canvas.drawBitmap(src, matrix, null);
  }

  @Override
  public void onImageAvailable(final ImageReader reader) {
    Image image = null;
    try {
      image = reader.acquireLatestImage();
      Log.d(TAG, "onImageAvailable: image = " + image);

      if (image == null) {
        return;
      }

      // No mutex needed as this method is not reentrant.
      if (mIsComputing) {
        image.close();
        return;
      }
      mIsComputing = true;

      Trace.beginSection("imageAvailable");

      final Plane[] planes = image.getPlanes();

      // Initialize the storage bitmaps once when the resolution is known.
      if (mPreviewWidth != image.getWidth() || mPreviewHeight != image.getHeight()) {
        mPreviewWidth = image.getWidth();
        mPreviewHeight = image.getHeight();

        Log.d(TAG, String.format("Initializing at size %dx%d", mPreviewWidth, mPreviewHeight));
        mRGBBytes = new int[mPreviewWidth * mPreviewHeight];
        mRGBFrameBitmap = Bitmap.createBitmap(mPreviewWidth, mPreviewHeight, Config.ARGB_8888);
        mCroppedBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Config.ARGB_8888);

        mYUVBytes = new byte[planes.length][];
        for (int i = 0; i < planes.length; ++i) {
          mYUVBytes[i] = new byte[planes[i].getBuffer().capacity()];
        }
      }

      for (int i = 0; i < planes.length; ++i) {
        planes[i].getBuffer().get(mYUVBytes[i]);
      }

      final int yRowStride = planes[0].getRowStride();
      final int uvRowStride = planes[1].getRowStride();
      final int uvPixelStride = planes[1].getPixelStride();
      ImageUtils.convertYUV420ToARGB8888(
          mYUVBytes[0],
          mYUVBytes[1],
          mYUVBytes[2],
          mRGBBytes,
          mPreviewWidth,
          mPreviewHeight,
          yRowStride,
          uvRowStride,
          uvPixelStride,
          false);

      image.close();
    } catch (final Exception e) {
      if (image != null) {
        image.close();
      }
      Log.e(TAG, "Exception!", e);
      Trace.endSection();
      return;
    }

    mRGBFrameBitmap.setPixels(mRGBBytes, 0, mPreviewWidth, 0, 0, mPreviewWidth, mPreviewHeight);
    drawResizedBitmap(mRGBFrameBitmap, mCroppedBitmap);
    //mCroppedBitmap = mRGBFrameBitmap;

    if (SAVE_PREVIEW_BITMAP) {
      ImageUtils.saveBitmap(mActivity, mCroppedBitmap);
    }

    mInferenceHandler.post(
        new Runnable() {
          @Override
          public void run() {
            if (!new File(Constants.getFaceShapeModelPath(mContext)).exists()) {
              mTransparentTitleView.setText("Copying landmark model to " + Constants.getFaceShapeModelPath(mContext));
              //FileUtils.copyFileFromRawToOthers(mContext, R.raw.shape_predictor_68_face_landmarks, Constants.getFaceShapeModelPath(mContext));
              FileUtils.copyFileFromRawToOthers(mContext, R.raw.shape_predictor_68_face_landmarks2, Constants.getFaceShapeModelPath(mContext));
            }

            long startTime = System.currentTimeMillis();
            List<VisionDetRet> results;
            synchronized (OnGetImageListener.this) {
              results = mFaceDet.detect(mCroppedBitmap);
            }
            long endTime = System.currentTimeMillis();
            mTransparentTitleView.setText("Time cost: " + String.valueOf((endTime - startTime) / 1000f) + " sec");
            Log.d(TAG, "run: results = " + results);
            // Draw on bitmap
            if (results != null && results.size() > 0) {
              for (final VisionDetRet ret : results) {
                float resizeRatio = 1.0f;
                Rect bounds = new Rect();
                bounds.left = (int) (ret.getLeft() * resizeRatio);
                bounds.top = (int) (ret.getTop() * resizeRatio);
                bounds.right = (int) (ret.getRight() * resizeRatio);
                bounds.bottom = (int) (ret.getBottom() * resizeRatio);
                Canvas canvas = new Canvas(mCroppedBitmap);
                canvas.drawRect(bounds, mFaceLandmarkPaint);

                Log.d(TAG, "run: bounds = " + bounds);

                try {
                  mCroppedBitmapFace = Bitmap.createBitmap(mCroppedBitmap, bounds.left, bounds.top,
                      bounds.right - bounds.left + 10, bounds.bottom - bounds.top);
                } catch (Exception e) {
                  Log.e(TAG, "run: ", e);
                }

                mActivity.runOnUiThread(new Runnable() {
                  @Override
                  public void run() {
                    mFaceDetectedListener.onLandmarkDetected(true);
                  }
                });

                // Draw landmark
                ArrayList<Point> landmarks = ret.getFaceLandmarks();
                Log.d(TAG, "run: landmarks = " + landmarks);
                if (landmarks.size() > 0) {
                  for (Point point : landmarks) {
                    int pointX = (int) (point.x * resizeRatio);
                    int pointY = (int) (point.y * resizeRatio);
                    canvas.drawCircle(pointX, pointY, 2, mFaceLandmarkPaint);
                  }
                  /*mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                      mFaceDetectedListener.onLandmarkDetected(true);
                    }
                  });*/
                } else {
                  /*mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                      mFaceDetectedListener.onLandmarkDetected(false);
                    }
                  });*/
                }
              }

              //ImageUtils.saveBitmap(mActivity, mRGBFrameBitmap);
              //mFaceDetectedListener.onFaceDetected(mRGBFrameBitmap);
            } else {
              mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                  mFaceDetectedListener.onLandmarkDetected(false);
                }
              });
            }

            mWindow.setRGBBitmap(mCroppedBitmap);
            mIsComputing = false;
          }
        });

    Trace.endSection();
  }

  public void saveImage() {
    Thread thread = new Thread() {
      @Override
      public void run() {
        try {
          Matrix matrix = new Matrix();
          matrix.postRotate(90);
          Bitmap rotatedBitmap = Bitmap.createBitmap(mRGBFrameBitmap, 0, 0,
              mRGBFrameBitmap.getWidth(), mRGBFrameBitmap.getHeight(), matrix, true);
          Bitmap resizedBitmap = Bitmap.createScaledBitmap(rotatedBitmap, 640, 960, false);
          final File file = ImageUtils.saveBitmap(mActivity, resizedBitmap);
          mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              mFaceDetectedListener.onFaceDetected(file.getAbsolutePath());
            }
          });
          mIsComputing = true;
        } catch (Exception e) {
          Log.e(TAG, "run: ", e);
        }
      }
    };
    thread.start();
  }

  public void saveFaceImage() {
    Thread thread = new Thread() {
      @Override
      public void run() {
        try {
          /*Matrix matrix = new Matrix();
          matrix.postRotate(90);
          Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
              bitmap.getWidth(), bitmap.getHeight(), matrix, true);
          Bitmap resizedBitmap = Bitmap.createScaledBitmap(rotatedBitmap, 640, 960, false);*/
          final File file = ImageUtils.saveBitmap(mActivity, mCroppedBitmapFace);
          mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              mFaceDetectedListener.onFaceDetected(file.getAbsolutePath());
            }
          });
          mIsComputing = true;
        } catch (Exception e) {
          Log.e(TAG, "run: ", e);
        }
      }
    };
    thread.start();
  }
}
