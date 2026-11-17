package com.persondot.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.persondot.data.model.*

@Database(
    entities = [
        Settings::class, Account::class, Receivable::class, Debt::class, DebtInstallment::class,
        FixedCost::class, MonthEntry::class, Expense::class, Aporte::class, Reflection::class, Goal::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /** v1 -> v2: custos viram previsões mensais (datas de validade + ajustes por mês); "planejados" saem. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fixed_costs ADD COLUMN startsAt TEXT")
                db.execSQL("ALTER TABLE fixed_costs ADD COLUMN endsBefore TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS month_entries (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, fixedId INTEGER NOT NULL, month TEXT NOT NULL, " +
                        "status TEXT NOT NULL, amount REAL, accountId INTEGER)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_month_entries_fixedId_month ON month_entries (fixedId, month)")
                db.execSQL("DROP TABLE IF EXISTS planned")
            }
        }

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "persondot.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
        }
    }
}
