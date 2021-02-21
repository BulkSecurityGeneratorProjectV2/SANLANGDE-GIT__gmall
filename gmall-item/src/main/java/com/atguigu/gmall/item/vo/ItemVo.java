package com.atguigu.gmall.item.vo;

import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.entity.SkuImagesEntity;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ItemVo {
    //面包屑：三级分类  接口信息：根据三级分类id查询123级分类   V
    private List<CategoryEntity> categorise;

    //面包屑：品牌    接口信息：查询品牌信息 V
    private Long brandId;
    private String brandName;

    //面包屑：spu信息     接口信息：根据spuId查询spu信息 V
    private Long spuId;
    private String spuName;

    //中间：sku信息  接口信息：根据skuId查询sku信息 v
    private Long skuId;
    private String title;
    private String subTitle;
    private BigDecimal price;
    private String defaultImage;
    private Integer weight;

    //图片列表：sku-images   接口信息：根据skuId 查询图片列表 V
    private List<SkuImagesEntity> images;

    //中间：营销信息   接口信息：根据skuId 查询营销信息 V
    private List<ItemSaleVo> sales;
    //库存信息：是否有货     接口信息：根据skuId 查询是否有货 V
    private Boolean store=false;

    //sku销售属性   接口信息：根据sku中的spuId查询spu下所有sku的销售属性   V
    //[{attrId:1},{attrName:机身颜色},{attrValues:['金色','白色','黑色']}],
    // [{},{},{}],
    // [{attrId:3},{attrName:机身存储},{attrValues:['64GB','128GB','256GB','512GB']}]
    private List<SaleAttrValueVo> saleAttrs;

    //当前sku销售属性:属性高亮    接口信息：根据skuId 查询当前sku下的销售属性  V
    //{1:'白色',2:'8GB',3:'256GB'}
    private Map<Long,String> saleAttr;

    //销售属性组合和sku的映射关系   接口信息：根据skuId查询spu下所有销售属性组合与skuId的映射关系   V
    // {'金色，8GB，64GB':1,'黑色','12GB','256GB':2}
    private String skuJsons;

    //spu海报信息   接口信息：根据spuId 查询海报信息     V
    private List<String> spuImages;

    //规格参数分组列表  接口信息：根据分类ID、spuId、skuId 查询分组参数列表及值
    private List<ItemGroupVo> groups;

}
