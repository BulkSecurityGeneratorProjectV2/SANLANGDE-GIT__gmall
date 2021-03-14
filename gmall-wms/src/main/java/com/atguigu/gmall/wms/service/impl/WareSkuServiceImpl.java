package com.atguigu.gmall.wms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.exception.OrderException;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.wms.mapper.WareSkuMapper;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.service.WareSkuService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuMapper, WareSkuEntity> implements WareSkuService {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private WareSkuMapper wareSkuMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    //分布式锁前缀
    private static  final String LOCK_PREFIX="stock:lock:";
    //所库存缓存前缀
    private static  final String STOCK_LOCK_INFO="stock:lock:info:";

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<WareSkuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<WareSkuEntity>()
        );

        return new PageResultVo(page);
    }

    @Transactional
    @Override
    public List<SkuLockVo> checkAndLock(List<SkuLockVo> skuLockVos, String orderToken) {

        if(CollectionUtils.isEmpty(skuLockVos)){
            throw new OrderException("您没有要购买的商品！");
        }
        //遍历所有商品，验库存、所库存，
        skuLockVos.forEach(lockVo -> {
            this.checkLock(lockVo);
        });
        //如果有一个商品锁定失败，所有锁定成功的库存都有解锁
        if (skuLockVos.stream().anyMatch(skuLockVo -> !skuLockVo.getLock())) {
            //获取所有锁库成功的记录
            skuLockVos.stream().filter(SkuLockVo::getLock).forEach(lockVo -> {
                this.wareSkuMapper.unlock(lockVo.getWareSkuId(),lockVo.getCount());
            });
            //响应锁定状态数据
            return skuLockVos;
        }
        //如果所有商品都锁定成功，需要缓存锁定信息到redis；以便将来解锁库存
        // 以orderToken 作为key，以lockVos作为value
        this.redisTemplate.opsForValue().set(STOCK_LOCK_INFO + orderToken, JSON.toJSONString(skuLockVos));
        this.rabbitTemplate.convertAndSend("ORDER_EXCHANGE","stock.delay",orderToken);
        return null;
    }

    private void checkLock(SkuLockVo lockVo){
        RLock fairLock = this.redissonClient.getFairLock(LOCK_PREFIX + lockVo.getSkuId());

        fairLock.lock();

        try {
            //验库存：查询
            List<WareSkuEntity> wareSkuEntities = this.wareSkuMapper.check(lockVo.getSkuId(), lockVo.getCount());
            if(CollectionUtils.isEmpty(wareSkuEntities)){
                lockVo.setLock(false);
                return;
            }
            //使用第一条库存记录
            WareSkuEntity wareSkuEntity = wareSkuEntities.get(0);
            //所库存：更新
            Long wareSkuId = wareSkuEntity.getId();
            if (this.wareSkuMapper.lock(wareSkuId,lockVo.getCount())==1) {
                lockVo.setLock(true);
                lockVo.setWareSkuId(wareSkuId); //如果锁库成功，记录锁库的记录的id，以便以后解锁库存
            }else {
                lockVo.setLock(false);
            }
        } finally {
            fairLock.unlock();
        }


    }

}