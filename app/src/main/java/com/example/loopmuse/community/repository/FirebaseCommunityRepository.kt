package com.example.loopmuse.community.repository

import com.example.loopmuse.community.model.Report
import com.example.loopmuse.community.model.PostComment
import com.example.loopmuse.community.model.PostVote
import com.example.loopmuse.community.model.SongPost
import com.example.loopmuse.community.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
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
    private fun commentsCollection(postId: String) = postsCollection.document(postId).collection("comments")
    private fun votesCollection(postId: String) = postsCollection.document(postId).collection("votes")
    private fun commentVotesCollection(postId: String, commentId: String) =
        commentsCollection(postId).document(commentId).collection("votes")
    
    override fun getCurrentUid(): String? {
        return auth.currentUser?.takeIf { user ->
            !user.isAnonymous && user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        }?.uid
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
            val postDocument = postsCollection.document(postId)
            val postSnapshot = postDocument.get().await()
            if (!postSnapshot.exists()) return Result.success(Unit)
            if (postSnapshot.getBoolean("isDeleting") != true) {
                postDocument.update("isDeleting", true).await()
            }
            // Firestore does not remove nested vote collections with a comment or post.
            while (true) {
                val comments = commentsCollection(postId).limit(200).get().await().documents
                if (comments.isEmpty()) break
                comments.forEach { comment ->
                    deleteCollectionDocuments(commentVotesCollection(postId, comment.id))
                }
                val batch = firestore.batch()
                comments.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            deleteCollectionDocuments(votesCollection(postId))
            postDocument.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun deleteCollectionDocuments(collection: CollectionReference) {
        while (true) {
            val documents = collection.limit(400).get().await().documents
            if (documents.isEmpty()) break
            val batch = firestore.batch()
            documents.forEach { batch.delete(it.reference) }
            batch.commit().await()
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

    override fun getComments(postId: String): Flow<List<PostComment>> = callbackFlow {
        val subscription = commentsCollection(postId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toObject(PostComment::class.java) })
                }
            }
        awaitClose { subscription.remove() }
    }

    override suspend fun addComment(postId: String, message: String, parentCommentId: String): Result<Unit> {
        return try {
            val uid = getCurrentUid() ?: return Result.failure(Exception("Not logged in"))
            val comment = PostComment(
                postId = postId, parentCommentId = parentCommentId,
                message = message, uid = uid
            )
            commentsCollection(postId).document(comment.id).set(comment).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteComment(postId: String, commentId: String): Result<Unit> {
        return try {
            val commentDocument = commentsCollection(postId).document(commentId)
            val snapshot = commentDocument.get().await()
            if (!snapshot.exists()) return Result.success(Unit)
            if (snapshot.getBoolean("isDeleted") != true) {
                commentDocument.update(mapOf("message" to "", "isDeleted" to true)).await()
            }
            deleteCollectionDocuments(commentVotesCollection(postId, commentId))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reportComment(postId: String, commentId: String, reason: String): Result<Unit> {
        return try {
            val uid = getCurrentUid() ?: return Result.failure(Exception("Not logged in"))
            val report = Report(postId = postId, commentId = commentId, reporterUid = uid, reason = reason)
            reportsCollection.document(report.id).set(report).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getCommentVotes(postId: String, commentId: String): Flow<List<PostVote>> = callbackFlow {
        val subscription = commentVotesCollection(postId, commentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toObject(PostVote::class.java) })
                }
            }
        awaitClose { subscription.remove() }
    }

    override suspend fun setCommentVote(postId: String, commentId: String, value: Int): Result<Unit> {
        return try {
            val uid = getCurrentUid() ?: return Result.failure(Exception("Not logged in"))
            val voteDocument = commentVotesCollection(postId, commentId).document(uid)
            if (value == 0) voteDocument.delete().await()
            else voteDocument.set(PostVote(uid = uid, value = value)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getVotes(postId: String): Flow<List<PostVote>> = callbackFlow {
        val subscription = votesCollection(postId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                trySend(snapshot.documents.mapNotNull { it.toObject(PostVote::class.java) })
            }
        }
        awaitClose { subscription.remove() }
    }

    override suspend fun setVote(postId: String, value: Int): Result<Unit> {
        return try {
            val uid = getCurrentUid() ?: return Result.failure(Exception("Not logged in"))
            val voteDocument = votesCollection(postId).document(uid)
            if (value == 0) voteDocument.delete().await()
            else voteDocument.set(PostVote(uid = uid, value = value)).await()
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
            usersCollection.document(uid)
                .set(mapOf("uid" to uid, "isBanned" to true), SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
