package com.example.spotifyplaylistdownloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import com.chaquo.python.PyObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext



class DownloadService: Service() {

    private lateinit var mContext: Context
    private var serviceCallback: ServiceCallback? = null

    private var downloadJob: Job? = null
    private val myScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val downloaded = mutableListOf<String>()

    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private val notificationId = 1

    override fun onCreate() {
        super.onCreate()

        val channel = NotificationChannel(
            "downloading_channel",
            "Downloading Notifications",
            NotificationManager.IMPORTANCE_HIGH
        )

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    //callbacks to the fragment
    override fun onBind(intent: Intent?): IBinder? {
        return DownloadBinder()
    }

    inner class DownloadBinder : Binder() {
        fun getService(): DownloadService {
            return this@DownloadService
        }
    }

    fun setServiceCallback(callback: ServiceCallback) {
        serviceCallback = callback
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val playlistNameString = intent?.getStringExtra("playlistName")
        mContext = applicationContext

        when(intent?.action) {
            Actions.START.toString() -> start(playlistNameString.toString())
            Actions.STOP.toString() -> stop()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun start(playlistNameString: String) {
        //crete a button to stop the intent
        val actionIntent = Intent(mContext, MyBroadcastReceiver::class.java)
        actionIntent.action = "ACTION_BUTTON_CLICKED"
        val actionPendingIntent = PendingIntent.getBroadcast(
            mContext,
            0,
            actionIntent,
            PendingIntent.FLAG_IMMUTABLE)

        //create notification
        notificationBuilder = NotificationCompat.Builder(this, "downloading_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Downloading - $playlistNameString")
            .setContentText("0%")
            .setProgress(100, 0, false)
            .addAction(R.drawable.button_folder, "stop", actionPendingIntent)

        startForeground(
            notificationId,
            notificationBuilder.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )


        //download process
        val myFunDownload: PyObject? = myModule?.get("download")

        var downloadCount = 1
        val playlistSize = songArtistMap.size
        //start the download
        downloadJob = myScope.launch {
            for ((song, artist) in songArtistMap!!) {
                val resultDownload = withContext(Dispatchers.IO) { myFunDownload?.call(song, artist, downloadDirecotry) }
                downloaded.add(song)
                // make the song appear in the music player
                if (resultDownload.toString() != "") {
                    saveToExternalStorage(resultDownload.toString(), song.toString(), artist, playlistNameString, mContext)

                    Toast.makeText(mContext,"Downloaded: $song", Toast.LENGTH_SHORT).show()
                }

                serviceCallback?.onSongDownloaded(song)

                //update notification
                notificationBuilder.setProgress(100, ((downloadCount * 100) / playlistSize), false)
                notificationBuilder.setContentText("$downloadCount/$playlistSize")
                notificationManager.notify(notificationId, notificationBuilder.build())
                downloadCount++


                //check if it should be canceled
                if (!isActive) {
                    break
                }
            }
            //delete from the list that are already downloaded
            downloaded.forEach { songArtistMap.remove(it) }
            downloaded.clear()

            serviceCallback?.finishedDownload()

            notificationBuilder.setContentText("Downloaded")
            notificationBuilder.setProgress(0, 0, false)
            notificationBuilder.setOngoing(false)
            notificationManager.notify(notificationId, notificationBuilder.build())

        }


        stopSelf()
    }


    private fun stop() {
        //cancel download
        downloadJob?.cancel()

        //delete from the list that are already downloaded
        downloaded.forEach { songArtistMap.remove(it) }
        downloaded.clear()

        notificationManager.cancel(notificationId)
        stopForeground(true)
        stopSelf()
    }

    enum class Actions {
        START, STOP
    }
}


