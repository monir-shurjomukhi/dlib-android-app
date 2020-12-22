package com.tzutalin.dlib;

import android.os.Environment;
import android.util.Log;

import java.io.File;

/**
 * Created by darrenl on 2016/4/22.
 */
public final class Constants {
    private static final String TAG = "Constants";

    private Constants() {
        // Constants should be private
    }

    /**
     * getFaceShapeModelPath
     * @return default face shape model path
     */
    public static String getFaceShapeModelPath() {
        File sdcard = Environment.getExternalStorageDirectory();
        String targetPath = sdcard.getAbsolutePath() + File.separator + "shape_predictor_68_face_landmarks.dat";
        //String targetPath = sdcard.getAbsolutePath() + File.separator + "shape_predictor_68_face_landmarks2.dat";
        Log.d(TAG, "getFaceShapeModelPath: targetPath = " + targetPath);
        return targetPath;
    }
}
