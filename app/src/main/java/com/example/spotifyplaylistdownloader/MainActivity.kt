package com.example.spotifyplaylistdownloader

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ModuleInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils.replace
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.material.bottomnavigation.BottomNavigationView
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

    private var activeFragment: Fragment? = null
    private lateinit var sharedPref: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPref = this.getSharedPreferences("MySP", Context.MODE_PRIVATE)

        downloadDirecotry = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString()

        appContext = this

        // widgets
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Set default fragment
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, InputFragment())
                .commit()
        }

        //initialize python
        if (! Python.isStarted()) { Python.start(AndroidPlatform(this)); }

        py = Python.getInstance()
        myModule= py.getModule("get_spotify_names")
        myFunNames = myModule["get_names"]!!

        bottomNavigationView.setOnItemSelectedListener { item ->
            var selectedFragment: Fragment? = null

            when (item.itemId) {
                R.id.navigation_download -> selectedFragment = InputFragment()
                R.id.navigation_history -> selectedFragment = DownloadHistoryFragment()
                R.id.navigation_help -> selectedFragment = HelpFragment()
            }

            if (selectedFragment != null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit()
            }

            true
        }
    }

//    override fun onNewIntent(intent: Intent) {
//        super.onNewIntent(intent)
////
////        val fragmentToOpen = intent.getStringExtra("fragment")
////
////        if (fragmentToOpen == "DownloadFragment") {
////            val playlistName = intent.getStringExtra("playlistName") as String
////            val downloadFragment = PlaylistFragment.newInstance(playlistName, "")
////            supportFragmentManager.beginTransaction()
////                .replace(R.id.fragment_container, downloadFragment)
////                .commit()
////        }
//        if (intent?.getBooleanExtra("OPEN_INPUT_FRAGMENT", false) == true) {
//            // Navigate to InputFragmentsupportFragmentManager.beginTransaction()
//            supportFragmentManager.beginTransaction()
//                .replace(R.id.fragment_container, PlaylistFragment()) // Replace with your actual container ID
//                .commit()
//        }
//    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Check if the intent contains the flag to open PlaylistFragment
        if (intent.getBooleanExtra("OPEN_PLAYLIST_FRAGMENT", false)) {
            // If the fragment is already in the fragment manager, bring it to the front
            val fragment = supportFragmentManager.findFragmentByTag("PlaylistFragment")

            if (fragment != null) {
                // Fragment exists, bring it to the front
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit()
            } else {
                // Fragment doesn't exist, create it and pass necessary data
                val playlistName = intent.getStringExtra("playlistName") ?: ""
                val playlistLink = sharedPref.getString("link", "") ?: ""
                val playlistFragment = PlaylistFragment.newInstance(playlistName, playlistLink).apply {
                    arguments = Bundle().apply {
                        putString("link", playlistLink)
                    }
                }

                supportFragmentManager.beginTransaction()
                    .add(R.id.fragment_container, playlistFragment, "PlaylistFragment")
                    .commit()
            }
        } else if (intent.getBooleanExtra("OPEN_INPUT_FRAGMENT", false)) {
            // Handle the input fragment case
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, InputFragment())
                .commit()
        }
    }

}

suspend fun singleDownload(artist: String, song: String, album: String) {
//

    val myFunDownload: PyObject? = myModule?.get("download")

    Log.d("Download DIrecory", downloadDirecotry)
    val resultDownload = withContext(Dispatchers.IO) { myFunDownload?.call(song, artist, downloadDirecotry)}
    Log.println(Log.INFO, "download", "downloaded")
    saveToExternalStorage(resultDownload.toString(), song, artist, album, MainActivity.appContext)
    Log.println(Log.INFO, "download", "added to media store")

}

fun saveToExternalStorage(mp3Path: String, title: String, artist:String, album: String, context: Context): Boolean {
    val contentResolver = context.contentResolver
    val audioCollection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    Log.d("names", "artist name: $artist, album name: $album")

    val contentValues = ContentValues().apply {
        put(MediaStore.Audio.Media.IS_PENDING, 1)
        put(MediaStore.Audio.Media.DISPLAY_NAME, title)
        put(MediaStore.Audio.Media.ARTIST, artist)
        put(MediaStore.Audio.Media.ALBUM, album)
        put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/")
        //put(MediaStore.Audio.Media.RELATIVE_PATH, mp3Path)
        put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp3")

    }


    return try {
        val uri: Uri? = contentResolver.insert(audioCollection, contentValues)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                FileInputStream(File(mp3Path)).use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            // After writing, set IS_PENDING to 0 to make the file visible to other apps
            contentValues.clear()
            contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
            contentResolver.update(it, contentValues, null, null)
        } ?: throw IOException("Failed to create new MediaStore record.")
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