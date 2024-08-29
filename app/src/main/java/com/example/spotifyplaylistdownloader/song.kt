package com.example.spotifyplaylistdownloader

import android.os.Parcel
import android.os.Parcelable

class Song(
    var songName: String,
    var artistName: String,
    var duration: String,
    var state: Int
) : Parcelable {
    constructor(parcel: Parcel) : this(
    parcel.readString() ?: "",
    parcel.readString() ?: "",
    parcel.readString() ?: "",
    parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(songName)
        parcel.writeString(artistName)
        parcel.writeString(duration)
        parcel.writeInt(state)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Song> {
        override fun createFromParcel(parcel: Parcel): Song {
            return Song(parcel)
        }

        override fun newArray(size: Int): Array<Song?> {
            return arrayOfNulls(size)
        }
    }
}