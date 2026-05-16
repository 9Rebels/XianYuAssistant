package com.feijimiao.xianyuassistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 闲鱼商品信息Mapper
 */
@Mapper
public interface XianyuGoodsInfoMapper extends BaseMapper<XianyuGoodsInfo> {
    @Select("SELECT xianyu_account_id FROM xianyu_goods WHERE xy_good_id = #{xyGoodsId} LIMIT 1")
    Long selectOwnerAccountIdByGoodsId(@Param("xyGoodsId") String xyGoodsId);

    @Update("UPDATE xianyu_goods SET xy_good_id = #{newXyGoodId}, detail_url = #{detailUrl}, " +
            "status = #{status}, updated_time = datetime('now', 'localtime') " +
            "WHERE xianyu_account_id = #{xianyuAccountId} AND xy_good_id = #{oldXyGoodId}")
    int replaceGoodsId(@Param("xianyuAccountId") Long xianyuAccountId,
                       @Param("oldXyGoodId") String oldXyGoodId,
                       @Param("newXyGoodId") String newXyGoodId,
                       @Param("detailUrl") String detailUrl,
                       @Param("status") Integer status);
}
