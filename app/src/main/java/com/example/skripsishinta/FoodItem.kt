package com.example.skripsishinta


import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FoodItem(
    val name: String,
    val quantity: Int,
    val calories: Int,
    val weight: Int
) : Parcelable
