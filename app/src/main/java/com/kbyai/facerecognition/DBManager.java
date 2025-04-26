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
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/** @noinspection ALL*/
public class DBManager extends SQLiteOpenHelper {

    public static ArrayList<Person> personList = new ArrayList<>();
    private static final String SHEET_URL = "https://script.google.com/macros/s/AKfycbyIGwfo8vz0EK5A8Ic6JkSsY15dxZ9gmapl32RmKCFwLfqjHJrnSAI_0jSLmGREEoKm/exec";

    public DBManager(Context context) {
        super(context, "mydb", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "create table person " +
                        "(name text, face blob, templates blob)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS person");
        onCreate(db);
    }

    public void insertPerson(String name, Bitmap face, byte[] templates) {
        // Compress face image into a byte array
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        face.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] faceJpg = byteArrayOutputStream.toByteArray();

        // Encode face and templates into Base64 strings
        String encodedFace = Base64.encodeToString(faceJpg, Base64.DEFAULT);
        String encodedTemplates = Base64.encodeToString(templates, Base64.DEFAULT);

        // Log the data to verify it's being prepared correctly
        Log.d("DBManager", "Sending data to Google Sheet:\nName: " + name + "\nFace: " + encodedFace + "\nTemplates: " + encodedTemplates);


        // Insert person into the local SQLite database
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put("name", name);
        contentValues.put("face", faceJpg);
        contentValues.put("templates", templates);
        db.insert("person", null, contentValues);

        // Add the person to the personList
        personList.add(new Person(name, face, templates));

        // Send to Google Sheets
        sendDataToGoogleSheet(name, face, templates);
    }


    public void deletePerson(String name) {
        for (int i = 0; i < personList.size(); i++) {
            if (personList.get(i).name.equals(name)) {
                personList.remove(i);
                i--;
            }
        }

        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("person", "name = ?", new String[]{name});
    }

    public Integer clearDB() {
        personList.clear();
        SQLiteDatabase db = this.getWritableDatabase();
        db.execSQL("delete from person");
        return 0;
    }

    public void loadPerson() {
        personList.clear();

        SQLiteDatabase db = this.getReadableDatabase();
        @SuppressLint("Recycle") Cursor res = db.rawQuery("select * from person", null);
        res.moveToFirst();

        while (!res.isAfterLast()) {
            @SuppressLint("Range") String name = res.getString(res.getColumnIndex("name"));
            @SuppressLint("Range") byte[] faceJpg = res.getBlob(res.getColumnIndex("face"));
            @SuppressLint("Range") byte[] templates = res.getBlob(res.getColumnIndex("templates"));
            Bitmap face = BitmapFactory.decodeByteArray(faceJpg, 0, faceJpg.length);

            Person person = new Person(name, face, templates);
            personList.add(person);
            res.moveToNext();
        }
    }

    // Send to Google Sheets
    @SuppressLint("StaticFieldLeak")
    private void sendDataToGoogleSheet(String name, Bitmap face, byte[] templates) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        face.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] faceBytes = byteArrayOutputStream.toByteArray();
        String encodedFace = Base64.encodeToString(faceBytes, Base64.DEFAULT);
        String encodedTemplates = Base64.encodeToString(templates, Base64.DEFAULT);

        String postData = "name=" + name + "&face=" + encodedFace + "&templates=" + encodedTemplates;

        new AsyncTask<String, Void, String>() {
            @Override
            protected String doInBackground(String... params) {
                try {
                    URL url = new URL(SHEET_URL);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setRequestMethod("POST");
                    urlConnection.setDoOutput(true);
                    urlConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

                    OutputStream os = urlConnection.getOutputStream();
                    os.write(postData.getBytes());
                    os.flush();

                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                    return readStream(in);
                } catch (IOException e) {
                    e.printStackTrace();
                    return "Error sending data";
                }
            }
        }.execute(postData);
    }

    // Fetch from Google Sheets (example)
    public void fetchDataFromGoogleSheet() {
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                try {
                    URL url = new URL(SHEET_URL);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setRequestMethod("GET");

                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());
                    return readStream(in);
                } catch (IOException e) {
                    e.printStackTrace();
                    return "Error fetching data";
                }
            }

            @Override
            protected void onPostExecute(String result) {
                Log.d("GoogleSheets", "Data retrieved: " + result);
                // TODO: Parse JSON and update personList if needed
            }
        }.execute();
    }

    // Utility: Read InputStream into String
    private String readStream(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = in.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, len));
        }
        return sb.toString();
    }
}
