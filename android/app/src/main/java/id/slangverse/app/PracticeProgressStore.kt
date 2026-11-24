package id.slangverse.app

import android.content.Context

/**
 * Stores the local practice snapshot in this app's private preferences. Kept thin on purpose: the
 * codec carries the behaviour and is tested on the JVM, this class only moves strings around.
 */
internal class PracticeProgressStore(context: Context, participant: Boolean = false) {
    private val prefs = context.applicationContext.getSharedPreferences(
        if (participant) "participant-practice" else FILE, Context.MODE_PRIVATE
    )

    fun load(): LocalPracticeSnapshot = LocalPracticeCodec.decode(prefs.getString(KEY, null))

    fun save(snapshot: LocalPracticeSnapshot) {
        prefs.edit().putString(KEY, LocalPracticeCodec.encode(snapshot)).apply()
    }

    fun loadOrder(): PracticeOrder = PracticeOrder.fromStored(prefs.getString(ORDER_KEY, null))

    fun saveOrder(order: PracticeOrder) {
        prefs.edit().putString(ORDER_KEY, order.name).apply()
    }

    fun loadReadUpdates(): List<String> = prefs.getStringSet(READ_UPDATES_KEY, emptySet()).orEmpty().toList()

    fun saveReadUpdates(ids: List<String>) {
        prefs.edit().putStringSet(READ_UPDATES_KEY, ids.toSet()).apply()
    }

    private companion object {
        const val FILE = "local-practice"
        const val KEY = "state-v1"
        const val ORDER_KEY = "feed-order"
        const val READ_UPDATES_KEY = "read-updates"
    }
}
