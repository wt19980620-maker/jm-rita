package com.par9uet.jm.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.par9uet.jm.database.model.DownloadComic
import com.par9uet.jm.database.model.UpdateComicCover
import com.par9uet.jm.database.model.UpdateComicProgress
import com.par9uet.jm.database.model.UpdateComicStatus
import com.par9uet.jm.database.model.UpdateComicZipPath
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadComicDao {
    @Query("""
        SELECT * FROM download_comics
        WHERE ((:status = 'downloading' AND status IN ('pending', 'downloading', 'error', 'paused'))
            OR (:status = 'complete' AND status = 'complete'))
            AND (:query = '' OR instr(lower(name), lower(:query)) > 0
                OR instr(lower(authorList), lower(:query)) > 0
                OR instr(CAST(id AS TEXT), :query) > 0)
        ORDER BY CASE WHEN :sort = 'NEWEST' THEN createTime END DESC,
            CASE WHEN :sort = 'OLDEST' THEN createTime END ASC,
            CASE WHEN :sort = 'NAME' THEN name END COLLATE NOCASE ASC,
            id DESC
    """)
    fun getDownloadList(status: String, query: String, sort: String): PagingSource<Int, DownloadComic>

    @Query("SELECT EXISTS(SELECT 1 FROM download_comics WHERE id = :comicId)")
    fun isExist(comicId: Int): Flow<Boolean>

    @Query("SELECT * FROM download_comics WHERE id = :comicId LIMIT 1")
    suspend fun getById(comicId: Int): DownloadComic?

    @Query("UPDATE download_comics SET status = :status, errorMessage = :message WHERE id = :comicId")
    suspend fun setState(comicId: Int, status: String, message: String = "")

    @Query("UPDATE download_comics SET status = :status, errorMessage = :message WHERE id = :comicId AND status IN ('pending', 'downloading')")
    suspend fun setActiveState(comicId: Int, status: String, message: String = ""): Int

    @Update(entity = DownloadComic::class)
    suspend fun updateCover(updateComicCover: UpdateComicCover)

    @Update(entity = DownloadComic::class)
    suspend fun updateStatus(updateComicStatus: UpdateComicStatus)

    @Update(entity = DownloadComic::class)
    suspend fun updateProgress(updateComicProgress: UpdateComicProgress)

    @Update(entity = DownloadComic::class)
    suspend fun updateZipPath(updateComicZipPath: UpdateComicZipPath)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: DownloadComic)

    @Update
    suspend fun update(task: DownloadComic)

    @Delete
    suspend fun delete(task: DownloadComic)
}
