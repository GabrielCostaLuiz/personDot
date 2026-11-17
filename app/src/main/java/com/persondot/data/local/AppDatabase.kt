package com.persondot.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.persondot.data.model.*

@Database(
    entities = [
        Settings::class, Account::class, Receivable::class, Debt::class, DebtInstallment::class,
        FixedCost::class, Planned::class, Expense::class, Aporte::class, Reflection::class, Goal::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun financeDao(): FinanceDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "persondot.db")
                .build().also { INSTANCE = it }
        }
    }
}
