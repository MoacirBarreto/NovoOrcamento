
# --------------------------------------------------------------------------
# REGRAS DO MPAndroidChart (Gráficos)
# --------------------------------------------------------------------------
# Mantém a biblioteca de gráficos intacta para evitar que os eixos e dados sumam
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# --------------------------------------------------------------------------
# REGRAS DO ROOM (Banco de Dados)
# --------------------------------------------------------------------------
# O Room precisa encontrar as classes de banco de dados e modelos pelo nome real
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>(...);
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# --------------------------------------------------------------------------
# REGRAS PARA SEUS MODELOS (Lançamento, Categoria, Agenda, SaldoMensal)
# --------------------------------------------------------------------------
# Isso garante que o banco de dados consiga ler e escrever seus dados corretamente
-keep class com.moacir.Lume.model.** { *; }

# --------------------------------------------------------------------------
# REGRAS PARA KOTLIN COROUTINES (Processamento em segundo plano)
# --------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-dontwarn kotlinx.coroutines.**

# --------------------------------------------------------------------------
# REGRAS PARA MATERIAL DESIGN 3
# --------------------------------------------------------------------------
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**