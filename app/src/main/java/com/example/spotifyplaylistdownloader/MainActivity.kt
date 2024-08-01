package com.example.spotifyplaylistdownloader

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ModuleInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment.Companion.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException


lateinit var downloadDirecotry: String
var playlistName: String? = ""
var songArtistMap = mutableMapOf<String, String>()

lateinit var py: Python
lateinit var myModule: PyObject
lateinit var myFunNames: PyObject

class MainActivity : AppCompatActivity() {

    //private val songsAdapter = RecyclerAdapter()
    companion object {
        lateinit var appContext: Context
            private set
    }

    private var readPermissionGranted = false
    private var writePermissionGranted = true
    lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>

    private val myScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    lateinit var songsAdapter: RecyclerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        val btnDownload = findViewById<Button>(R.id.menuDownloadBtn)
        val btnHistory = findViewById<Button>(R.id.menuHistoryBtn)
        val btnHelp = findViewById<Button>(R.id.menuHelpBtn)

        var selectedButton = btnDownload

        val colorWhite = resources.getColor(R.color.white)
        val colorLightGray = resources.getColor(R.color.light_gray)

        //set the actions for the buttons and make it with the color
        btnDownload.setOnClickListener {
            findNavController(1).navigate(R.id.action_menuFragment_to_inputFragment)
            if (selectedButton != btnDownload) {
                btnDownload.setTextColor(colorWhite)
                selectedButton.setTextColor(colorLightGray)
                selectedButton = btnDownload
            }
        }
        btnHistory.setOnClickListener {
            if (selectedButton != btnHistory) {
                btnHistory.setTextColor(colorWhite)
                selectedButton.setTextColor(colorLightGray)
                selectedButton = btnHistory
            }
        }
        btnHelp.setOnClickListener {
            if (selectedButton != btnHelp) {
                btnHelp.setTextColor(colorWhite)
                selectedButton.setTextColor(colorLightGray)
                selectedButton = btnHelp
            }
        }

        downloadDirecotry = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString()
        appContext = this
        // widgets

        //initialize python
        if (! Python.isStarted()) { Python.start(AndroidPlatform(this)); }

        py = Python.getInstance()
        myModule= py.getModule("get_spotify_names")
        myFunNames = myModule["get_names"]!!

    }



}

suspend fun singleDownload(artist: String, song: String, album: String) {
//

    val myFunDownload: PyObject? = myModule?.get("download")

    val resultDownload = withContext(Dispatchers.IO) { myFunDownload?.call(song, artist, downloadDirecotry)}
    Log.println(Log.INFO, "download", "downloaded")
    saveToExternalStorage(resultDownload.toString(), song, artist, album, MainActivity.appContext)
    Log.println(Log.INFO, "download", "added to media stor")

}

fun saveToExternalStorage(mp3Path: String, title: String, artist:String, album: String, context: Context): Boolean {
    val contentResolver = context.contentResolver
    val audioCollection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    val contentValues = ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, title)
        put(MediaStore.Audio.Media.ARTIST, artist)
        put(MediaStore.Audio.Media.ALBUM, album)
        put(MediaStore.Audio.Media.RELATIVE_PATH, "Music")
        //put(MediaStore.Audio.Media.RELATIVE_PATH, mp3Path)
        put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp3")

    }
    return try {
        contentResolver.insert(audioCollection, contentValues)?.also { uri ->
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(File(mp3Path)).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        true
    } catch (e: IOException) {
        e.printStackTrace()
        false
    }

}


fun checkInternetConnectivity(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork
    val capabilities = connectivityManager.getNetworkCapabilities(network)

    //device is connected
    return capabilities != null //&& capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}