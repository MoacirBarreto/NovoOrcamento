package com.moacir.Lume.database

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import java.io.FileInputStream
import java.io.FileOutputStream

object BackupManager {
    private const val DB_NAME = "lume_database" // Nome definido no seu AppDatabase

    fun exportDatabase(context: Context, destinationUri: Uri) {
        try {
            val dbFile = context.getDatabasePath(DB_NAME)
            // Se usar WAL mode, pode precisar de dbFile-shm e dbFile-wal,
            // mas no Lume estamos focando no arquivo principal.

            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                FileInputStream(dbFile).use { input ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(context, "Backup concluído com sucesso!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("Backup", "Erro ao exportar: ${e.message}")
            Toast.makeText(context, "Erro ao exportar backup", Toast.LENGTH_SHORT).show()
        }
    }

    fun importDatabase(context: Context, sourceUri: Uri, onComplete: () -> Unit) {
        try {
            val dbFile = context.getDatabasePath(DB_NAME)

            // Fecha o banco antes de sobrescrever para evitar corrupção
            AppDatabase.getDatabase(context).close()

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
            onComplete()
        } catch (e: Exception) {
            Log.e("Backup", "Erro ao importar: ${e.message}")
            Toast.makeText(context, "Arquivo de backup inválido", Toast.LENGTH_SHORT).show()
        }
    }
}