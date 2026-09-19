package br.com.deh.copiloto

import android.app.Application
import androidx.room.Room
import br.com.deh.copiloto.data.*
import br.com.deh.copiloto.routing.RoutingRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class CopilotoApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var repository: Repository; private set
    lateinit var routing: RoutingRepository; private set
    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "copiloto.db").addMigrations(AppDatabase.MIGRATION_1_2).build()
        val store = SettingsStore(this); repository = Repository(db, store, scope); routing = RoutingRepository(this)
        scope.launch { val retention = store.flow.first().retentionDays; val before = System.currentTimeMillis() - retention * 86_400_000L; db.dao().prune(before); db.dao().pruneSessions(before) }
    }
}
