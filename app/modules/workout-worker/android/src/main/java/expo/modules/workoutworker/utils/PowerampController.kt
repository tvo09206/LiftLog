package expo.modules.workoutworker.utils

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.util.Log

/**
 * Controller to automatically switch playlists in Poweramp music player
 * during gym workouts:
 * - Switches to "Rest / 休息" playlist when a rest timer begins.
 * - Switches to "Beast / 起杠 / 战歌" playlist when the rest timer finishes or is dismissed.
 */
object PowerampController {
    private const val TAG = "PowerampController"
    private const val POWERAMP_PACKAGE = "com.maxmpz.audioplayer"
    private const val ACTION_API_COMMAND = "com.maxmpz.audioplayer.API_COMMAND"
    private const val EXTRA_CMD = "cmd"
    private const val CMD_OPEN_TO_PLAY = 20 // PowerampAPI.Commands.OPEN_TO_PLAY

    // Candidates for playlist names
    private val REST_PLAYLIST_NAMES = listOf("Gym_Rest", "休息", "Rest", "Gym Rest")
    private val BEAST_PLAYLIST_NAMES = listOf("Gym_Beast", "起杠", "战歌", "Beast", "Gym Beast")

    @Volatile
    private var isResting = false

    fun onRestStarted(context: Context) {
        if (isResting) return
        isResting = true
        Log.d(TAG, "Rest started -> switching to Rest playlist in Poweramp")
        playPlaylistByNameCandidates(context, REST_PLAYLIST_NAMES, fallbackId = 1)
    }

    fun onRestEnded(context: Context) {
        if (!isResting) return
        isResting = false
        Log.d(TAG, "Rest ended -> switching to Beast playlist in Poweramp")
        playPlaylistByNameCandidates(context, BEAST_PLAYLIST_NAMES, fallbackId = 2)
    }

    private fun playPlaylistByNameCandidates(
        context: Context,
        candidateNames: List<String>,
        fallbackId: Long
    ) {
        val playlistId = findPlaylistId(context, candidateNames) ?: fallbackId
        sendPlayPlaylistIntent(context, playlistId)
    }

    private fun findPlaylistId(context: Context, candidateNames: List<String>): Long? {
        val uri = Uri.parse("content://com.maxmpz.audioplayer.data/playlists")
        val projection = arrayOf("_id", "playlist")
        try {
            val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                val idCol = it.getColumnIndex("_id")
                val nameCol = it.getColumnIndex("playlist")
                if (idCol != -1 && nameCol != -1) {
                    while (it.moveToNext()) {
                        val name = it.getString(nameCol)
                        if (candidateNames.any { candidate -> candidate.equals(name, ignoreCase = true) }) {
                            val id = it.getLong(idCol)
                            Log.d(TAG, "Found Poweramp playlist '$name' with ID $id")
                            return id
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not query Poweramp playlists from ContentProvider", e)
        }
        return null
    }

    private fun sendPlayPlaylistIntent(context: Context, playlistId: Long) {
        try {
            val dataUri = Uri.parse("content://com.maxmpz.audioplayer.data/playlists/$playlistId/files")
            val intent = Intent(ACTION_API_COMMAND).apply {
                setPackage(POWERAMP_PACKAGE)
                data = dataUri
                putExtra(EXTRA_CMD, CMD_OPEN_TO_PLAY)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(intent)
            Log.d(TAG, "Broadcast sent to Poweramp: play playlist ID $playlistId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Poweramp broadcast", e)
        }
    }
}
