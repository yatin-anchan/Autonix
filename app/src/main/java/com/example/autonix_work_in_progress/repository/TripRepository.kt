package com.example.autonix_work_in_progress.repository

import com.example.autonix_work_in_progress.models.TripData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class TripRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    companion object {
        private const val TRIPS_COLLECTION = "completed_trips"
    }

    /** Save trip to Firestore */
    suspend fun saveTrip(trip: TripData): Result<String> {
        return try {
            val userId = auth.currentUser?.uid ?: "anonymous_user"
            val tripId = firestore.collection(TRIPS_COLLECTION).document().id
            val tripWithUser = trip.copy(tripId = tripId, userId = userId)

            firestore.collection(TRIPS_COLLECTION)
                .document(tripId)
                .set(tripWithUser)
                .await()

            Result.success(tripId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Get all trips for current user */
    suspend fun getUserTrips(): Result<List<TripData>> {
        return try {
            val userId = auth.currentUser?.uid ?: "anonymous_user"
            val snapshot = firestore.collection(TRIPS_COLLECTION)
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()

            val trips = snapshot.toObjects(TripData::class.java)
            Result.success(trips)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Get trip by ID */
    suspend fun getTripById(tripId: String): Result<TripData?> {
        return try {
            val snapshot = firestore.collection(TRIPS_COLLECTION)
                .document(tripId)
                .get()
                .await()

            Result.success(snapshot.toObject(TripData::class.java))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Delete trip by ID */
    suspend fun deleteTripById(tripId: String): Result<Unit> {
        return try {
            firestore.collection(TRIPS_COLLECTION)
                .document(tripId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Update trip route title (if needed) */
    suspend fun updateRouteTitle(tripId: String, newTitle: String): Result<Unit> {
        return try {
            firestore.collection(TRIPS_COLLECTION)
                .document(tripId)
                .update("routeTitle", newTitle)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
