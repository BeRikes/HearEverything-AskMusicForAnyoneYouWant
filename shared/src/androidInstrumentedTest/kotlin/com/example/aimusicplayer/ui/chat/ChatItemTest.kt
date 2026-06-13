package com.example.aimusicplayer.ui.chat

import com.example.aimusicplayer.ai.AiCommand
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class ChatItemTest {

    @Test
    fun user_message_has_correct_fields() {
        val msg = ChatItem.UserMessage(text = "播放夜曲")
        assertEquals("播放夜曲", msg.text)
        assertTrue(msg.timestamp > 0)
    }

    @Test
    fun ai_message_with_command() {
        val cmd = AiCommand(action = "search_play", query = "夜曲")
        val msg = ChatItem.AiMessage(text = "好的，正在搜索夜曲", command = cmd)
        assertEquals("好的，正在搜索夜曲", msg.text)
        assertNotNull(msg.command)
        assertEquals("search_play", msg.command?.action)
    }

    @Test
    fun ai_message_without_command() {
        val msg = ChatItem.AiMessage(text = "你好！")
        assertEquals("你好！", msg.text)
        assertNull(msg.command)
    }

    @Test
    fun system_message() {
        val msg = ChatItem.SystemMessage(text = "API Key 未配置")
        assertEquals("API Key 未配置", msg.text)
    }

    @Test
    fun chat_items_are_sealed_class_instances() {
        val user = ChatItem.UserMessage("hi")
        val ai = ChatItem.AiMessage("hello")
        val sys = ChatItem.SystemMessage("error")
        assertTrue(user is ChatItem)
        assertTrue(ai is ChatItem)
        assertTrue(sys is ChatItem)
    }
}
