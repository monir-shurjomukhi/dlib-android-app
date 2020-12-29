package com.tzutalin.dlibtest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class FaceDetectClient {

  private static final String FACE_DETECT_BASE_URL = "http://34.71.57.168:8000/";
  //private static final String FACE_DETECT_BASE_URL = "http://192.168.68.147:8845/";
  private static FaceDetectClient retrofitClient;
  private Retrofit retrofit;

  private FaceDetectClient() {
    OkHttpClient okHttpClient = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(50, TimeUnit.SECONDS)
        .readTimeout(50, TimeUnit.SECONDS)
        .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
        .build();

    Gson gson = new GsonBuilder()
        .create();

    retrofit = new Retrofit.Builder()
        .baseUrl(FACE_DETECT_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build();
  }

  public static synchronized FaceDetectClient getInstance() {
    if (retrofitClient == null) {
      retrofitClient = new FaceDetectClient();
    }
    return retrofitClient;
  }

  public FaceDetectInterface getApi() {
    return retrofit.create(FaceDetectInterface.class);
  }
}
