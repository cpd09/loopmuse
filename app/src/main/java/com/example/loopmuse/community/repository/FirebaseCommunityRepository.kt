package com.example.loopmuse.community.repository

import com.example.loopmuse.community.model.Report
import com.example.loopmuse.community.model.SongPost
import com.example.loopmuse.community.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseCommunityRepository : CommunityRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val postsCollection = firestore.collection("posts")
    private val reportsCollection = firestore.collection("reports")
    private val usersCollection = firestore.collection("users")
    
    override suspend fun signInAnonymously(): String? {
        auth.currentUser?.uid?.let { return it }
        return try {
            val result = auth.signInAnonymously().await()
            result.user?.uid
        } catch (e: Exception) {
            null
        }
    }

    override fun getCurrentUid(): String? {
        return auth.currentUser?.uid
    }

    override fun getPosts(): Flow<List<SongPost>> = callbackFlow {
        val subscription = postsCollection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val posts = snapshot.documents.mapNotNull { it.toObject(SongPost::class.java) }
                    trySend(posts)
                }
            }
        awaitClose { subscription.remove() }
    }

    override suspend fun addPost(post: SongPost): Result<Unit> {
        return try {
            postsCollection.document(post.id).set(post).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deletePost(postId: String): Result<Unit> {
        return try {
            postsCollection.document(postId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reportPost(postId: String, reason: String): Result<Unit> {
        return try {
            val uid = getCurrentUid() ?: return Result.failure(Exception("Not logged in"))
            val report = Report(postId = postId, reporterUid = uid, reason = reason)
            reportsCollection.document(report.id).set(report).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserProfile(uid: String): Result<UserProfile> {
        return try {
            val doc = usersCollection.document(uid).get().await()
            val profile = doc.toObject(UserProfile::class.java)
            if (profile != null) {
                Result.success(profile)
            } else {
                // If not exists, return default
                Result.success(UserProfile(uid = uid, isAdmin = false, isBanned = false))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun banUser(uid: String): Result<Unit> {
        return try {
            usersCollection.document(uid).set(UserProfile(uid = uid, isBanned = true)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
