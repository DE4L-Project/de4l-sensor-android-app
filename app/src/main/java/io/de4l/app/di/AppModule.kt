package io.de4l.app.di

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.de4l.app.AppConstants
import io.de4l.app.auth.AuthManager
import io.de4l.app.bluetooth.BleConnectionManager
import io.de4l.app.bluetooth.BluetoothDeviceManager
import io.de4l.app.database.AppDatabase
import io.de4l.app.device.DeviceRepository
import io.de4l.app.location.LocationService
import io.de4l.app.mqtt.MqttManager
import io.de4l.app.mqtt.MqttMessagePersistence
import io.de4l.app.sensor.AirBeamSensorValueParser
import io.de4l.app.tracking.BackgroundServiceWatcher
import io.de4l.app.tracking.TrackingManager
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class AppModule() {

    @Singleton
    @Provides
    fun provideLocationService(): LocationService {
        return LocationService()
    }

    @Singleton
    @Provides
    fun provideDatabase(application: Application): AppDatabase {
        return Room.databaseBuilder(
            application,
            AppDatabase::class.java,
            AppConstants.ROOM_DB_NAME
        )
            .addMigrations(migrate())
            .fallbackToDestructiveMigration()
            .build()
    }

    @Singleton
    @Provides
    fun provideMqttPersistence(appDatabase: AppDatabase): MqttMessagePersistence {
        return MqttMessagePersistence(appDatabase)
    }

    @Singleton
    @Provides
    fun provideBluetoothAdapter(application: Application): BluetoothAdapter {
        return (application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    @Singleton
    @Provides
    fun provideLocationManager(application: Application): LocationManager {
        return application.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    @Singleton
    @Provides
    fun provideFusedLocationProviderClients(application: Application): FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(application)
    }

    @Singleton
    @Provides
    fun provideDe4lBluetoothManager(
        application: Application,
        bluetoothAdapter: BluetoothAdapter,
        locationService: LocationService,
        deviceRepository: DeviceRepository,
        trackingManager: TrackingManager,
    ): BluetoothDeviceManager {
        return BluetoothDeviceManager(
            application,
            bluetoothAdapter,
            locationService,
            deviceRepository,
            trackingManager
        )
    }

    @Singleton
    @Provides
    fun provideAuthManager(application: Application): AuthManager {
        return AuthManager(application)
    }

    @Singleton
    @Provides
    fun provideMqttManager(
        application: Application,
        mqttMessagePersistence: MqttMessagePersistence,
        authManager: AuthManager
    ): MqttManager {
        return MqttManager(application, mqttMessagePersistence, authManager)
    }

    @Singleton
    @Provides
    fun provideDeviceManager(appDatabase: AppDatabase): DeviceRepository {
        return DeviceRepository(appDatabase)
    }

    @Singleton
    @Provides
    fun provideServiceWatcher(application: Application): BackgroundServiceWatcher {
        return BackgroundServiceWatcher(application)
    }

    @Singleton
    @Provides
    fun provideTrackingManager(
        mqttManager: MqttManager,
        authManager: AuthManager,
        deviceRepository: DeviceRepository
    ): TrackingManager {
        return TrackingManager(mqttManager, authManager, deviceRepository)
    }

//    @Singleton
//    @Provides
//    fun provideBleConnectionManager(
//        application: Application,
//    ): BleConnectionManager {
//        return BleConnectionManager(application)
//    }

    private fun migrate(): Migration {
        return object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
            }
        }
    }
}