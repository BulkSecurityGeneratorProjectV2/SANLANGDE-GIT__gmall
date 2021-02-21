package com.atguigu.gmall.search;

import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.feign.GmallPmsClient;
import com.atguigu.gmall.search.feign.GmallWmsClient;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchAttrValue;
import com.atguigu.gmall.search.repository.GoodsRepository;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import io.jsonwebtoken.lang.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
class GmallSearchApplicationTests {

    @Autowired
    private ElasticsearchRestTemplate restTemplate;
    @Autowired
    private GoodsRepository repository;
    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallWmsClient wmsClient;

    @Test
    void saveGoods() {

        Integer pageNum = 1;
        Integer pageSize = 100;
        do {
            //数据导入所需接口
            //1、分页查询已上架的SPU信息v
            PageParamVo pageParamVo = new PageParamVo(pageNum, pageSize, null);
            ResponseVo<List<SpuEntity>> listResponseVo = this.pmsClient.querySpuByPageJson(pageParamVo);
            List<SpuEntity> spuEntities = listResponseVo.getData();
            if (Collections.isEmpty(spuEntities)) {
                break;
            }
            spuEntities.forEach(spuEntity -> {
                ResponseVo<List<SkuEntity>> skuResponseVo = pmsClient.querySkuListBySpuId(spuEntity.getId());
                List<SkuEntity> skuEntities = skuResponseVo.getData();
                if (!Collections.isEmpty(skuEntities)) {

                    List<Goods> goodsList = new ArrayList<>();
                    goodsList.addAll(skuEntities.stream().map(sku -> {
                        Goods goods = new Goods();
                        //sku相关信息
                        goods.setSkuId(sku.getId());
                        goods.setTitle(sku.getTitle());
                        goods.setSubTitle(sku.getSubtitle());
                        goods.setDefaultImage(sku.getDefaultImage());
                        goods.setPrice(sku.getPrice().doubleValue());

                        ResponseVo<List<WareSkuEntity>> wareSkuResponseVo = this.wmsClient.queryWareSkuEntitiesBySkuId(sku.getId());
                        List<WareSkuEntity> wareSkuEntities = wareSkuResponseVo.getData();
                        if (!Collections.isEmpty(wareSkuEntities)) {
                            goods.setSales(wareSkuEntities.stream().map(WareSkuEntity::getSales).reduce((a, b) -> a + b).get());
                            goods.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
                        }
                        //品牌
                        ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(sku.getBrandId());
                        BrandEntity brandEntity = brandEntityResponseVo.getData();
                        if (brandEntity != null) {
                            goods.setBrandId(brandEntity.getId());
                            goods.setBrandName(brandEntity.getName());
                            goods.setLogo(brandEntity.getLogo());
                        }

                        //分类
                        ResponseVo<CategoryEntity> categoryEntityResponseVo = this.pmsClient.queryCategoryById(sku.getCategoryId());
                        CategoryEntity categoryEntity = categoryEntityResponseVo.getData();
                        if (categoryEntity != null) {
                            goods.setCategoryId(categoryEntity.getId());
                            goods.setCategoryName(categoryEntity.getName());
                        }
                        //spu创建时间
                        goods.setCreateTime(spuEntity.getCreateTime());
                        //规格参数
                        List<SearchAttrValue> attrValues =new ArrayList<>();

                        ResponseVo<List<SkuAttrValueEntity>> querySkuAttrValueResponseVo = this.pmsClient.querySkuAttrValueByCidAndSkuId(sku.getCategoryId(), sku.getId());
                        List<SkuAttrValueEntity> skuAttrValueEntities = querySkuAttrValueResponseVo.getData();
                        if(!Collections.isEmpty(skuAttrValueEntities)){
                            attrValues.addAll(skuAttrValueEntities.stream().map(skuAttrValueEntity -> {
                                SearchAttrValue searchAttrValue=new SearchAttrValue();
                                BeanUtils.copyProperties(skuAttrValueEntity,searchAttrValue);
                                return searchAttrValue;
                            }).collect(Collectors.toList()));
                        }
                        ResponseVo<List<SpuAttrValueEntity>> querySpuAttrValueResponseVo = this.pmsClient.querySpuAttrValueByCidAndSpuId(sku.getCategoryId(), sku.getSpuId());
                        List<SpuAttrValueEntity> spuAttrValueEntities = querySpuAttrValueResponseVo.getData();
                        if(!Collections.isEmpty(spuAttrValueEntities)){

                            attrValues.addAll(spuAttrValueEntities.stream().map(spuAttrValueEntity -> {
                                SearchAttrValue searchAttrValue=new SearchAttrValue();
                                BeanUtils.copyProperties(spuAttrValueEntity,searchAttrValue);
                                return searchAttrValue;
                            }).collect(Collectors.toList()));

                        }
                        goods.setSearchAttrs(attrValues);
                        return goods;
                    }).collect(Collectors.toList()));
                    this.repository.saveAll(goodsList);
                }

            });
            //2、根据spuId 查询对应SKU信息（接口已存在）v
            //3、根据skuId 查询sku的库存信息 v
            //4、根据品牌ID查询品牌信息 v
            //5、根据分类ID查询分类信息 v
            /*
            6.1 根据分类id查询搜索属性的规格参数
            6.2 根据skuId和attrId查询销售类型的检索规格参数及值
            6.3 根据spuId和attrId查询基本类型的检索规格参数及值
             */

            //pageSize赋值
            pageSize = spuEntities.size();
            pageNum++;
        } while (pageSize == 100);


    }

    @Test
    void contextLoads() {
        restTemplate.createIndex(Goods.class);
        restTemplate.putMapping(Goods.class);
    }

}
