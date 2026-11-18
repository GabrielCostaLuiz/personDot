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
        FixedCost::class, MonthEntry::class, Expense::class, Aporte::class, Reflection::class, Goal::class,
        Revenue::class, Snapshot::class
    ],
    version = 5,
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

        /** v2 -> v3: categorias e nomes de grupos viram dados editáveis. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN categories TEXT NOT NULL DEFAULT '$DEFAULT_CATEGORIES'")
                db.execSQL("ALTER TABLE settings ADD COLUMN groupNames TEXT NOT NULL DEFAULT '$DEFAULT_GROUP_NAMES'")
            }
        }

        /** v3 -> v4: limite do MEI, faturamento por mês e histórico do patrimônio. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN meiOn INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE settings ADD COLUMN meiLimit REAL NOT NULL DEFAULT 81000.0")
                db.execSQL("CREATE TABLE IF NOT EXISTS revenues (month TEXT NOT NULL, amount REAL NOT NULL, PRIMARY KEY(month))")
                db.execSQL("CREATE TABLE IF NOT EXISTS snapshots (month TEXT NOT NULL, inHand REAL NOT NULL, ipca REAL NOT NULL, owed REAL NOT NULL, PRIMARY KEY(month))")
            }
        }

        /** v4 -> v5: grupos de custo criados pela pessoa. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE settings ADD COLUMN customGroups TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "persondot.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build().also { INSTANCE = it }
        }
    }
}
