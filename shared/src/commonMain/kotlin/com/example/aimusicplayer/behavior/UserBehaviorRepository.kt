package com.example.aimusicplayer.behavior

/**
 * 用户行为数据读取接口。
 *
 * 供推荐引擎查询聚合后的用户画像数据。
 * 平台无关 —— 数据由 [UserBehaviorTracker] 写入，此处只读。
 */
class UserBehaviorRepository(private val tracker: UserBehaviorTracker) {

    /**
     * 获取用户最常播放的 top N 歌手（按播放次数降序）。
     */
    suspend fun getTopArtists(n: Int = 5): List<String> {
        val behaviors = tracker.getAllBehaviors()
        return behaviors
            .groupBy { it.artist }
            .filter { it.key.isNotBlank() }
            .mapValues { (_, list) -> list.size }
            .entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key }
    }

    /**
     * 获取用户最喜欢的 top N 歌手（按喜欢标记次数降序）。
     */
    suspend fun getTopLikedArtists(n: Int = 5): List<String> {
        val behaviors = tracker.getAllBehaviors()
        return behaviors
            .filter { it.liked }
            .groupBy { it.artist }
            .filter { it.key.isNotBlank() }
            .mapValues { (_, list) -> list.size }
            .entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key }
    }

    /**
     * 获取用户最常听的流派 top N。
     */
    suspend fun getTopGenres(n: Int = 3): List<String> {
        val behaviors = tracker.getAllBehaviors()
        return behaviors
            .filter { it.genre != null && it.genre.isNotBlank() }
            .groupBy { it.genre!! }
            .mapValues { (_, list) -> list.size }
            .entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key }
    }

    /**
     * 获取所有已播放过的歌曲 ID（用于排除）。
     */
    suspend fun getPlayedSongIds(): Set<String> {
        return tracker.getAllBehaviors()
            .map { "${it.songId}|${it.platform}" }
            .toSet()
    }

    /**
     * 获取用户标记喜欢的歌曲 ID 集合。
     */
    suspend fun getLikedSongIds(): Set<String> {
        return tracker.getAllBehaviors()
            .filter { it.liked }
            .map { "${it.songId}|${it.platform}" }
            .toSet()
    }

    /**
     * 获取用户最近 N 毫秒内播放过的歌曲 ID。
     */
    suspend fun getRecentSongIds(withinMs: Long): Set<String> {
        val cutoff = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - withinMs
        return tracker.getAllBehaviors()
            .filter { it.playedAt >= cutoff }
            .map { "${it.songId}|${it.platform}" }
            .toSet()
    }

    /**
     * 获取所有行为记录。
     */
    suspend fun getAllBehaviors(): List<UserBehavior> = tracker.getAllBehaviors()

    /**
     * 获取行为记录总数（用于冷启动判断）。
     */
    suspend fun getBehaviorCount(): Int = tracker.getBehaviorCount()
}
