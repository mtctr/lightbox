package com.lightbox.batch.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class PhotoDao_Impl implements PhotoDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<PhotoEntity> __insertionAdapterOfPhotoEntity;

  private final EntityDeletionOrUpdateAdapter<PhotoEntity> __updateAdapterOfPhotoEntity;

  private final SharedSQLiteStatement __preparedStmtOfSetAllSelected;

  private final SharedSQLiteStatement __preparedStmtOfSetSelected;

  private final SharedSQLiteStatement __preparedStmtOfMarkProcessed;

  private final SharedSQLiteStatement __preparedStmtOfMarkExported;

  private final SharedSQLiteStatement __preparedStmtOfSetAutoCorrectForBatch;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByBatch;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAllPhotos;

  public PhotoDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfPhotoEntity = new EntityInsertionAdapter<PhotoEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `photos` (`id`,`uri`,`fileName`,`fileSize`,`width`,`height`,`dateTaken`,`dateImported`,`batchId`,`thumbnailPath`,`isSelected`,`isProcessed`,`isExported`,`appliedPresetId`,`autoCorrectApplied`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PhotoEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getUri());
        statement.bindString(3, entity.getFileName());
        statement.bindLong(4, entity.getFileSize());
        statement.bindLong(5, entity.getWidth());
        statement.bindLong(6, entity.getHeight());
        statement.bindLong(7, entity.getDateTaken());
        statement.bindLong(8, entity.getDateImported());
        statement.bindString(9, entity.getBatchId());
        if (entity.getThumbnailPath() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getThumbnailPath());
        }
        final int _tmp = entity.isSelected() ? 1 : 0;
        statement.bindLong(11, _tmp);
        final int _tmp_1 = entity.isProcessed() ? 1 : 0;
        statement.bindLong(12, _tmp_1);
        final int _tmp_2 = entity.isExported() ? 1 : 0;
        statement.bindLong(13, _tmp_2);
        if (entity.getAppliedPresetId() == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, entity.getAppliedPresetId());
        }
        final int _tmp_3 = entity.getAutoCorrectApplied() ? 1 : 0;
        statement.bindLong(15, _tmp_3);
      }
    };
    this.__updateAdapterOfPhotoEntity = new EntityDeletionOrUpdateAdapter<PhotoEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `photos` SET `id` = ?,`uri` = ?,`fileName` = ?,`fileSize` = ?,`width` = ?,`height` = ?,`dateTaken` = ?,`dateImported` = ?,`batchId` = ?,`thumbnailPath` = ?,`isSelected` = ?,`isProcessed` = ?,`isExported` = ?,`appliedPresetId` = ?,`autoCorrectApplied` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final PhotoEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getUri());
        statement.bindString(3, entity.getFileName());
        statement.bindLong(4, entity.getFileSize());
        statement.bindLong(5, entity.getWidth());
        statement.bindLong(6, entity.getHeight());
        statement.bindLong(7, entity.getDateTaken());
        statement.bindLong(8, entity.getDateImported());
        statement.bindString(9, entity.getBatchId());
        if (entity.getThumbnailPath() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getThumbnailPath());
        }
        final int _tmp = entity.isSelected() ? 1 : 0;
        statement.bindLong(11, _tmp);
        final int _tmp_1 = entity.isProcessed() ? 1 : 0;
        statement.bindLong(12, _tmp_1);
        final int _tmp_2 = entity.isExported() ? 1 : 0;
        statement.bindLong(13, _tmp_2);
        if (entity.getAppliedPresetId() == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, entity.getAppliedPresetId());
        }
        final int _tmp_3 = entity.getAutoCorrectApplied() ? 1 : 0;
        statement.bindLong(15, _tmp_3);
        statement.bindLong(16, entity.getId());
      }
    };
    this.__preparedStmtOfSetAllSelected = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE photos SET isSelected = ? WHERE batchId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetSelected = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE photos SET isSelected = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfMarkProcessed = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE photos SET isProcessed = 1, appliedPresetId = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfMarkExported = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE photos SET isExported = 1 WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetAutoCorrectForBatch = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE photos SET autoCorrectApplied = ? WHERE batchId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteByBatch = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM photos WHERE batchId = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAllPhotos = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM photos";
        return _query;
      }
    };
  }

  @Override
  public Object insertAll(final List<PhotoEntity> photos,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfPhotoEntity.insert(photos);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insert(final PhotoEntity photo, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfPhotoEntity.insertAndReturnId(photo);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final PhotoEntity photo, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfPhotoEntity.handle(photo);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object setAllSelected(final String batchId, final boolean selected,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetAllSelected.acquire();
        int _argIndex = 1;
        final int _tmp = selected ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindString(_argIndex, batchId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetAllSelected.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setSelected(final long id, final boolean selected,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetSelected.acquire();
        int _argIndex = 1;
        final int _tmp = selected ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetSelected.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object markProcessed(final long id, final String presetId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkProcessed.acquire();
        int _argIndex = 1;
        if (presetId == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, presetId);
        }
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfMarkProcessed.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object markExported(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfMarkExported.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfMarkExported.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setAutoCorrectForBatch(final String batchId, final boolean applied,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetAutoCorrectForBatch.acquire();
        int _argIndex = 1;
        final int _tmp = applied ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindString(_argIndex, batchId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetAutoCorrectForBatch.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteByBatch(final String batchId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteByBatch.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, batchId);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteByBatch.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAllPhotos(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAllPhotos.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAllPhotos.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<PhotoEntity>> getPhotosByBatch(final String batchId) {
    final String _sql = "SELECT * FROM photos WHERE batchId = ? ORDER BY dateImported DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, batchId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"photos"}, new Callable<List<PhotoEntity>>() {
      @Override
      @NonNull
      public List<PhotoEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUri = CursorUtil.getColumnIndexOrThrow(_cursor, "uri");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfFileSize = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSize");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfDateTaken = CursorUtil.getColumnIndexOrThrow(_cursor, "dateTaken");
          final int _cursorIndexOfDateImported = CursorUtil.getColumnIndexOrThrow(_cursor, "dateImported");
          final int _cursorIndexOfBatchId = CursorUtil.getColumnIndexOrThrow(_cursor, "batchId");
          final int _cursorIndexOfThumbnailPath = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailPath");
          final int _cursorIndexOfIsSelected = CursorUtil.getColumnIndexOrThrow(_cursor, "isSelected");
          final int _cursorIndexOfIsProcessed = CursorUtil.getColumnIndexOrThrow(_cursor, "isProcessed");
          final int _cursorIndexOfIsExported = CursorUtil.getColumnIndexOrThrow(_cursor, "isExported");
          final int _cursorIndexOfAppliedPresetId = CursorUtil.getColumnIndexOrThrow(_cursor, "appliedPresetId");
          final int _cursorIndexOfAutoCorrectApplied = CursorUtil.getColumnIndexOrThrow(_cursor, "autoCorrectApplied");
          final List<PhotoEntity> _result = new ArrayList<PhotoEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PhotoEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpUri;
            _tmpUri = _cursor.getString(_cursorIndexOfUri);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final long _tmpFileSize;
            _tmpFileSize = _cursor.getLong(_cursorIndexOfFileSize);
            final int _tmpWidth;
            _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            final int _tmpHeight;
            _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            final long _tmpDateTaken;
            _tmpDateTaken = _cursor.getLong(_cursorIndexOfDateTaken);
            final long _tmpDateImported;
            _tmpDateImported = _cursor.getLong(_cursorIndexOfDateImported);
            final String _tmpBatchId;
            _tmpBatchId = _cursor.getString(_cursorIndexOfBatchId);
            final String _tmpThumbnailPath;
            if (_cursor.isNull(_cursorIndexOfThumbnailPath)) {
              _tmpThumbnailPath = null;
            } else {
              _tmpThumbnailPath = _cursor.getString(_cursorIndexOfThumbnailPath);
            }
            final boolean _tmpIsSelected;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSelected);
            _tmpIsSelected = _tmp != 0;
            final boolean _tmpIsProcessed;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsProcessed);
            _tmpIsProcessed = _tmp_1 != 0;
            final boolean _tmpIsExported;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsExported);
            _tmpIsExported = _tmp_2 != 0;
            final String _tmpAppliedPresetId;
            if (_cursor.isNull(_cursorIndexOfAppliedPresetId)) {
              _tmpAppliedPresetId = null;
            } else {
              _tmpAppliedPresetId = _cursor.getString(_cursorIndexOfAppliedPresetId);
            }
            final boolean _tmpAutoCorrectApplied;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfAutoCorrectApplied);
            _tmpAutoCorrectApplied = _tmp_3 != 0;
            _item = new PhotoEntity(_tmpId,_tmpUri,_tmpFileName,_tmpFileSize,_tmpWidth,_tmpHeight,_tmpDateTaken,_tmpDateImported,_tmpBatchId,_tmpThumbnailPath,_tmpIsSelected,_tmpIsProcessed,_tmpIsExported,_tmpAppliedPresetId,_tmpAutoCorrectApplied);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getPhotosByBatchList(final String batchId,
      final Continuation<? super List<PhotoEntity>> $completion) {
    final String _sql = "SELECT * FROM photos WHERE batchId = ? ORDER BY dateImported DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, batchId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<PhotoEntity>>() {
      @Override
      @NonNull
      public List<PhotoEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUri = CursorUtil.getColumnIndexOrThrow(_cursor, "uri");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfFileSize = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSize");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfDateTaken = CursorUtil.getColumnIndexOrThrow(_cursor, "dateTaken");
          final int _cursorIndexOfDateImported = CursorUtil.getColumnIndexOrThrow(_cursor, "dateImported");
          final int _cursorIndexOfBatchId = CursorUtil.getColumnIndexOrThrow(_cursor, "batchId");
          final int _cursorIndexOfThumbnailPath = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailPath");
          final int _cursorIndexOfIsSelected = CursorUtil.getColumnIndexOrThrow(_cursor, "isSelected");
          final int _cursorIndexOfIsProcessed = CursorUtil.getColumnIndexOrThrow(_cursor, "isProcessed");
          final int _cursorIndexOfIsExported = CursorUtil.getColumnIndexOrThrow(_cursor, "isExported");
          final int _cursorIndexOfAppliedPresetId = CursorUtil.getColumnIndexOrThrow(_cursor, "appliedPresetId");
          final int _cursorIndexOfAutoCorrectApplied = CursorUtil.getColumnIndexOrThrow(_cursor, "autoCorrectApplied");
          final List<PhotoEntity> _result = new ArrayList<PhotoEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PhotoEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpUri;
            _tmpUri = _cursor.getString(_cursorIndexOfUri);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final long _tmpFileSize;
            _tmpFileSize = _cursor.getLong(_cursorIndexOfFileSize);
            final int _tmpWidth;
            _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            final int _tmpHeight;
            _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            final long _tmpDateTaken;
            _tmpDateTaken = _cursor.getLong(_cursorIndexOfDateTaken);
            final long _tmpDateImported;
            _tmpDateImported = _cursor.getLong(_cursorIndexOfDateImported);
            final String _tmpBatchId;
            _tmpBatchId = _cursor.getString(_cursorIndexOfBatchId);
            final String _tmpThumbnailPath;
            if (_cursor.isNull(_cursorIndexOfThumbnailPath)) {
              _tmpThumbnailPath = null;
            } else {
              _tmpThumbnailPath = _cursor.getString(_cursorIndexOfThumbnailPath);
            }
            final boolean _tmpIsSelected;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSelected);
            _tmpIsSelected = _tmp != 0;
            final boolean _tmpIsProcessed;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsProcessed);
            _tmpIsProcessed = _tmp_1 != 0;
            final boolean _tmpIsExported;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsExported);
            _tmpIsExported = _tmp_2 != 0;
            final String _tmpAppliedPresetId;
            if (_cursor.isNull(_cursorIndexOfAppliedPresetId)) {
              _tmpAppliedPresetId = null;
            } else {
              _tmpAppliedPresetId = _cursor.getString(_cursorIndexOfAppliedPresetId);
            }
            final boolean _tmpAutoCorrectApplied;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfAutoCorrectApplied);
            _tmpAutoCorrectApplied = _tmp_3 != 0;
            _item = new PhotoEntity(_tmpId,_tmpUri,_tmpFileName,_tmpFileSize,_tmpWidth,_tmpHeight,_tmpDateTaken,_tmpDateImported,_tmpBatchId,_tmpThumbnailPath,_tmpIsSelected,_tmpIsProcessed,_tmpIsExported,_tmpAppliedPresetId,_tmpAutoCorrectApplied);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getPhotoById(final long id, final Continuation<? super PhotoEntity> $completion) {
    final String _sql = "SELECT * FROM photos WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<PhotoEntity>() {
      @Override
      @Nullable
      public PhotoEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUri = CursorUtil.getColumnIndexOrThrow(_cursor, "uri");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfFileSize = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSize");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfDateTaken = CursorUtil.getColumnIndexOrThrow(_cursor, "dateTaken");
          final int _cursorIndexOfDateImported = CursorUtil.getColumnIndexOrThrow(_cursor, "dateImported");
          final int _cursorIndexOfBatchId = CursorUtil.getColumnIndexOrThrow(_cursor, "batchId");
          final int _cursorIndexOfThumbnailPath = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailPath");
          final int _cursorIndexOfIsSelected = CursorUtil.getColumnIndexOrThrow(_cursor, "isSelected");
          final int _cursorIndexOfIsProcessed = CursorUtil.getColumnIndexOrThrow(_cursor, "isProcessed");
          final int _cursorIndexOfIsExported = CursorUtil.getColumnIndexOrThrow(_cursor, "isExported");
          final int _cursorIndexOfAppliedPresetId = CursorUtil.getColumnIndexOrThrow(_cursor, "appliedPresetId");
          final int _cursorIndexOfAutoCorrectApplied = CursorUtil.getColumnIndexOrThrow(_cursor, "autoCorrectApplied");
          final PhotoEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpUri;
            _tmpUri = _cursor.getString(_cursorIndexOfUri);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final long _tmpFileSize;
            _tmpFileSize = _cursor.getLong(_cursorIndexOfFileSize);
            final int _tmpWidth;
            _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            final int _tmpHeight;
            _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            final long _tmpDateTaken;
            _tmpDateTaken = _cursor.getLong(_cursorIndexOfDateTaken);
            final long _tmpDateImported;
            _tmpDateImported = _cursor.getLong(_cursorIndexOfDateImported);
            final String _tmpBatchId;
            _tmpBatchId = _cursor.getString(_cursorIndexOfBatchId);
            final String _tmpThumbnailPath;
            if (_cursor.isNull(_cursorIndexOfThumbnailPath)) {
              _tmpThumbnailPath = null;
            } else {
              _tmpThumbnailPath = _cursor.getString(_cursorIndexOfThumbnailPath);
            }
            final boolean _tmpIsSelected;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSelected);
            _tmpIsSelected = _tmp != 0;
            final boolean _tmpIsProcessed;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsProcessed);
            _tmpIsProcessed = _tmp_1 != 0;
            final boolean _tmpIsExported;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsExported);
            _tmpIsExported = _tmp_2 != 0;
            final String _tmpAppliedPresetId;
            if (_cursor.isNull(_cursorIndexOfAppliedPresetId)) {
              _tmpAppliedPresetId = null;
            } else {
              _tmpAppliedPresetId = _cursor.getString(_cursorIndexOfAppliedPresetId);
            }
            final boolean _tmpAutoCorrectApplied;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfAutoCorrectApplied);
            _tmpAutoCorrectApplied = _tmp_3 != 0;
            _result = new PhotoEntity(_tmpId,_tmpUri,_tmpFileName,_tmpFileSize,_tmpWidth,_tmpHeight,_tmpDateTaken,_tmpDateImported,_tmpBatchId,_tmpThumbnailPath,_tmpIsSelected,_tmpIsProcessed,_tmpIsExported,_tmpAppliedPresetId,_tmpAutoCorrectApplied);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getSelectedPhotos(final String batchId,
      final Continuation<? super List<PhotoEntity>> $completion) {
    final String _sql = "SELECT * FROM photos WHERE batchId = ? AND isSelected = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, batchId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<PhotoEntity>>() {
      @Override
      @NonNull
      public List<PhotoEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUri = CursorUtil.getColumnIndexOrThrow(_cursor, "uri");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfFileSize = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSize");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfDateTaken = CursorUtil.getColumnIndexOrThrow(_cursor, "dateTaken");
          final int _cursorIndexOfDateImported = CursorUtil.getColumnIndexOrThrow(_cursor, "dateImported");
          final int _cursorIndexOfBatchId = CursorUtil.getColumnIndexOrThrow(_cursor, "batchId");
          final int _cursorIndexOfThumbnailPath = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailPath");
          final int _cursorIndexOfIsSelected = CursorUtil.getColumnIndexOrThrow(_cursor, "isSelected");
          final int _cursorIndexOfIsProcessed = CursorUtil.getColumnIndexOrThrow(_cursor, "isProcessed");
          final int _cursorIndexOfIsExported = CursorUtil.getColumnIndexOrThrow(_cursor, "isExported");
          final int _cursorIndexOfAppliedPresetId = CursorUtil.getColumnIndexOrThrow(_cursor, "appliedPresetId");
          final int _cursorIndexOfAutoCorrectApplied = CursorUtil.getColumnIndexOrThrow(_cursor, "autoCorrectApplied");
          final List<PhotoEntity> _result = new ArrayList<PhotoEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PhotoEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpUri;
            _tmpUri = _cursor.getString(_cursorIndexOfUri);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final long _tmpFileSize;
            _tmpFileSize = _cursor.getLong(_cursorIndexOfFileSize);
            final int _tmpWidth;
            _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            final int _tmpHeight;
            _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            final long _tmpDateTaken;
            _tmpDateTaken = _cursor.getLong(_cursorIndexOfDateTaken);
            final long _tmpDateImported;
            _tmpDateImported = _cursor.getLong(_cursorIndexOfDateImported);
            final String _tmpBatchId;
            _tmpBatchId = _cursor.getString(_cursorIndexOfBatchId);
            final String _tmpThumbnailPath;
            if (_cursor.isNull(_cursorIndexOfThumbnailPath)) {
              _tmpThumbnailPath = null;
            } else {
              _tmpThumbnailPath = _cursor.getString(_cursorIndexOfThumbnailPath);
            }
            final boolean _tmpIsSelected;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSelected);
            _tmpIsSelected = _tmp != 0;
            final boolean _tmpIsProcessed;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsProcessed);
            _tmpIsProcessed = _tmp_1 != 0;
            final boolean _tmpIsExported;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsExported);
            _tmpIsExported = _tmp_2 != 0;
            final String _tmpAppliedPresetId;
            if (_cursor.isNull(_cursorIndexOfAppliedPresetId)) {
              _tmpAppliedPresetId = null;
            } else {
              _tmpAppliedPresetId = _cursor.getString(_cursorIndexOfAppliedPresetId);
            }
            final boolean _tmpAutoCorrectApplied;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfAutoCorrectApplied);
            _tmpAutoCorrectApplied = _tmp_3 != 0;
            _item = new PhotoEntity(_tmpId,_tmpUri,_tmpFileName,_tmpFileSize,_tmpWidth,_tmpHeight,_tmpDateTaken,_tmpDateImported,_tmpBatchId,_tmpThumbnailPath,_tmpIsSelected,_tmpIsProcessed,_tmpIsExported,_tmpAppliedPresetId,_tmpAutoCorrectApplied);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getPhotoCount(final String batchId,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM photos WHERE batchId = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, batchId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getExportedCount(final String batchId,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM photos WHERE batchId = ? AND isExported = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, batchId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getRandomSamplePhotos(final String batchId, final int limit,
      final Continuation<? super List<PhotoEntity>> $completion) {
    final String _sql = "SELECT * FROM photos WHERE batchId = ? ORDER BY RANDOM() LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, batchId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<PhotoEntity>>() {
      @Override
      @NonNull
      public List<PhotoEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfUri = CursorUtil.getColumnIndexOrThrow(_cursor, "uri");
          final int _cursorIndexOfFileName = CursorUtil.getColumnIndexOrThrow(_cursor, "fileName");
          final int _cursorIndexOfFileSize = CursorUtil.getColumnIndexOrThrow(_cursor, "fileSize");
          final int _cursorIndexOfWidth = CursorUtil.getColumnIndexOrThrow(_cursor, "width");
          final int _cursorIndexOfHeight = CursorUtil.getColumnIndexOrThrow(_cursor, "height");
          final int _cursorIndexOfDateTaken = CursorUtil.getColumnIndexOrThrow(_cursor, "dateTaken");
          final int _cursorIndexOfDateImported = CursorUtil.getColumnIndexOrThrow(_cursor, "dateImported");
          final int _cursorIndexOfBatchId = CursorUtil.getColumnIndexOrThrow(_cursor, "batchId");
          final int _cursorIndexOfThumbnailPath = CursorUtil.getColumnIndexOrThrow(_cursor, "thumbnailPath");
          final int _cursorIndexOfIsSelected = CursorUtil.getColumnIndexOrThrow(_cursor, "isSelected");
          final int _cursorIndexOfIsProcessed = CursorUtil.getColumnIndexOrThrow(_cursor, "isProcessed");
          final int _cursorIndexOfIsExported = CursorUtil.getColumnIndexOrThrow(_cursor, "isExported");
          final int _cursorIndexOfAppliedPresetId = CursorUtil.getColumnIndexOrThrow(_cursor, "appliedPresetId");
          final int _cursorIndexOfAutoCorrectApplied = CursorUtil.getColumnIndexOrThrow(_cursor, "autoCorrectApplied");
          final List<PhotoEntity> _result = new ArrayList<PhotoEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final PhotoEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpUri;
            _tmpUri = _cursor.getString(_cursorIndexOfUri);
            final String _tmpFileName;
            _tmpFileName = _cursor.getString(_cursorIndexOfFileName);
            final long _tmpFileSize;
            _tmpFileSize = _cursor.getLong(_cursorIndexOfFileSize);
            final int _tmpWidth;
            _tmpWidth = _cursor.getInt(_cursorIndexOfWidth);
            final int _tmpHeight;
            _tmpHeight = _cursor.getInt(_cursorIndexOfHeight);
            final long _tmpDateTaken;
            _tmpDateTaken = _cursor.getLong(_cursorIndexOfDateTaken);
            final long _tmpDateImported;
            _tmpDateImported = _cursor.getLong(_cursorIndexOfDateImported);
            final String _tmpBatchId;
            _tmpBatchId = _cursor.getString(_cursorIndexOfBatchId);
            final String _tmpThumbnailPath;
            if (_cursor.isNull(_cursorIndexOfThumbnailPath)) {
              _tmpThumbnailPath = null;
            } else {
              _tmpThumbnailPath = _cursor.getString(_cursorIndexOfThumbnailPath);
            }
            final boolean _tmpIsSelected;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsSelected);
            _tmpIsSelected = _tmp != 0;
            final boolean _tmpIsProcessed;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsProcessed);
            _tmpIsProcessed = _tmp_1 != 0;
            final boolean _tmpIsExported;
            final int _tmp_2;
            _tmp_2 = _cursor.getInt(_cursorIndexOfIsExported);
            _tmpIsExported = _tmp_2 != 0;
            final String _tmpAppliedPresetId;
            if (_cursor.isNull(_cursorIndexOfAppliedPresetId)) {
              _tmpAppliedPresetId = null;
            } else {
              _tmpAppliedPresetId = _cursor.getString(_cursorIndexOfAppliedPresetId);
            }
            final boolean _tmpAutoCorrectApplied;
            final int _tmp_3;
            _tmp_3 = _cursor.getInt(_cursorIndexOfAutoCorrectApplied);
            _tmpAutoCorrectApplied = _tmp_3 != 0;
            _item = new PhotoEntity(_tmpId,_tmpUri,_tmpFileName,_tmpFileSize,_tmpWidth,_tmpHeight,_tmpDateTaken,_tmpDateImported,_tmpBatchId,_tmpThumbnailPath,_tmpIsSelected,_tmpIsProcessed,_tmpIsExported,_tmpAppliedPresetId,_tmpAutoCorrectApplied);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
