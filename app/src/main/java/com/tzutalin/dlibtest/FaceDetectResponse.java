package com.tzutalin.dlibtest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class FaceDetectResponse {

  @SerializedName("message")
  @Expose
  public String message;
  @SerializedName("predictions")
  @Expose
  public Predictions predictions;

  @Override
  public String toString() {
    return "FaceDetectResponse{" +
        "message='" + message + '\'' +
        ", predictions=" + predictions +
        '}';
  }
}
