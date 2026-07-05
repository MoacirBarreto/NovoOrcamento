package com.moacir.Lume.database

import android.content.Context
import android.net.Uri
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object BackupManager {
    private const val DATABASE_NAME = "orcamento_db"

    fun exportDatabase(context: Context, destinationUri: Uri) {
        try {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            val outputStream = context.contentResolver.openOutputStream(destinationUri)
            val inputStream = FileInputStream(dbFile)

            outputStream?.use { output ->
                inputStream.use { input ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(context, "Backup concluído!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Erro ao exportar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun importDatabase(context: Context, sourceUri: Uri, onComplete: () -> Unit) {
        try {
            val dbName = "orcamento_db"
            val dbFile = context.getDatabasePath(dbName)

            // 1. FECHAR O BANCO E LIMPAR A INSTÂNCIA
            AppDatabase.getDatabase(context).close()

            // 2. Copiar os arquivos
            val inputStream = context.contentResolver.openInputStream(sourceUri)
            val outputStream = FileOutputStream(dbFile)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            // 3. DELETAR arquivos temporários (WAL e SHM) - ISSO É VITAL
            val shmFile = File(dbFile.path + "-shm")
            val walFile = File(dbFile.path + "-wal")
            shmFile.delete()
            walFile.delete()

            onComplete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}