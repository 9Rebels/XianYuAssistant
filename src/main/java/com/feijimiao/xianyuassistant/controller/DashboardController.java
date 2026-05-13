package com.feijimiao.xianyuassistant.controller;

import com.feijimiao.xianyuassistant.common.ResultObject;
import com.feijimiao.xianyuassistant.entity.XianyuGoodsInfo;
import com.feijimiao.xianyuassistant.mapper.XianyuAccountMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsInfoMapper;
import com.feijimiao.xianyuassistant.mapper.XianyuGoodsOrderMapper;
import com.feijimiao.xianyuassistant.controller.dto.DashboardStatsRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 首页仪表板控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    @Autowired
    private XianyuAccountMapper accountMapper;
    
    @Autowired
    private XianyuGoodsInfoMapper goodsMapper;

    @Autowired
    private XianyuGoodsOrderMapper orderMapper;

    /**
     * 获取首页统计数据
     */
    @PostMapping("/stats")
    public ResultObject<DashboardStatsRespDTO> getDashboardStats() {
        try {
            log.info("获取首页统计数据");
            
            // 获取账号总数
            int accountCount = accountMapper.selectCount(null).intValue();
            
            // 获取商品总数
            int itemCount = goodsMapper.selectCount(null).intValue();
            
            // 获取在售商品数 (status = 0)
            int sellingItemCount = goodsMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<XianyuGoodsInfo>()
                    .eq("status", 0)
            ).intValue();
            
            // 获取已下架商品数 (status = 1)
            int offShelfItemCount = goodsMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<XianyuGoodsInfo>()
                    .eq("status", 1)
            ).intValue();
            
            // 获取已售出商品数 (status = 2)
            int soldItemCount = goodsMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<XianyuGoodsInfo>()
                    .eq("status", 2)
            ).intValue();
            
            // 构造响应数据
            DashboardStatsRespDTO respDTO = new DashboardStatsRespDTO();
            respDTO.setAccountCount(accountCount);
            respDTO.setItemCount(itemCount);
            respDTO.setSellingItemCount(sellingItemCount);
            respDTO.setOffShelfItemCount(offShelfItemCount);
            respDTO.setSoldItemCount(soldItemCount);
            respDTO.setDeliverySuccessCount(orderMapper.countDeliverySuccess());
            respDTO.setDeliveryFailCount(orderMapper.countDeliveryFail());
            respDTO.setPendingDeliveryCount(orderMapper.countPendingDelivery());
            
            return ResultObject.success(respDTO);
        } catch (Exception e) {
            log.error("获取首页统计数据失败", e);
            return ResultObject.failed("获取首页统计数据失败: " + e.getMessage());
        }
    }
}
