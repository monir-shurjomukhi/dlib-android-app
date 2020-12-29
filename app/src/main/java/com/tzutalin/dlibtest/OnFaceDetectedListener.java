package com.tzutalin.dlibtest;

public interface OnFaceDetectedListener {
  public void onFaceDetected(String filePath);
  public void onLandmarkDetected(boolean isDetected);
}
