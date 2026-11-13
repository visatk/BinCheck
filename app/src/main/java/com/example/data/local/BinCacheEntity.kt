package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.BinInfo

@Entity(
    tableName = "bin_cache",
    indices = [Index(value = ["expiresAtMillis"])]
)
data class BinCacheEntity(
    @PrimaryKey val bin: String,
    val scheme: String,
    val type: String,
    val brand: String,
    val bankName: String,
    val bankPhone: String,
    val bankUrl: String,
    val countryName: String,
    val countryCode: String,
    val countryEmoji: String,
    val currency: String,
    val isPrepaid: Boolean?,
    val source: String,
    val isNetworkVerified: Boolean,
    val fetchedAtMillis: Long,
    val expiresAtMillis: Long
) {
    fun toBinInfo(): BinInfo = BinInfo(
        bin = bin,
        scheme = scheme,
        type = type,
        brand = brand,
        bankName = bankName,
        bankPhone = bankPhone,
        bankUrl = bankUrl,
        countryName = countryName,
        countryCode = countryCode,
        countryEmoji = countryEmoji,
        currency = currency,
        isPrepaid = isPrepaid,
        source = source,
        isNetworkVerified = isNetworkVerified
    )

    companion object {
        fun fromBinInfo(
            info: BinInfo,
            ttlMillis: Long,
            nowMillis: Long = System.currentTimeMillis()
        ): BinCacheEntity = BinCacheEntity(
            bin = info.bin,
            scheme = info.scheme,
            type = info.type,
            brand = info.brand,
            bankName = info.bankName,
            bankPhone = info.bankPhone,
            bankUrl = info.bankUrl,
            countryName = info.countryName,
            countryCode = info.countryCode,
            countryEmoji = info.countryEmoji,
            currency = info.currency,
            isPrepaid = info.isPrepaid,
            source = info.source,
            isNetworkVerified = info.isNetworkVerified,
            fetchedAtMillis = nowMillis,
            expiresAtMillis = nowMillis + ttlMillis
        )
    }
}
