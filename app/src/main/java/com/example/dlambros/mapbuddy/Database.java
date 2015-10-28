package com.example.dlambros.mapbuddy;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by dlambros on 10/5/15.
 */
public class Database extends SQLiteOpenHelper
{
    // Name of the Database
    public static final String DB_NAME = "Locations.db";

    // Name of the table
    public static final String TABLE_NAME = "location_table";

    // Labels on the Columns of the table
    public static final String COL_1 = "ID";
    public static final String COL_2 = "LONGITUDE";
    public static final String COL_3 = "LATITUDE";
    public static final String COL_4 = "ADDRESS";

    // Constructor for the Database
    public Database(Context context)
    {
        super(context, DB_NAME, null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
        // Define the structure of the database table
        db.execSQL("CREATE TABLE " + TABLE_NAME + " (" + COL_1 + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COL_2 + " TEXT," + COL_3 + " TEXT," + COL_4 + " TEXT )");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        // If the table exists, remove it.
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);

        // Create database table.
        onCreate(db);
    }

    /**
     * Inserts data into the database
     * @param longitude - Longitude Value to be inserted.
     * @param latitude - Latitude Value to be inserted.
     * @param address - Address Value to be inserted.
     * @return true if the row insertion succeeded or false otherwise
     */
    public boolean insertData(String longitude, String latitude, String address)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(COL_2, longitude);
        contentValues.put(COL_3, latitude);
        contentValues.put(COL_4, address);
        long result = db.insert(TABLE_NAME, null, contentValues);
        if (result == -1)
            return false;
        else
            return true;
    }

    /**
     * The function getAllData() will query the table for all entries.
     * @return - Will return a Cursor containing a rawQuery of all entries.
     */
    public Cursor getAllData()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor res = db.rawQuery("select * from " + TABLE_NAME, null);
        return res;
    }

    public void deleteAllData()
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_NAME, null, null);
    }
}
