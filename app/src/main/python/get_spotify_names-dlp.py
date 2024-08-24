import spotipy
from spotipy.oauth2 import SpotifyClientCredentials
from spotipy.exceptions import SpotifyException
from youtubesearchpython import VideosSearch
import yt_dlp as youtube_dl
import os
import logging

# Configure logging to help with debugging
logging.basicConfig(level=logging.DEBUG)
logger = logging.getLogger(__name__)

# using spotipy
def get_playlist_name(sp, playlist):
    try:
        name = sp.playlist(playlist)["name"]
    except SpotifyException:
        name = sp.album(playlist)["name"]

    return name


def validate_link(sp, playlist):
    try:
        try:
            name_playlist = sp.playlist(playlist)["name"]
        except:
            name_album = sp.album(playlist)["name"]
        return True
    except SpotifyException:
        return False


def get_thumbnail(sp, playlist):
    try:
        url = sp.playlist(playlist)["images"][0]["url"]
        return url
    except SpotifyException:
        url = sp.album(playlist)["images"][0]["url"]
        return url


def get_names_list(sp, playlist):
    try:
        results = sp.playlist_tracks(playlist)
        tracks = results["items"]
        while results["next"]:
            results = sp.next(results)
            tracks.extend(results["items"])
    except spotipy.exceptions.SpotifyException:
        # Handling the case for albums
        album = sp.album(playlist)
        tracks = album['tracks']['items']

    # load to dictionary
    song_artist_d = dict()

    for track in tracks:
        if "track" in track:  # Check if it's a playlist response
            track = track["track"]
        track_name = track["name"]
        artist_name = ", ".join([artist["name"] for artist in track["artists"]])
        # get song duration
        duration_seconds = track["duration_ms"] / 1000
        seconds_num = int(duration_seconds % 60)
        if len(str(seconds_num)) == 1:
            seconds_num = f"0{seconds_num}"
        duration_formated = f"{int(duration_seconds / 60)}:{seconds_num}"
        song_artist_d[track_name] = [artist_name, duration_formated]

    return song_artist_d


def get_names(playlist_link, action):
    client_id = "3095cef11f7a4b8681759c1584dd83f8"
    client_secret = "0be2463d4d9f460085871bb0f0447c69"
    playlist_id = playlist_link.split("/")[-1].split("?")[0]

    client_credentials_manager = SpotifyClientCredentials(client_id=client_id, client_secret=client_secret)
    sp = spotipy.Spotify(client_credentials_manager=client_credentials_manager)

    if action == "pl_name":
        return get_playlist_name(sp, playlist_id)
    elif action == "songs":
        return get_names_list(sp, playlist_id)
    elif action == "validate":
        return validate_link(sp, playlist_id)
    elif action == "thumbnail":
        return get_thumbnail(sp, playlist_id)


# downloading and searching youtube
def download(song, artist, directory):
    # Ensure the directory exists
    if not os.path.exists(directory):
        os.makedirs(directory)

    # get the link
    song = song.encode("ascii", errors="replace").decode("ascii")
    search_result = VideosSearch(f"{song} {artist}", limit=1).result()
    if search_result and len(search_result) > 0:
        video_link = search_result["result"][0]["link"].encode("ascii", errors="replace").decode("ascii")
        logger.debug(f"Video link found: {video_link}")
    else:
        logger.error("No video link found.")
        return ""

    # download
    ydl_opts = {
        'format': 'bestaudio/best',
        'outtmpl': os.path.join(directory, '%(title)s.%(ext)s'),
        'postprocessors': [{
            'key': 'FFmpegExtractAudio',
            'preferredcodec': 'mp3',
            'preferredquality': '192',
        }],
        'quiet': False,  # Change to False to enable yt-dlp's verbose output for debugging
        'noplaylist': True,
        'no_warnings': True,
        'ignoreerrors': True,
        'logtostderr': True,
    }

    try:
        with youtube_dl.YoutubeDL(ydl_opts) as ydl:
            info_dict = ydl.extract_info(video_link, download=True)
            logger.debug(f"Info dict: {info_dict}")
            new_file = ydl.prepare_filename(info_dict).replace(".webm", ".mp3").replace(".m4a", ".mp3")

            # Ensure the post-processing was successful and the file isn't empty
            if os.path.exists(new_file) and os.path.getsize(new_file) > 0:
                logger.info(f"Download successful: {new_file}")
                return new_file
            else:
                logger.error("Download failed or resulted in an empty file.")
                return ""
    except youtube_dl.utils.DownloadError as e:
        logger.error(f"An error occurred during download: {e}")
        return ""
    except Exception as e:
        logger.error(f"Unexpected error: {e}")
        return ""

    return ""
