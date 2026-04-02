package com.pridalac.radarcapri.`data`

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Double
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class RadarDao_Impl(
  __db: RoomDatabase,
) : RadarDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfSpeedCamera: EntityInsertAdapter<SpeedCamera>
  init {
    this.__db = __db
    this.__insertAdapterOfSpeedCamera = object : EntityInsertAdapter<SpeedCamera>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `speed_cameras` (`id`,`name`,`type`,`lat`,`lon`,`speed`,`city`,`street`) VALUES (?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: SpeedCamera) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.type)
        statement.bindDouble(4, entity.lat)
        statement.bindDouble(5, entity.lon)
        statement.bindLong(6, entity.speed.toLong())
        val _tmpCity: String? = entity.city
        if (_tmpCity == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpCity)
        }
        val _tmpStreet: String? = entity.street
        if (_tmpStreet == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpStreet)
        }
      }
    }
  }

  public override suspend fun insertAll(cameras: List<SpeedCamera>): Unit = performSuspending(__db,
      false, true) { _connection ->
    __insertAdapterOfSpeedCamera.insert(_connection, cameras)
  }

  public override fun getAllCameras(): Flow<List<SpeedCamera>> {
    val _sql: String = "SELECT * FROM speed_cameras"
    return createFlow(__db, false, arrayOf("speed_cameras")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfLat: Int = getColumnIndexOrThrow(_stmt, "lat")
        val _columnIndexOfLon: Int = getColumnIndexOrThrow(_stmt, "lon")
        val _columnIndexOfSpeed: Int = getColumnIndexOrThrow(_stmt, "speed")
        val _columnIndexOfCity: Int = getColumnIndexOrThrow(_stmt, "city")
        val _columnIndexOfStreet: Int = getColumnIndexOrThrow(_stmt, "street")
        val _result: MutableList<SpeedCamera> = mutableListOf()
        while (_stmt.step()) {
          val _item: SpeedCamera
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpLat: Double
          _tmpLat = _stmt.getDouble(_columnIndexOfLat)
          val _tmpLon: Double
          _tmpLon = _stmt.getDouble(_columnIndexOfLon)
          val _tmpSpeed: Int
          _tmpSpeed = _stmt.getLong(_columnIndexOfSpeed).toInt()
          val _tmpCity: String?
          if (_stmt.isNull(_columnIndexOfCity)) {
            _tmpCity = null
          } else {
            _tmpCity = _stmt.getText(_columnIndexOfCity)
          }
          val _tmpStreet: String?
          if (_stmt.isNull(_columnIndexOfStreet)) {
            _tmpStreet = null
          } else {
            _tmpStreet = _stmt.getText(_columnIndexOfStreet)
          }
          _item =
              SpeedCamera(_tmpId,_tmpName,_tmpType,_tmpLat,_tmpLon,_tmpSpeed,_tmpCity,_tmpStreet)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getCameraCount(): Flow<Int> {
    val _sql: String = "SELECT COUNT(*) FROM speed_cameras"
    return createFlow(__db, false, arrayOf("speed_cameras")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getCamerasInBoundingBox(
    latMin: Double,
    latMax: Double,
    lonMin: Double,
    lonMax: Double,
  ): List<SpeedCamera> {
    val _sql: String = """
        |
        |        SELECT * FROM speed_cameras
        |        WHERE lat BETWEEN ? AND ?
        |          AND lon BETWEEN ? AND ?
        |        
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindDouble(_argIndex, latMin)
        _argIndex = 2
        _stmt.bindDouble(_argIndex, latMax)
        _argIndex = 3
        _stmt.bindDouble(_argIndex, lonMin)
        _argIndex = 4
        _stmt.bindDouble(_argIndex, lonMax)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfLat: Int = getColumnIndexOrThrow(_stmt, "lat")
        val _columnIndexOfLon: Int = getColumnIndexOrThrow(_stmt, "lon")
        val _columnIndexOfSpeed: Int = getColumnIndexOrThrow(_stmt, "speed")
        val _columnIndexOfCity: Int = getColumnIndexOrThrow(_stmt, "city")
        val _columnIndexOfStreet: Int = getColumnIndexOrThrow(_stmt, "street")
        val _result: MutableList<SpeedCamera> = mutableListOf()
        while (_stmt.step()) {
          val _item: SpeedCamera
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpLat: Double
          _tmpLat = _stmt.getDouble(_columnIndexOfLat)
          val _tmpLon: Double
          _tmpLon = _stmt.getDouble(_columnIndexOfLon)
          val _tmpSpeed: Int
          _tmpSpeed = _stmt.getLong(_columnIndexOfSpeed).toInt()
          val _tmpCity: String?
          if (_stmt.isNull(_columnIndexOfCity)) {
            _tmpCity = null
          } else {
            _tmpCity = _stmt.getText(_columnIndexOfCity)
          }
          val _tmpStreet: String?
          if (_stmt.isNull(_columnIndexOfStreet)) {
            _tmpStreet = null
          } else {
            _tmpStreet = _stmt.getText(_columnIndexOfStreet)
          }
          _item =
              SpeedCamera(_tmpId,_tmpName,_tmpType,_tmpLat,_tmpLon,_tmpSpeed,_tmpCity,_tmpStreet)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun countSync(): Int {
    val _sql: String = "SELECT COUNT(*) FROM speed_cameras"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAll() {
    val _sql: String = "DELETE FROM speed_cameras"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
