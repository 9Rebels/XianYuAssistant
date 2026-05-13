package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuPoiCache;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 发布地图点位缓存 Mapper
 */
@Mapper
public interface XianyuPoiCacheMapper extends BaseMapper<XianyuPoiCache> {

    @Select("SELECT * FROM xianyu_poi_cache " +
            "WHERE xianyu_account_id = #{accountId} AND division_id = #{divisionId} " +
            "LIMIT 1")
    XianyuPoiCache selectByAccountAndDivision(
            @Param("accountId") Long accountId,
            @Param("divisionId") Integer divisionId);

    @Select("<script>" +
            "SELECT * FROM xianyu_poi_cache " +
            "WHERE xianyu_account_id = #{accountId} " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (prov LIKE '%' || #{keyword} || '%' " +
            "OR city LIKE '%' || #{keyword} || '%' " +
            "OR area LIKE '%' || #{keyword} || '%' " +
            "OR poi_name LIKE '%' || #{keyword} || '%') " +
            "</if>" +
            "ORDER BY is_default DESC, updated_time DESC, id DESC " +
            "</script>")
    List<XianyuPoiCache> selectByAccountId(
            @Param("accountId") Long accountId,
            @Param("keyword") String keyword);

    @Select("SELECT * FROM xianyu_poi_cache " +
            "WHERE xianyu_account_id = #{accountId} " +
            "ORDER BY is_default DESC, updated_time DESC, id DESC LIMIT 1")
    XianyuPoiCache selectDefaultByAccountId(@Param("accountId") Long accountId);

    @Update("UPDATE xianyu_poi_cache SET is_default = 0 WHERE xianyu_account_id = #{accountId}")
    int clearDefault(@Param("accountId") Long accountId);

    @Insert("INSERT INTO xianyu_poi_cache (" +
            "xianyu_account_id, division_id, prov, city, area, poi_id, poi_name, gps, " +
            "latitude, longitude, address, source, is_default, created_time, updated_time) " +
            "VALUES (#{xianyuAccountId}, #{divisionId}, #{prov}, #{city}, #{area}, #{poiId}, " +
            "#{poiName}, #{gps}, #{latitude}, #{longitude}, #{address}, #{source}, #{isDefault}, " +
            "strftime('%Y-%m-%d %H:%M:%S', 'now', '+8 hours'), " +
            "strftime('%Y-%m-%d %H:%M:%S', 'now', '+8 hours')) " +
            "ON CONFLICT(xianyu_account_id, division_id) DO UPDATE SET " +
            "prov = excluded.prov, city = excluded.city, area = excluded.area, " +
            "poi_id = excluded.poi_id, poi_name = excluded.poi_name, gps = excluded.gps, " +
            "latitude = excluded.latitude, longitude = excluded.longitude, address = excluded.address, " +
            "source = excluded.source, is_default = excluded.is_default, " +
            "updated_time = strftime('%Y-%m-%d %H:%M:%S', 'now', '+8 hours')")
    int upsert(XianyuPoiCache cache);
}
