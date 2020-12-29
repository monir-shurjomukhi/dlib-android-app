package com.tzutalin.dlibtest;

import android.graphics.Bitmap;

import java.io.File;

public interface OnFaceDetectedListener {
  public void onFaceDetected(String filePath);
  public void onLandmarkDetected(boolean isDetected);
}
