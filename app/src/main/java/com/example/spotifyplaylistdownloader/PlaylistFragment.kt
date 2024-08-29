package com.example.spotifyplaylistdownloader

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.IBinder
import android.provider.ContactsContract
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaquo.python.PyObject
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.appcompat.widget.Toolbar
import androidx.work.Data
import com.google.android.material.bottomnavigation.BottomNavigationView
import jp.wasabeef.picasso.transformations.BlurTransformation
import java.io.Serializable
import kotlin.reflect.typeOf


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [PlaylistFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class PlaylistFragment : Fragment(), ServiceCallback {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    private val myScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var downloadingBoolean = false

    lateinit var songsAdapter: RecyclerAdapter
    lateinit var downloadButton: Button

    private lateinit var mainActivity: MainActivity

    private lateinit var sharedPref: SharedPreferences


    private var playlistName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }

        sharedPref = requireContext().getSharedPreferences("MySP", Context.MODE_PRIVATE)

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainActivity) {
            mainActivity = context
        }
    }

    override fun onSongDownloaded(song: String) {
        songsAdapter.allOneFinished(song)
        println("here function")
    }

    override fun finishedDownload() {
        downloadButton.text = "Downloaded"
        downloadButton.isEnabled = false
        downloadButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.light_gray))

        downloadingBoolean = false
    }

    private fun cancelDownloadUI() {
        downloadingBoolean = false

        //update ui button
        downloadButton.text = "Download"
        downloadButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green))
        songsAdapter.cancelAllDownload()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Start the service
        val serviceIntent = Intent(requireContext(), DownloadService::class.java)
        requireContext().startService(serviceIntent)

        // Bind to the service to set the callback
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(className: ComponentName?, binder: IBinder?) {
                val serviceBinder = binder as DownloadService.DownloadBinder
                val service = serviceBinder.getService()
                service.setServiceCallback(this@PlaylistFragment)
            }

            override fun onServiceDisconnected(className: ComponentName?) {
            }
        }
        requireContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_playlist, container, false)

        //get link from the previous fragment
        val playlistLink = arguments?.getString("link").toString()
        val playlistNameString = myFunNames.call(playlistLink, "pl_name").toString()
        playlistName = playlistNameString

        //add playlist to history shared preferences
        val sharedPreferences = MySharedPreferences(requireContext())
        val newPlaylist = PlaylistHistory(playlistNameString, playlistLink)
        sharedPreferences.savePlaylistToHistory("playlistHistory", newPlaylist)
        println(sharedPreferences.getPlaylistHistory("playlistHistory"))
        println("tady more")

        //widgets
        val imageView = view.findViewById<ImageView>(R.id.playlistImageView)
        val imageViewWide = view.findViewById<ImageView>(R.id.playlistImageViewWide)
        val playlistTextView = view.findViewById<TextView>(R.id.playlistName)
        downloadButton = view.findViewById<Button>(R.id.downloadButton)
        val toolbar = view.findViewById<Toolbar>(R.id.toolbarPlaylist)

        val bottomNavBar = mainActivity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavBar.visibility = View.INVISIBLE

        //toolbar
        (activity as AppCompatActivity).apply {
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
        }
        toolbar.title = playlistNameString
        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
            //requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        //recycler view
        fun createSongList(playlistLink: String): ArrayList<Song>{
            //get playlist songs and add to recycler view
            val artistSong = myFunNames?.call(playlistLink, "songs")?.asMap()!!.map { (key, value) -> key.toString() to value.asList() }.toMap()
            val list = ArrayList<Song>()

            songArtistMap = artistSong?.map { (key, value) -> key to value.toString().substringBefore(",").substringAfter("[")}!!.toMap().toMutableMap()
            println(songArtistMap)

            //add the output to song type and to list
            for ((key, value) in artistSong!!){
                val valueList = value.toList()
                var artist = valueList[0].toString()
                artist = if (artist.length > 35) "${artist.substring(0, 35)}..." else artist
                list.add(Song(songName = key.toString(), artistName =  artist, duration =  valueList[1].toString(), state = 0 ))
                //songArtistMap += key.toString() to valueList[0].toString()
            }
            return list
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        //get playlist songs and add to recycler view
        val songData = createSongList(playlistLink)
        songsAdapter = RecyclerAdapter(songData)
        recyclerView.adapter = songsAdapter


        //restore recycler view
        Log.d("febug", savedInstanceState.toString())
        if (savedInstanceState != null) {
            // Restore the state of the songs
            val songStates = savedInstanceState.getParcelableArrayList<Song>("songs_state")
            if (songStates != null) {
                songsAdapter.updateSongsState(songStates)
            }

            // Restore the state of the download button
            downloadingBoolean = savedInstanceState.getBoolean("downloading_state")
            playlistName = savedInstanceState.getString("playlist_name")

            if (downloadingBoolean) {
                downloadButton.text = "Cancel download"
                downloadButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red))
            } else {
                downloadButton.text = "Download"
                downloadButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green))
            }
        }


        //set playlist name
        playlistTextView.text = playlistNameString

        // handle images loads
        myScope.launch {
            //set the image to playlist thumbnail
            val imageUrl = myFunNames.call(playlistLink, "thumbnail").toString()
//            target to set the image to both normal and wide at once
            val targetImages = object : Target {
                override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                    // Set the bitmap to both ImageViews
                    imageView.setImageBitmap(bitmap)
                    imageViewWide.setImageBitmap(bitmap)

                    imageView.animate().alpha(1f).setDuration(500).start()
                    imageViewWide.animate().alpha(0.5f).setDuration(2000).start()
                }

                override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
                    // Handle bitmap loading failure
                }

                override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                    // Handle bitmap loading preparation
                }
            }

            //load images
            Picasso.get()
                .load(imageUrl)
                .into(targetImages)

        }



        //download all button
        val myFunDownload: PyObject? = myModule?.get("download")
        //variables for the download
        var isDownloading = false
        var downloadJob: Job? = null // Store reference to the download job
        val downloaded = mutableListOf<String>()

        downloadButton.setOnClickListener {
            try {
                if (!downloadingBoolean) {
                    val intent = Intent(requireContext(), DownloadService::class.java).apply {
                        action = DownloadService.Actions.START.toString()
                    }
                    intent.putExtra("playlistName", playlistNameString)
                    ContextCompat.startForegroundService(requireContext(), intent)
                    downloadingBoolean = true


                    //update ui
                    downloadButton.text = "Cancel download"
                    downloadButton.setBackgroundColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.red
                        )
                    )
                    songsAdapter.allStartDownload()

                } else {
                    val intent = Intent(requireContext(), DownloadService::class.java).apply {
                        action = DownloadService.Actions.STOP.toString()
                    }
                    ContextCompat.startForegroundService(requireContext(), intent)

                    //update ui
                    cancelDownloadUI()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }


    fun onDestroy() {
        super.onDestroy()
        myScope.cancel()
    }

        return view
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return true
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment PlaylistFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            PlaylistFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // Save the state of each song
        val songStates = songsAdapter.getSongsState()
        Log.d("PlaylistFragment", "Saving state: $songStates")
        outState.putParcelableArrayList("songs_state", songStates)

        // Save the state of the download button
        outState.putBoolean("downloading_state", downloadingBoolean)
        outState.putString("playlist_name", playlistName)
    }


}