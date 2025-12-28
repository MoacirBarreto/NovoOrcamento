package devandroid.moacir.novoorcamento.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import devandroid.moacir.novoorcamento.model.Categoria
import devandroid.moacir.novoorcamento.model.Lancamento
import java.util.concurrent.Executors

@Database(entities = [Categoria::class, Lancamento::class], version = 1)
abstract class AppDatabase : RoomDatabase() {

    // Aqui definiremos os DAOs depois
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
                    .addCallback(DatabaseCallback) // <--- O SEGREDO ESTÁ AQUI
                    .allowMainThreadQueries() // Simplificando para este estágio inicial
                    .build()
                INSTANCE = instance
                instance
            }
        }

        // Isso roda apenas quando o app é instalado/banco criado pela 1ª vez
        private val DatabaseCallback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)

                Executors.newSingleThreadExecutor().execute {
                    val dao = INSTANCE?.orcamentoDao()

                    // Suas categorias iniciais
                    val list = listOf(
                        Categoria(nome = "Receita"),
                        Categoria(nome = "Alimentação"),
                        Categoria(nome = "Casa"),
                        Categoria(nome = "Lazer"),
                        Categoria(nome = "Transporte"),
                        Categoria(nome = "Outros")
                    )

                    // Inserir todas
                    list.forEach { dao?.inserirCategoria(it) }
                }
            }
        }
    }
}
