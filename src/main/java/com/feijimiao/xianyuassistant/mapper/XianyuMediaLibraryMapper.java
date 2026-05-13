package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuMediaLibrary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 媒体库素材 Mapper
 */
@Mapper
public interface XianyuMediaLibraryMapper extends BaseMapper<XianyuMediaLibrary> {

    @Select("<script>" +
            "SELECT * FROM xianyu_media_library " +
            "WHERE xianyu_account_id = #{accountId} " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (file_name LIKE '%' || #{keyword} || '%' OR media_url LIKE '%' || #{keyword} || '%') " +
            "</if>" +
            "ORDER BY id DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<XianyuMediaLibrary> selectByAccountId(
            @Param("accountId") Long accountId,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("<script>" +
            "SELECT COUNT(*) FROM xianyu_media_library " +
            "WHERE xianyu_account_id = #{accountId} " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (file_name LIKE '%' || #{keyword} || '%' OR media_url LIKE '%' || #{keyword} || '%') " +
            "</if>" +
            "</script>")
    long countByAccountId(@Param("accountId") Long accountId, @Param("keyword") String keyword);
}
