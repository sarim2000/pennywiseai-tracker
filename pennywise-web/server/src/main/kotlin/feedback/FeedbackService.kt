package com.example.feedback

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlinx.serialization.encodeToString
import org.postgresql.util.PGobject
import java.sql.Connection
import java.sql.Statement
import java.sql.Types
import java.sql.Timestamp
import java.util.UUID

class FeedbackService(private val connection: Connection) {
    companion object {
        private const val CREATE_TABLE_FEEDBACK_ITEMS = """
            CREATE TABLE IF NOT EXISTS feedback_items (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                item_type VARCHAR(20) NOT NULL CHECK (item_type IN ('BUG', 'FEATURE')),
                source VARCHAR(20) NOT NULL CHECK (source IN ('ANDROID_APP', 'WEB_TOOL')),
                title VARCHAR(200) NOT NULL,
                description TEXT NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'PLANNED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED')),
                vote_count INTEGER NOT NULL DEFAULT 0,
                submitter_fingerprint VARCHAR(64) NOT NULL,
                metadata JSONB,
                created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
            )
        """

        private const val CREATE_TABLE_VOTES = """
            CREATE TABLE IF NOT EXISTS votes (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                feedback_item_id UUID NOT NULL REFERENCES feedback_items(id) ON DELETE CASCADE,
                voter_fingerprint VARCHAR(64) NOT NULL,
                created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(feedback_item_id, voter_fingerprint)
            )
        """

        private const val CREATE_TABLE_RATE_LIMITS = """
            CREATE TABLE IF NOT EXISTS rate_limits (
                fingerprint VARCHAR(64) PRIMARY KEY,
                submission_count INTEGER NOT NULL DEFAULT 1,
                last_submission_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
            )
        """

        private const val CREATE_INDEX_FEEDBACK_TYPE = """
            CREATE INDEX IF NOT EXISTS idx_feedback_type ON feedback_items(item_type)
        """

        private const val CREATE_INDEX_FEEDBACK_STATUS = """
            CREATE INDEX IF NOT EXISTS idx_feedback_status ON feedback_items(status)
        """

        private const val CREATE_INDEX_FEEDBACK_VOTES = """
            CREATE INDEX IF NOT EXISTS idx_feedback_votes ON feedback_items(vote_count DESC)
        """

        private const val CREATE_INDEX_VOTES_ITEM = """
            CREATE INDEX IF NOT EXISTS idx_votes_item ON votes(feedback_item_id)
        """

        private const val MAX_SUBMISSIONS_PER_HOUR = 5
        private const val RATE_LIMIT_WINDOW_MS = 60 * 60 * 1000L // 1 hour
    }

    init {
        try {
            val statement = connection.createStatement()
            statement.executeUpdate(CREATE_TABLE_FEEDBACK_ITEMS)
            statement.executeUpdate(CREATE_TABLE_VOTES)
            statement.executeUpdate(CREATE_TABLE_RATE_LIMITS)
            statement.executeUpdate(CREATE_INDEX_FEEDBACK_TYPE)
            statement.executeUpdate(CREATE_INDEX_FEEDBACK_STATUS)
            statement.executeUpdate(CREATE_INDEX_FEEDBACK_VOTES)
            statement.executeUpdate(CREATE_INDEX_VOTES_ITEM)
            statement.close()
        } catch (e: Exception) {
            println("Feedback table initialization: ${e.message}")
        }
    }

    suspend fun createFeedback(
        request: CreateFeedbackRequest,
        fingerprint: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!checkRateLimit(fingerprint)) {
            return@withContext Result.failure(Exception("Rate limit exceeded. Please try again later."))
        }

        val itemType = try {
            FeedbackItemType.valueOf(request.itemType.uppercase())
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Invalid item type"))
        }

        val source = try {
            FeedbackSource.valueOf(request.source.uppercase())
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Invalid source"))
        }

        if (request.title.isBlank() || request.title.length > 200) {
            return@withContext Result.failure(Exception("Title must be 1-200 characters"))
        }

        if (request.description.isBlank()) {
            return@withContext Result.failure(Exception("Description is required"))
        }

        val sql = """
            INSERT INTO feedback_items (item_type, source, title, description, submitter_fingerprint, metadata, vote_count)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, 1)
            RETURNING id
        """

        val statement = connection.prepareStatement(sql)
        try {
            statement.setString(1, itemType.name)
            statement.setString(2, source.name)
            statement.setString(3, request.title.trim())
            statement.setString(4, request.description.trim())
            statement.setString(5, fingerprint)

            if (request.metadata != null) {
                val pgObject = PGobject()
                pgObject.type = "jsonb"
                pgObject.value = Json.encodeToString(request.metadata)
                statement.setObject(6, pgObject)
            } else {
                statement.setNull(6, Types.OTHER)
            }

            val rs = statement.executeQuery()
            if (rs.next()) {
                val feedbackId = rs.getString(1)

                addVote(feedbackId, fingerprint)
                updateRateLimit(fingerprint)

                return@withContext Result.success(feedbackId)
            }
            return@withContext Result.failure(Exception("Failed to create feedback"))
        } finally {
            statement.close()
        }
    }

    suspend fun getFeedbackItems(
        fingerprint: String,
        typeFilter: FeedbackItemType? = null,
        statusFilter: FeedbackStatus? = null,
        forRoadmap: Boolean = false
    ): List<FeedbackItem> = withContext(Dispatchers.IO) {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Any>()

        if (typeFilter != null) {
            conditions.add("item_type = ?")
            params.add(typeFilter.name)
        }

        if (statusFilter != null) {
            conditions.add("status = ?")
            params.add(statusFilter.name)
        }

        if (forRoadmap) {
            conditions.add("status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED')")
        }

        val whereClause = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        val orderBy = if (forRoadmap) {
            "ORDER BY CASE status WHEN 'IN_PROGRESS' THEN 1 WHEN 'PLANNED' THEN 2 WHEN 'COMPLETED' THEN 3 END, vote_count DESC"
        } else {
            "ORDER BY created_at DESC"
        }

        val sql = """
            SELECT f.id, f.item_type, f.source, f.title, f.description, f.status,
                   f.vote_count, f.metadata, f.created_at, f.updated_at,
                   EXISTS(SELECT 1 FROM votes v WHERE v.feedback_item_id = f.id AND v.voter_fingerprint = ?) as has_voted
            FROM feedback_items f
            $whereClause
            $orderBy
            LIMIT 100
        """

        val statement = connection.prepareStatement(sql)
        try {
            statement.setString(1, fingerprint)
            var paramIndex = 2
            for (param in params) {
                statement.setString(paramIndex++, param as String)
            }

            val rs = statement.executeQuery()
            val items = mutableListOf<FeedbackItem>()

            while (rs.next()) {
                val metadataStr = rs.getString("metadata")
                val metadata = if (metadataStr != null) {
                    Json.parseToJsonElement(metadataStr).jsonObject
                } else null

                items.add(
                    FeedbackItem(
                        id = rs.getString("id"),
                        itemType = FeedbackItemType.valueOf(rs.getString("item_type")),
                        source = FeedbackSource.valueOf(rs.getString("source")),
                        title = rs.getString("title"),
                        description = rs.getString("description"),
                        status = FeedbackStatus.valueOf(rs.getString("status")),
                        voteCount = rs.getInt("vote_count"),
                        hasVoted = rs.getBoolean("has_voted"),
                        metadata = metadata,
                        createdAt = rs.getTimestamp("created_at").toString(),
                        updatedAt = rs.getTimestamp("updated_at").toString()
                    )
                )
            }

            return@withContext items
        } finally {
            statement.close()
        }
    }

    suspend fun toggleVote(feedbackId: String, fingerprint: String): VoteResponse = withContext(Dispatchers.IO) {
        val checkSql = "SELECT 1 FROM votes WHERE feedback_item_id = ?::uuid AND voter_fingerprint = ?"
        val checkStmt = connection.prepareStatement(checkSql)
        try {
            checkStmt.setString(1, feedbackId)
            checkStmt.setString(2, fingerprint)
            val rs = checkStmt.executeQuery()
            val hasVoted = rs.next()

            if (hasVoted) {
                removeVote(feedbackId, fingerprint)
            } else {
                addVote(feedbackId, fingerprint)
            }

            val newCount = getVoteCount(feedbackId)
            return@withContext VoteResponse(
                success = true,
                message = if (hasVoted) "Vote removed" else "Vote added",
                newVoteCount = newCount,
                hasVoted = !hasVoted
            )
        } catch (e: Exception) {
            return@withContext VoteResponse(
                success = false,
                message = "Failed to toggle vote: ${e.message}"
            )
        } finally {
            checkStmt.close()
        }
    }

    private fun addVote(feedbackId: String, fingerprint: String) {
        val insertSql = """
            INSERT INTO votes (feedback_item_id, voter_fingerprint)
            VALUES (?::uuid, ?)
            ON CONFLICT DO NOTHING
        """
        val updateSql = """
            UPDATE feedback_items
            SET vote_count = vote_count + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?::uuid
        """

        val insertStmt = connection.prepareStatement(insertSql)
        val updateStmt = connection.prepareStatement(updateSql)
        try {
            insertStmt.setString(1, feedbackId)
            insertStmt.setString(2, fingerprint)
            val inserted = insertStmt.executeUpdate()

            if (inserted > 0) {
                updateStmt.setString(1, feedbackId)
                updateStmt.executeUpdate()
            }
        } finally {
            insertStmt.close()
            updateStmt.close()
        }
    }

    private fun removeVote(feedbackId: String, fingerprint: String) {
        val deleteSql = "DELETE FROM votes WHERE feedback_item_id = ?::uuid AND voter_fingerprint = ?"
        val updateSql = """
            UPDATE feedback_items
            SET vote_count = GREATEST(0, vote_count - 1), updated_at = CURRENT_TIMESTAMP
            WHERE id = ?::uuid
        """

        val deleteStmt = connection.prepareStatement(deleteSql)
        val updateStmt = connection.prepareStatement(updateSql)
        try {
            deleteStmt.setString(1, feedbackId)
            deleteStmt.setString(2, fingerprint)
            val deleted = deleteStmt.executeUpdate()

            if (deleted > 0) {
                updateStmt.setString(1, feedbackId)
                updateStmt.executeUpdate()
            }
        } finally {
            deleteStmt.close()
            updateStmt.close()
        }
    }

    private fun getVoteCount(feedbackId: String): Int {
        val sql = "SELECT vote_count FROM feedback_items WHERE id = ?::uuid"
        val stmt = connection.prepareStatement(sql)
        try {
            stmt.setString(1, feedbackId)
            val rs = stmt.executeQuery()
            return if (rs.next()) rs.getInt("vote_count") else 0
        } finally {
            stmt.close()
        }
    }

    private fun checkRateLimit(fingerprint: String): Boolean {
        val sql = """
            SELECT submission_count, last_submission_at
            FROM rate_limits
            WHERE fingerprint = ?
        """
        val stmt = connection.prepareStatement(sql)
        try {
            stmt.setString(1, fingerprint)
            val rs = stmt.executeQuery()
            if (!rs.next()) return true

            val count = rs.getInt("submission_count")
            val lastSubmission = rs.getTimestamp("last_submission_at").time
            val now = System.currentTimeMillis()

            if (now - lastSubmission > RATE_LIMIT_WINDOW_MS) {
                resetRateLimit(fingerprint)
                return true
            }

            return count < MAX_SUBMISSIONS_PER_HOUR
        } finally {
            stmt.close()
        }
    }

    private fun updateRateLimit(fingerprint: String) {
        val sql = """
            INSERT INTO rate_limits (fingerprint, submission_count, last_submission_at)
            VALUES (?, 1, CURRENT_TIMESTAMP)
            ON CONFLICT (fingerprint) DO UPDATE SET
                submission_count = rate_limits.submission_count + 1,
                last_submission_at = CURRENT_TIMESTAMP
        """
        val stmt = connection.prepareStatement(sql)
        try {
            stmt.setString(1, fingerprint)
            stmt.executeUpdate()
        } finally {
            stmt.close()
        }
    }

    private fun resetRateLimit(fingerprint: String) {
        val sql = """
            UPDATE rate_limits
            SET submission_count = 0, last_submission_at = CURRENT_TIMESTAMP
            WHERE fingerprint = ?
        """
        val stmt = connection.prepareStatement(sql)
        try {
            stmt.setString(1, fingerprint)
            stmt.executeUpdate()
        } finally {
            stmt.close()
        }
    }
}
