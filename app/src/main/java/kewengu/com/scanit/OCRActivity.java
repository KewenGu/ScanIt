package kewengu.com.scanit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.googlecode.tesseract.android.TessBaseAPI;

import org.w3c.dom.Text;

public class OCRActivity extends Activity {
    public static final String DATA_PATH = Environment
            .getExternalStorageDirectory().toString() + "/ScanIt/";

    public static final String lang = "eng";

    private static final String TAG = "ScanIt.java";
    private static final String PHOTO_TAKEN = "photo_taken";

    private Button _button1;
    private Button _button2;
    private Button _button3;
    private Button _button4;
    private Button _button5;
    private EditText _field;
    private TextView _notification;
    private String path;
    private boolean taken;
    private boolean fromLibrary = false;

    private int layer = 0;

    private static SQLiteDatabase database;
    private DataBaseAdapter mdb;
    long mrowID = 0;
    @Override
    public void onCreate(Bundle savedInstanceState) {

        Log.v(TAG, "Creating database...");
        openDB();
        try {
            database = new DatabaseHelper(this).getWritableDatabase();
        }
        catch (SQLiteException e){
            Log.e(TAG, "Failed to create database");
        }
        Log.v(TAG, "Database created");

        String[] paths = new String[] { DATA_PATH, DATA_PATH + "tessdata/" };

        for (String path : paths) {
            File dir = new File(path);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Log.v(TAG, "ERROR: Creation of directory " + path + " on sdcard failed");
                    return;
                } else {
                    Log.v(TAG, "Created directory " + path + " on sdcard");
                }
            }

        }

        // lang.traineddata file with the app (in assets folder)
        // You can get them at:
        // http://code.google.com/p/tesseract-ocr/downloads/list
        // This area needs work and optimization
        if (!(new File(DATA_PATH + "tessdata/" + lang + ".traineddata")).exists()) {
            try {

                AssetManager assetManager = getAssets();
                InputStream in = assetManager.open("tessdata/" + lang + ".traineddata");
                //GZIPInputStream gin = new GZIPInputStream(in);
                OutputStream out = new FileOutputStream(DATA_PATH
                        + "tessdata/" + lang + ".traineddata");

                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                //while ((lenf = gin.read(buff)) > 0) {
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                //gin.close();
                out.close();

                Log.v(TAG, "Copied " + lang + " traineddata");
            } catch (IOException e) {
                Log.e(TAG, "Was unable to copy " + lang + " traineddata " + e.toString());
            }
        }

        super.onCreate(savedInstanceState);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        setContentView(R.layout.activity_main);

        // _image = (ImageView) findViewById(R.id.image);
        _field = (EditText) findViewById(R.id.field);
        _button1 = (Button) findViewById(R.id.button1);
        _button2 = (Button) findViewById(R.id.button2);
        _button3 = (Button) findViewById(R.id.button3);
        _button4 = (Button) findViewById(R.id.button4);
        _button5 = (Button) findViewById(R.id.button5);
        _notification = (TextView) findViewById(R.id.textView);
        _button1.setOnClickListener(new Button1ClickHandler());
        _button2.setOnClickListener(new Button2ClickHandler());
        _button3.setOnClickListener(new Button3ClickHandler());
        _button4.setOnClickListener(new Button4ClickHandler());
        _button5.setOnClickListener(new Button5ClickHandler());


        path = DATA_PATH + "/ocr.jpg";
    }

    // Take a photo
    public class Button1ClickHandler implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            _notification.setText("");
            _field.setText("");
            Log.v(TAG, "Starting Camera app");
            startCameraActivity();
        }
    }

    // Choose from photo gallery
    public class Button2ClickHandler implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            fromLibrary = true;
            _notification.setText("");
            _field.setText("");
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, 0);
        }
    }

    // Browse saved documents
    public class Button3ClickHandler implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            _notification.setText("");
            _field.setText("");
            setContentView(R.layout.document_list);
            layer = 1;
            populateList();



        }
    }

    // Save the recognized text as a document
    public class Button4ClickHandler implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            _notification.setText("");
            Editable text = _field.getText();
            if (text.length() > 1) {
                insertToDatabase(String.valueOf(text));
                _notification.setText("Document Saved");
            }
            else
                _notification.setText("There's nothing to save");
        }
    }

    // Discard the recognized text
    public class Button5ClickHandler implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            _notification.setText("");
            _field.setText("");
        }
    }

    @Override
    public void onBackPressed()
    {
        if (layer == 2) {
            setContentView(R.layout.document_list);
            layer = 1;
            populateList();
        }
        else {
            setContentView(R.layout.activity_main);
            layer = 0;
            Intent i = new Intent(getApplicationContext(), OCRActivity.class);
            startActivity(i);
        }
    }

    protected void startCameraActivity() {
        File file = new File(path);
        Uri outputFileUri = Uri.fromFile(file);

        final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);

        startActivityForResult(intent, 0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        Log.i(TAG, "resultCode: " + resultCode);

        if (resultCode == RESULT_OK) {
            if (fromLibrary) {
                Uri targetUri = data.getData();
                String targetPath = getRealPathFromURI(targetUri);
                Log.v(TAG, "targetURI: " + targetPath);
                File targetImage = new File(targetPath);
                Log.v(TAG, "path: " + path);
                File destImage = new File(path);
                try {
                    copy(targetImage, destImage);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            fromLibrary = false;
            onPhotoTaken();
        } else {
            Log.v(TAG, "User cancelled");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(OCRActivity.PHOTO_TAKEN, taken);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        Log.i(TAG, "onRestoreInstanceState()");
        if (savedInstanceState.getBoolean(OCRActivity.PHOTO_TAKEN)) {
            onPhotoTaken();
        }
    }

    protected void onPhotoTaken() {
        taken = true;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;

        Bitmap bitmap = BitmapFactory.decodeFile(path, options);

        try {
            ExifInterface exif = new ExifInterface(path);
            int exifOrientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);

            Log.v(TAG, "Orient: " + exifOrientation);

            int rotate = 0;

            switch (exifOrientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
            }

            Log.v(TAG, "Rotation: " + rotate);

            if (rotate != 0) {

                // Getting width & height of the given image.
                int w = bitmap.getWidth();
                int h = bitmap.getHeight();

                // Setting pre rotate
                Matrix mtx = new Matrix();
                mtx.preRotate(rotate);

                // Rotating Bitmap
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, false);
            }

            // Convert to ARGB_8888, required by tess
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

        } catch (IOException e) {
            Log.e(TAG, "Couldn't correct orientation: " + e.toString());
        }

        // _image.setImageBitmap( bitmap );

        Log.v(TAG, "Before baseApi");

        TessBaseAPI baseApi = new TessBaseAPI();
        baseApi.setDebug(true);
        baseApi.init(DATA_PATH, lang);
        baseApi.setImage(bitmap);

        String recognizedText = baseApi.getUTF8Text();

        baseApi.end();

        // You now have the text in recognizedText var, you can do anything with it.
        // We will display a stripped out trimmed alpha-numeric version of it (if lang is eng)
        // so that garbage doesn't make it to the display.

        Log.v(TAG, "OCRED TEXT: " + recognizedText);

        if ( lang.equalsIgnoreCase("eng") ) {
            recognizedText = recognizedText.replaceAll("[^a-zA-Z0-9]+", " ");
        }

        recognizedText = recognizedText.trim();

        if ( recognizedText.length() != 0 ) {
            _field.setText(_field.getText().toString().length() == 0 ? recognizedText : _field.getText() + " " + recognizedText);
            _field.setSelection(_field.getText().toString().length());
        }

        // Cycle done.
    }
    //open data base
    private void openDB(){
        mdb = new DataBaseAdapter(this);
        mdb.open();
    }
    // Insert the create time and the content of document into the database
    private void insertToDatabase(String content) {
        Date time = Calendar.getInstance().getTime();
        String timeStr = time.toString();
//        String timeStr = (new SimpleDateFormat("mm/dd/yy,hh:mm:ss")).format(time);
        ContentValues values = getDatabaseValues(timeStr, content);
        if(!TextUtils.isEmpty(content)){
            mdb.insertRow(content,timeStr);
        }
        database.insert(DatabaseSchema.Table.NAME, null, values);
    }

    // Get the content value to insert to the database
    private static ContentValues getDatabaseValues(String time, String content) {
        ContentValues values = new ContentValues();
        values.put(DatabaseSchema.Table.Cols.CREATE_TIME, time);
        values.put(DatabaseSchema.Table.Cols.CONTENT, content);
        return values;
    }


    private DocumentCursorWrapper queryDocument(String whereClause, String[] whereArgs) {
        Cursor cursor = database.query(
                DatabaseSchema.Table.NAME,
                null,
                whereClause,
                whereArgs,
                null,
                null,
                null
        );

        return new DocumentCursorWrapper(cursor);
    }

    private List<Document> getDocuments() {
        List<Document> documents = new ArrayList<Document>();

        DocumentCursorWrapper cursor = queryDocument(null, null);

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            documents.add(cursor.getDocument());
            cursor.moveToNext();
        }
        cursor.close();

        return documents;
    }

    private void populateList(){
        Cursor cursor = mdb.getAllRows();
        String[] fromFieldName = new String[]{mdb.KEY_DATE, mdb.KEY_CONTENT, mdb.KEY_ROWID};
        int[] toViewID = new int[]{R.id.textView2, R.id.textView3, R.id.textView4};
        SimpleCursorAdapter myAdapter = new SimpleCursorAdapter(getBaseContext(),R.layout.document_list_item,cursor,fromFieldName,toViewID,0);
        ListView myList  = (ListView) findViewById(R.id.listView);
        myList.setAdapter(myAdapter);

        myList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                TextView tv2 = (TextView) view.findViewById(R.id.textView2);
                TextView tv3 = (TextView) view.findViewById(R.id.textView3);
                TextView tv4 = (TextView) view.findViewById(R.id.textView4);
                setContentView(R.layout.document_profile);
                layer = 2;
                final TextView tv5 = (TextView) findViewById(R.id.textView5);
                final TextView tv6 = (TextView) findViewById(R.id.textView6);
                tv5.setText(tv2.getText());
                tv6.setText(tv3.getText());
                mrowID = Long.parseLong(tv4.getText().toString());
                Button delete = (Button) findViewById(R.id.delete_button);
                Button share = (Button) findViewById(R.id.share_button);
                delete.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mdb.deleteRow(mrowID);
                        setContentView(R.layout.document_list);
                        populateList();
                    }
                });
                share.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent sendIntent = new Intent();
                        sendIntent.setAction(Intent.ACTION_SEND);
                        sendIntent.putExtra(Intent.EXTRA_TEXT, tv6.getText().toString());
                        sendIntent.setType("text/plain");
                        startActivity(Intent.createChooser(sendIntent, "Share to ..."));
                    }
                });

            }
        });
    }


    public void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);

        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }


    public String getRealPathFromURI(Uri contentUri) {
        String res = null;
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
        if(cursor.moveToFirst()){;
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            res = cursor.getString(column_index);
        }
        cursor.close();
        return res;
    }
}
