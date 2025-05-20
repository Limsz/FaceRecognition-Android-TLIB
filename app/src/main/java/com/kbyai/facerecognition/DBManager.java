package com.kbyai.facerecognition;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

public class DBManager extends SQLiteOpenHelper {
//a
    //a
    public static ArrayList<Person> personList = new ArrayList<>();
    private static final String WEB_APP_URL = "https://script.google.com/macros/s/AKfycbwe381E2BpLjWp3Ee9XrMlzTR82dsXk8AgG5BSjT6Cdb9KiV6cmnE5whyU6KPMW3X2G/exec";

    // Constants for better maintainability
    private static final String TABLE_PERSON = "person";
    private static final String COL_NAME = "name";
    private static final String COL_FACE = "face";
    private static final String COL_TEMPLATES = "templates";
    private static final String TAG = "DBManager";
    private static final int CONNECTION_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 15000;

    public DBManager(Context context) {
        super(context, "mydb", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + TABLE_PERSON + " (" +
                        COL_NAME + " TEXT, " +
                        COL_FACE + " BLOB, " +
                        COL_TEMPLATES + " BLOB)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PERSON);
        onCreate(db);
    }

    public void insertPerson(String name, Bitmap face, byte[] templates) {
        // Compress face image (use JPEG for smaller size)
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        face.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
        byte[] faceBytes = byteArrayOutputStream.toByteArray();

        // Insert into local database
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(COL_NAME, name);
        contentValues.put(COL_FACE, faceBytes);
        contentValues.put(COL_TEMPLATES, templates);
        db.insert(TABLE_PERSON, null, contentValues);

        // Add to memory list
        personList.add(new Person(name, face, templates));

        // Upload to Google Drive
        uploadToGoogleDrive(name, face, templates);
    }

    public void deletePerson(String name) {
        // Remove from memory list
        for (int i = 0; i < personList.size(); i++) {
            if (personList.get(i).name.equals(name)) {
                personList.remove(i);
                break;
            }
        }

        // Remove from database
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_PERSON, COL_NAME + " = ?", new String[]{name});
    }

    public void clearDB() {
        personList.clear();
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("DELETE FROM " + TABLE_PERSON);
    }

    public void loadPerson() {
        personList.clear();
        SQLiteDatabase db = this.getReadableDatabase();

        try (Cursor res = db.rawQuery("SELECT * FROM " + TABLE_PERSON, null)) {
            while (res.moveToNext()) {
                @SuppressLint("Range") String name = res.getString(res.getColumnIndex(COL_NAME));
                @SuppressLint("Range") byte[] faceJpg = res.getBlob(res.getColumnIndex(COL_FACE));
                @SuppressLint("Range") byte[] templates = res.getBlob(res.getColumnIndex(COL_TEMPLATES));

                Bitmap face = BitmapFactory.decodeByteArray(faceJpg, 0, faceJpg.length);
                personList.add(new Person(name, face, templates));
            }
        }
    }

    @SuppressLint("StaticFieldLeak")
    private void uploadToGoogleDrive(String name, Bitmap face, byte[] templates) {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                try {
                    // Prepare image data with proper MIME type
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    face.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
                    String encodedFace = "data:image/jpeg;base64," +
                            Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.NO_WRAP);

                    // Prepare templates
                    String encodedTemplates = Base64.encodeToString(templates, Base64.NO_WRAP);

                    // Create POST data
                    String postData = "action=uploadFaceData" +
                            "&name=" + URLEncoder.encode(name, "UTF-8") +
                            "&face=" + URLEncoder.encode(encodedFace, "UTF-8") +
                            "&templates=" + URLEncoder.encode(encodedTemplates, "UTF-8");

                    // Configure connection
                    HttpURLConnection conn = (HttpURLConnection) new URL(WEB_APP_URL).openConnection();
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    conn.setConnectTimeout(CONNECTION_TIMEOUT);
                    conn.setReadTimeout(READ_TIMEOUT);

                    // Send data
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(postData.getBytes("UTF-8"));
                    }

                    // Handle response
                    if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
                            return readStream(in);
                        }
                    } else {
                        return "Error: HTTP " + conn.getResponseCode();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Upload error", e);
                    return "Error: " + e.getMessage();
                }
            }

            @Override
            protected void onPostExecute(String result) {
                if (result.contains("\"status\":\"success\"")) {
                    Log.i(TAG, "Upload successful: " + result);
                } else {
                    Log.e(TAG, "Upload failed: " + result);
                }
            }
        }.execute();
    }

    private String readStream(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = in.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, len, "UTF-8"));
        }
        return sb.toString();
    }
}