package com.example.redbook.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.redbook.MainActivity

/**
 * 一次通知点击携带的跳转信息。
 * type: like | favorite | comment | reply | follow | dm
 */
data class PendingNotif(
    val type: String,
    val actorUid: String,
    val actorName: String,
    val actorAvatar: String = "",
    val postId: String = "",
    val commentId: String = "",
    val conversationId: String = "",
    val contentText: String = ""
) {
    val isBlank: Boolean
        get() = type.isBlank() || actorUid.isBlank()
}

object NotifClickRouter {

    const val ACTION_NOTIF_TAP = "com.example.redbook.action.NOTIF_TAP"

    private const val EXTRA_TYPE = "notif_type"
    private const val EXTRA_ACTOR_UID = "notif_actor_uid"
    private const val EXTRA_ACTOR_NAME = "notif_actor_name"
    private const val EXTRA_ACTOR_AVATAR = "notif_actor_avatar"
    private const val EXTRA_POST_ID = "notif_post_id"
    private const val EXTRA_COMMENT_ID = "notif_comment_id"
    private const val EXTRA_CONVERSATION_ID = "notif_conversation_id"

    fun buildContentIntent(context: Context, n: PendingNotif): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_NOTIF_TAP)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_TYPE, n.type)
            .putExtra(EXTRA_ACTOR_UID, n.actorUid)
            .putExtra(EXTRA_ACTOR_NAME, n.actorName)
            .putExtra(EXTRA_ACTOR_AVATAR, n.actorAvatar)
            .putExtra(EXTRA_POST_ID, n.postId)
            .putExtra(EXTRA_COMMENT_ID, n.commentId)
            .putExtra(EXTRA_CONVERSATION_ID, n.conversationId)
        return PendingIntent.getActivity(
            context,
            n.actorUid.hashCode() and 0x7fffffff,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun parse(intent: Intent?): PendingNotif {
        if (intent == null) return PendingNotif("", "", "")
        val n = PendingNotif(
            type = intent.getStringExtra(EXTRA_TYPE) ?: "",
            actorUid = intent.getStringExtra(EXTRA_ACTOR_UID) ?: "",
            actorName = intent.getStringExtra(EXTRA_ACTOR_NAME) ?: "",
            actorAvatar = intent.getStringExtra(EXTRA_ACTOR_AVATAR) ?: "",
            postId = intent.getStringExtra(EXTRA_POST_ID) ?: "",
            commentId = intent.getStringExtra(EXTRA_COMMENT_ID) ?: "",
            conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID) ?: ""
        )
        return n
    }
}
