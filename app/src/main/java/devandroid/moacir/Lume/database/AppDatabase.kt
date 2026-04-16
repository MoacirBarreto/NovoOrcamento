package com.moacir.Lume.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moacir.Lume.model.Agenda
import com.moacir.Lume.model.Categoria
import com.moacir.Lume.model.Lancamento

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

        private val DatabaseCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {super.onCreate(db)
                // Usar transação SQL direta no 'db' fornecido pelo onCreate é mais seguro
                db.execSQL("INSERT INTO categorias (id, nome) VALUES (1, 'Receita')")
                db.execSQL("INSERT INTO categorias (id, nome) VALUES (2, 'Alimentação')")
                db.execSQL("INSERT INTO categorias (id, nome) VALUES (3, 'Casa')")
                db.execSQL("INSERT INTO categorias (id, nome) VALUES (4, 'Lazer')")
                db.execSQL("INSERT INTO categorias (id, nome) VALUES (5, 'Transporte')")
                db.execSQL("INSERT INTO categorias (id, nome) VALUES (6, 'Outros')")
            }
        }
    }
}