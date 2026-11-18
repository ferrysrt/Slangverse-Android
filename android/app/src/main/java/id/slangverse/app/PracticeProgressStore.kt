package id.slangverse.app

import android.content.Context

/**
 * Stores the local practice snapshot in this app's private preferences. Kept thin on purpose: the
 * codec carries the behaviour and is tested on the JVM, this class only moves strings around.
 */
internal class PracticeProgressStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): LocalPracticeSnapshot = LocalPracticeCodec.decode(prefs.getString(KEY, null))

    fun save(snapshot: LocalPracticeSnapshot) {
        prefs.edit().putString(KEY, LocalPracticeCodec.encode(snapshot)).apply()
    }

    private companion object {
        const val FILE = "local-practice"
        const val KEY = "state-v1"
    }
}
