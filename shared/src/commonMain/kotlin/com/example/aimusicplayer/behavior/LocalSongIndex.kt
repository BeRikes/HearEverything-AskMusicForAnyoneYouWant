package com.example.aimusicplayer.behavior

/**
 * 本地歌曲元数据索引（推荐候选池）。
 *
 * 存储已知歌曲的元数据（不含播放 URL），用于推荐时的候选人筛选。
 * 数据来源：播放历史自动累积 + MusicBrainz 批量导入 + 搜索结果自动入库。
 */
expect class LocalSongIndex {
    /** 插入或忽略一条歌曲元数据 */
    suspend fun insertOrIgnore(entry: SongIndexEntry)

    /** 批量插入 */
    suspend fun insertOrIgnoreAll(entries: List<SongIndexEntry>)

    /** 按歌手查询全部歌曲 */
    suspend fun queryByArtist(artist: String): List<SongIndexEntry>

    /** 按流派查询，按全局热度降序 */
    suspend fun queryByGenre(genre: String, limit: Int = 50): List<SongIndexEntry>

    /** 按全局热度降序获取 top N 歌曲 */
    suspend fun queryHot(limit: Int = 50): List<SongIndexEntry>

    /** 获取候选池总大小 */
    suspend fun count(): Int

    /** 获取所有歌曲 */
    suspend fun getAll(): List<SongIndexEntry>

    /** 获取未过期的歌曲（createdAt 在 ttlHours 小时以内） */
    suspend fun getNonExpired(ttlHours: Int): List<SongIndexEntry>

    /** 删除过期歌曲（createdAt 超过 ttlHours 小时） */
    suspend fun removeExpired(ttlHours: Int)

    /** 删除指定歌曲 */
    suspend fun removeByKey(songId: String, platform: String)

    /** 清除全部索引 */
    suspend fun clearAll()
}
