package com.example.roamly.data.utils

/**
 * Utility geografica per operazioni relative alle coordinate e distanze tra punti sulla mappa.
 */
object MapUtils {

    /**
     * Calcola la distanza in chilometri tra due punti geografici utilizzando
     * la formula dell’haversine, che tiene conto della curvatura terrestre.
     *
     * @param lat1 Latitudine del primo punto.
     * @param lon1 Longitudine del primo punto.
     * @param lat2 Latitudine del secondo punto.
     * @param lon2 Longitudine del secondo punto.
     * @return Distanza approssimativa in chilometri tra i due punti.
     */
    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // Raggio della Terra in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}