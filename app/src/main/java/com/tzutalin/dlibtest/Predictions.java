package com.tzutalin.dlibtest;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Predictions {

  @SerializedName("detection_class")
  @Expose
  public String detectionClass;

  @SerializedName("distance_error_rate")
  @Expose
  public float distanceErrorRate;

  @Override
  public String toString() {
    return "Predictions{" +
        "detectionClass='" + detectionClass + '\'' +
        ", distanceErrorRate=" + distanceErrorRate +
        '}';
  }
}
