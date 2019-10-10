package com.xmy.service.impl;

import com.alibaba.dubbo.config.annotation.Service;
import com.xmy.api.IGoodsService;
import com.xmy.constant.ShopCode;
import com.xmy.entity.Result;
import com.xmy.exception.CastException;
import com.xmy.mapper.ShopGoodsMapper;
import com.xmy.mapper.ShopOrderGoodsLogMapper;
import com.xmy.pojo.ShopGoods;
import com.xmy.pojo.ShopGoodsExample;
import com.xmy.pojo.ShopOrderGoodsLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author xmy
 * @date 2019-10-09 21:49
 */
@Slf4j
@SuppressWarnings("ALL")
@Component
@Service(interfaceClass = IGoodsService.class)
public class GoodsServiceImpl implements IGoodsService {
    @Autowired
    private ShopGoodsMapper shopGoodsMapper;

    @Autowired
    private ShopOrderGoodsLogMapper shopOrderGoodsLogMapper;

    @Override
    public ShopGoods findOne(Long goodsId) {
        if (goodsId == null) {
            CastException.cast(ShopCode.SHOP_REQUEST_PARAMETER_VALID);
        }
        return shopGoodsMapper.selectByPrimaryKey(goodsId);
    }

    @Override
    public Result reduceGoodsNum(ShopOrderGoodsLog orderGoodsLog) {
        // 空指针过滤
        if (orderGoodsLog == null ||
                orderGoodsLog.getOrderId() == null ||
                orderGoodsLog.getGoodsId() == null ||
                orderGoodsLog.getGoodsNumber() == null ||
                orderGoodsLog.getGoodsNumber().intValue() <= 0) {
            CastException.cast(ShopCode.SHOP_REQUEST_PARAMETER_VALID);
        }
        ShopGoods goods = shopGoodsMapper.selectByPrimaryKey(orderGoodsLog.getGoodsId());
        // 判断库存是否充足
        if (goods.getGoodsNumber() < orderGoodsLog.getGoodsNumber()) {
            CastException.cast(ShopCode.SHOP_REDUCE_GOODS_NUM_FAIL);
        }
        Integer goodsnum = goods.getGoodsNumber();
        goods.setGoodsNumber(goods.getGoodsNumber() - orderGoodsLog.getGoodsNumber());

        // 分布式并发问题 ,使用乐观锁 <方案待提升>
        ShopGoodsExample shopGoodsExample = new ShopGoodsExample();
        ShopGoodsExample.Criteria criteria = shopGoodsExample.createCriteria();
        criteria.andGoodsIdEqualTo(goods.getGoodsId());
        criteria.andGoodsNumberEqualTo(goodsnum);
        int r = shopGoodsMapper.updateByExample(goods, shopGoodsExample);
        if (r <= 0) {
            // 未修改成功
            log.info("库存数量并发修改,稍后处理");
            // 引入MQ 解决问题 或者 循环
        }

        // 记录库存日志 库存数量  负数:扣库存
        orderGoodsLog.setGoodsNumber(-(orderGoodsLog.getGoodsNumber()));
        orderGoodsLog.setLogTime(new Date());
        shopOrderGoodsLogMapper.insert(orderGoodsLog);
        log.info("扣减库存成功");
        return new Result(ShopCode.SHOP_SUCCESS.getSuccess(), ShopCode.SHOP_SUCCESS.getMessage());
    }
}
