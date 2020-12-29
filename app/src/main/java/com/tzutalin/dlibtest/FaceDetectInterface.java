package com.tzutalin.dlibtest;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface FaceDetectInterface {

  @Multipart
  @POST("api/v1/face_detect/")
  Call<FaceDetectResponse> detectFace(@Part MultipartBody.Part file);

  @Multipart
  @POST("api/v1/img_classify/")
  Call<FaceDetectResponse> imgClassify(
      @Part MultipartBody.Part file,
      @Part("farm_id") RequestBody farmId);
}
