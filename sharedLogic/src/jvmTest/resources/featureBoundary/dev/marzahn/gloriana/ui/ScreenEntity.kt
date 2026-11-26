package dev.marzahn.gloriana.ui

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "screens")
data class ScreenEntity(@PrimaryKey val name: String)
