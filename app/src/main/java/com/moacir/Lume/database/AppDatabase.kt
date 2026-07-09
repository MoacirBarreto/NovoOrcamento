package com.moacir.Lume.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moacir.Lume.model.Agenda
import com.moacir.Lume.model.Categoria
import com.moacir.Lume.model.Lancamento
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Categoria::class, Lancamento::class, Agenda::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun orcamentoDao(): OrcamentoDao
    abstract fun agendaDao(): AgendaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "orcamento_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .addCallback(DatabaseCallback)
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val DatabaseCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Usamos Coroutine para inserir via DAO após a criação do banco
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        prepopularCategorias(database.orcamentoDao())
                    }
                }
            }

            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        prepopularCategorias(database.orcamentoDao())
                    }
                }
            }

            private suspend fun prepopularCategorias(dao: OrcamentoDao) {
                val categoriasPadrao = listOf(
                    Categoria(id = 1, nome = "Receita"),
                    Categoria(id = 2, nome = "Alimentação"),
                    Categoria(id = 3, nome = "Casa"),
                    Categoria(id = 4, nome = "Lazer"),
                    Categoria(id = 5, nome = "Transporte"),
                    Categoria(id = 6, nome = "Outros")
                )
                dao.inserirCategoriasIniciais(categoriasPadrao)
            }
        }
    }
}