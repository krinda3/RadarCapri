package com.pridalac.radarcapri.`data`

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class RadarDatabase_Impl : RadarDatabase() {
  private val _radarDao: Lazy<RadarDao> = lazy {
    RadarDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1,
        "c7c12cac7f7a4a4826272236964ce128", "563f677208de4b2cffd3c1428b060529") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `speed_cameras` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `lat` REAL NOT NULL, `lon` REAL NOT NULL, `speed` INTEGER NOT NULL, `city` TEXT, `street` TEXT, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_speed_cameras_lat_lon` ON `speed_cameras` (`lat`, `lon`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'c7c12cac7f7a4a4826272236964ce128')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `speed_cameras`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsSpeedCameras: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsSpeedCameras.put("id", TableInfo.Column("id", "TEXT", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSpeedCameras.put("name", TableInfo.Column("name", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSpeedCameras.put("type", TableInfo.Column("type", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSpeedCameras.put("lat", TableInfo.Column("lat", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSpeedCameras.put("lon", TableInfo.Column("lon", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSpeedCameras.put("speed", TableInfo.Column("speed", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSpeedCameras.put("city", TableInfo.Column("city", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSpeedCameras.put("street", TableInfo.Column("street", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysSpeedCameras: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesSpeedCameras: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesSpeedCameras.add(TableInfo.Index("index_speed_cameras_lat_lon", false, listOf("lat",
            "lon"), listOf("ASC", "ASC")))
        val _infoSpeedCameras: TableInfo = TableInfo("speed_cameras", _columnsSpeedCameras,
            _foreignKeysSpeedCameras, _indicesSpeedCameras)
        val _existingSpeedCameras: TableInfo = read(connection, "speed_cameras")
        if (!_infoSpeedCameras.equals(_existingSpeedCameras)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |speed_cameras(com.pridalac.radarcapri.data.SpeedCamera).
              | Expected:
              |""".trimMargin() + _infoSpeedCameras + """
              |
              | Found:
              |""".trimMargin() + _existingSpeedCameras)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "speed_cameras")
  }

  public override fun clearAllTables() {
    super.performClear(false, "speed_cameras")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(RadarDao::class, RadarDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun radarDao(): RadarDao = _radarDao.value
}
