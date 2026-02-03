package devandroid.moacir.novoorcamento.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import devandroid.moacir.novoorcamento.model.Categoria
import devandroid.moacir.novoorcamento.model.Lancamento
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@Database(entities = [Categoria::class, Lancamento::class], version = 2)
abstract class AppDatabase : RoomDatabase() {

    abstract fun orcamentoDao(): OrcamentoDao

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
                    .addCallback(DatabaseCallback) // O Callback agora lida com os dois casos
                    //.allowMainThreadQueries()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val DatabaseCallback = object : RoomDatabase.Callback() {
            // Chamado na primeira instalação
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                prepopularCategorias()
            }

            // CHAMADO QUANDO O BANCO É APAGADO E REFEITO (O seu caso atual)
            override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                super.onDestructiveMigration(db)
                prepopularCategorias()
            }

            private fun prepopularCategorias() {
                // Use GlobalScope or a custom scope to launch a coroutine on the IO thread
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