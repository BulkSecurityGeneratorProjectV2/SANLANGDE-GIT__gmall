package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.feign.GmallSmsClient;
import com.atguigu.gmall.pms.mapper.SkuMapper;
import com.atguigu.gmall.pms.mapper.SpuDescMapper;
import com.atguigu.gmall.pms.service.*;
import com.atguigu.gmall.pms.vo.SkuVo;
import com.atguigu.gmall.pms.vo.SpuAttrValueVo;
import com.atguigu.gmall.pms.vo.SpuVo;
import com.atguigu.gmall.sms.vo.SkuSaleVo;
import io.jsonwebtoken.lang.Collections;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.pms.mapper.SpuMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@Service("spuService")
@Slf4j
public class SpuServiceImpl extends ServiceImpl<SpuMapper, SpuEntity> implements SpuService {

    @Autowired
    private SpuDescService spuDescService;
    @Autowired
    private SpuAttrValueService baseAttrsService;
    @Autowired
    private SkuMapper skuMapper;
    @Autowired
    private SkuImagesService skuImagesService;
    @Autowired
    private SkuAttrValueService saleAttrsService;
    @Autowired
    private GmallSmsClient gmallSmsClient;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<SpuEntity>()
        );

        return new PageResultVo(page);
    }

    @Override
    public PageResultVo querySpuByCategory(Long categoryId, PageParamVo paramVo) {
        QueryWrapper<SpuEntity> wrapper = new QueryWrapper<>();
        if (categoryId != 0) {
            wrapper.eq("category_id", categoryId);
        }
        String key = paramVo.getKey();
        if (!StringUtils.isBlank(key)) {
            wrapper.and(w -> w.eq("id", key).or().like("name", key));
        }

        IPage<SpuEntity> page = this.page(
                paramVo.getPage(),
                wrapper
        );

        return new PageResultVo(page);
    }

    @GlobalTransactional
    @Override
    public void bigSave(SpuVo spu) {
        //1、先保存SPU相关信息
        //1.1、保存spu表信息
        Long spuId = saveSpu(spu);
        //1.2、保存spu_desc表信息
        this.spuDescService.saveSpuDesc(spu, spuId);

        //TimeUnit.SECONDS.sleep(4);

        //
        //1.3、保存spu_attr_value表信息
        saveBaseAttrs(spu, spuId);
        //2、再保存SKU相关信息
        saveSku(spu, spuId);

        //int i=10/0;

        sendMessage(spuId,"insert");

    }

    private void saveSku(SpuVo spu, Long spuId) {
        List<SkuVo> skus = spu.getSkus();
        if(Collections.isEmpty(skus)){
            return;
        }
        skus.forEach(sku->{
            //2.1、保存sku表信息
            sku.setSpuId(spuId);
            sku.setCategoryId(spu.getCategoryId());
            sku.setBrandId(spu.getBrandId());
            List<String> images = sku.getImages();
            if(!Collections.isEmpty(images)) {
                sku.setDefaultImage(StringUtils.isBlank(sku.getDefaultImage())?images.get(0):sku.getDefaultImage());
            }
            skuMapper.insert(sku);
            Long skuId = sku.getId();
            //2.2、保存sku_desc表信息
            if(!Collections.isEmpty(images)) {
              this.skuImagesService.saveBatch(images.stream().map(image->{
                  SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                  skuImagesEntity.setUrl(image);
                  skuImagesEntity.setSkuId(skuId);
                  skuImagesEntity.setDefaultStatus(StringUtils.equals(sku.getDefaultImage(),image)?1:0);
                  return skuImagesEntity;
              }).collect(Collectors.toList()));
            }
            //2.3、保存sku_attr_value表信息
            List<SkuAttrValueEntity> saleAttrs = sku.getSaleAttrs();
            if(!Collections.isEmpty(saleAttrs)){
                saleAttrs.forEach(skuAttrValueEntity -> skuAttrValueEntity.setSkuId(skuId));
                saleAttrsService.saveBatch(saleAttrs);
            }
            //3、最后保存营销相关信息
            SkuSaleVo skuSaleVo = new SkuSaleVo();
            BeanUtils.copyProperties(sku,skuSaleVo);
            skuSaleVo.setSkuId(skuId);
            this.gmallSmsClient.saveSales(skuSaleVo);
        });
    }

    private void saveBaseAttrs(SpuVo spu, Long spuId) {
        List<SpuAttrValueVo> baseAttrs = spu.getBaseAttrs();
        if(!Collections.isEmpty(baseAttrs)){
            this.baseAttrsService.saveBatch(baseAttrs.stream().map(spuAttrVo->{
                SpuAttrValueEntity spuAttrValueEntity = new SpuAttrValueEntity();
                BeanUtils.copyProperties(spuAttrVo,spuAttrValueEntity);
                spuAttrValueEntity.setSpuId(spuId);
                return spuAttrValueEntity;
            }).collect(Collectors.toList()));
        }
    }

    private Long saveSpu(SpuVo spu) {
        spu.setCreateTime(new Date());
        spu.setUpdateTime(spu.getCreateTime());
        this.save(spu);
        return spu.getId();
    }

    private void sendMessage(Long id, String type){
        // 发送消息
        try {
            this.rabbitTemplate.convertAndSend("item_exchange", "item." + type, id);
        } catch (Exception e) {
            log.error("{}商品消息发送异常，商品id：{}", type, id, e);
        }
    }

//    public static void main(String[] args) {
//        List<User> userList= Arrays.asList(
//                new User(1l,"张三丰",201),
//                new User(3l,"张无忌",18),
//                new User(2l,"张翠山",28),
//                new User(4l,"赵敏",17)
//        );
//        //过滤filter
//        userList.stream().filter(user -> user.getAge()>20).collect(Collectors.toList()).forEach(System.out::println);
//
//        //集合之间的转化map
//        userList.stream().map(User::getName).collect(Collectors.toList()).forEach(System.out::println);
//
//        userList.stream().map(user -> {
//            Person person = new Person();
//            person.setId(user.getId());
//            person.setPersonName(user.getName());
//            person.setAge(user.getAge());
//            return person;
//        }).collect(Collectors.toList());
//        //求和reduce
//        System.out.println(userList.stream().map(User::getAge).reduce(Integer::sum));
//    }
}
//
//@Data
//@NoArgsConstructor
//@AllArgsConstructor
//class User{
//    private Long id;
//    private String name;
//    private Integer age;
//}
//
//@Data
//class Person{
//    private Long id;
//    private String personName;
//    private Integer age;
//}
