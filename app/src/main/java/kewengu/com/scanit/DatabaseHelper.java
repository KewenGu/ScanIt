package kewengu.com.scanit;

/**
 * Created by kewen on 4/24/2016.
 */

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import kewengu.com.scanit.DatabaseSchema.Table;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final int VERSION = 1;
    private static final String NAME = "documents.db";

    public DatabaseHelper(Context context){
        super(context,NAME,null,VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("create table "+ Table.NAME+"("+"_id integer primary key autoincrement, "+ Table.Cols.CREATE_TIME+", "+ Table.Cols.CONTENT +")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
