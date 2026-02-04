package devandroid.moacir.novoorcamento.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import devandroid.moacir.novoorcamento.model.Categoria
import devandroid.moacir.novoorcamento.model.Lancamento
import devandroid.moacir.novoorcamento.model.Agenda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// 1. Adicionado Agenda::class aqui
@Database(entities = [Categoria::class, Lancamento::class, Agenda::class], version = 3)
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
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val DatabaseCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                prepopularCategorias()
            }

            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                prepopularCategorias()
            }

            private fun prepopularCategorias() {
                // Escopo de Coroutine para rodar em segundo plano
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = INSTANCE?.orcamentoDao()
                    val list = listOf(
                        Categoria(id = 1, nome = "Receita"),
                        Categoria(id = 2, nome = "Alimentação"),
                        Categoria(id = 3, nome = "Casa"),
                        Categoria(id = 4, nome = "Lazer"),
                        Categoria(id = 5, nome = "Transporte"),
                        Categoria(id = 6, nome = "Outros")
                    )
                    list.forEach { dao?.upsertCategoria(it) }
                }
            }
        }
    }
}