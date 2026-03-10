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

@Database(
    entities = [Categoria::class, Lancamento::class, Agenda::class],
    version = 3,
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
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // Dentro do AppDatabase.kt
        private val DatabaseCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val dao = database.orcamentoDao()

                        // Lista explícita com IDs para garantir a ordem no primeiro acesso
                        val categoriasIniciais = listOf(
                            Categoria(id = 1, nome = "Receita"),
                            Categoria(id = 2, nome = "Alimentação"),
                            Categoria(id = 3, nome = "Casa"),
                            Categoria(id = 4, nome = "Lazer"),
                            Categoria(id = 5, nome = "Transporte"),
                            Categoria(id = 6, nome = "Outros")
                        )

                        categoriasIniciais.forEach {
                            dao.upsertCategoria(it)
                        }
                    }
                }
            }
        }
    }
}