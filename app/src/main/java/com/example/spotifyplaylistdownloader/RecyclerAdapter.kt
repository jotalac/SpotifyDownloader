package com.example.spotifyplaylistdownloader

import android.content.ContentValues
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class RecyclerAdapter(private var songs: ArrayList<Song>) : RecyclerView.Adapter<RecyclerAdapter.SongViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val holder = SongViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.song_item, parent, false))
        holder.itemView.setOnClickListener {
            val songName = songs[holder.adapterPosition].songName
            val artistName = songs[holder.adapterPosition].artistName
            Toast.makeText(MainActivity.appContext, "Song - $songName by $artistName", Toast.LENGTH_SHORT).show()
        }
        return holder
    }

    fun add(song: Song) {
        songs = songs.apply { add(song) }
        notifyItemInserted(songs.size - 1)
    }

    fun delete() {
        songs = songs.apply { removeAt(0) }
        notifyDataSetChanged()
    }

    fun allStartDownload() {
        songs.forEach { if (it.state != 2) it.state = 1 }
        notifyDataSetChanged()
    }

    fun allOneFinished(name: String) {
        val position = songs.indexOfFirst { it.songName == name }
        songs[position].state = 2
        notifyItemChanged(position)
    }

    fun cancelAllDownload() {
        songs.forEach { if (it.state == 1) it.state=0 }
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        var song = songs[position]

        //display the values
        holder.songName.text = song.songName
        holder.artistName.text = song.artistName
        holder.duration.text = song.duration

        //make it reset for every item
        when (song.state) {
            0 -> holder.stateDownload()
            1 -> holder.stateLoading()
            2 -> holder.stateFinished()
        }

        //handle the download click on single song
        holder.downloadButton.setOnClickListener {
            song.state = 1
            holder.stateLoading()
            //start the downloading
            CoroutineScope(Dispatchers.IO).launch {
                val resultDownload = singleDownload(song.artistName, song.songName, playlistName.toString())
                song.state = 2
                songArtistMap.remove(song.songName)
                println(songArtistMap)

                withContext(Dispatchers.Main) {
                    holder.stateFinished()
                    Toast.makeText(MainActivity.appContext, "Downloaded: ${song.songName}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return songs.size
    }

    fun getSongsState(): ArrayList<Song> {
        return songs
    }

    fun updateSongsState(songStates: ArrayList<Song>) {
        songs = songStates
        notifyDataSetChanged()
    }

    class SongViewHolder(view: View) : RecyclerView.ViewHolder(view){
        val songName = view.findViewById<TextView>(R.id.song_name)
        val artistName = view.findViewById<TextView>(R.id.artist_name)
        val duration = view.findViewById<TextView>(R.id.duration_view)
        val downloadButton = view.findViewById<ImageButton>(R.id.button_download_single)
        val loadingProgressBar = view.findViewById<ProgressBar>(R.id.loading_wheel)
        val checkImage = view.findViewById<ImageView>(R.id.finished_image)

        fun stateLoading() {
            downloadButton.visibility = View.INVISIBLE
            loadingProgressBar.visibility = View.VISIBLE
            checkImage.visibility = View.INVISIBLE
        }

        fun stateDownload() {
            downloadButton.visibility = View.VISIBLE
            loadingProgressBar.visibility = View.INVISIBLE
            checkImage.visibility = View.INVISIBLE
        }

        fun stateFinished() {
            downloadButton.visibility = View.INVISIBLE
            loadingProgressBar.visibility = View.INVISIBLE
            checkImage.visibility = View.VISIBLE

        }
    }
}

