package kewengu.com.scanit;

import android.database.Cursor;
import android.database.CursorWrapper;

/**
 * Created by kewen on 4/25/2016.
 */
public class DocumentCursorWrapper extends CursorWrapper {
    public DocumentCursorWrapper(Cursor cursor) { super(cursor); }

    public Document getDocument() {
        String createTime = getString(getColumnIndex(DatabaseSchema.Table.Cols.CREATE_TIME));
        String content = getString(getColumnIndex(DatabaseSchema.Table.Cols.CONTENT));

        Document document = new Document(createTime, content);

        return document;
    }
}
