package com.atguigu.gmall.pms.vo;

import com.atguigu.gmall.pms.entity.SpuEntity;
import lombok.Data;

import java.util.List;

@Data
public class SpuVo extends SpuEntity {

    //pm_spu_desc图片描述
    private List<String> spuImages;
    //pms_spu_attr_valueSPU基本属性
    private List<SpuAttrValueVo> baseAttrs;
    //SKU信息
    private List<SkuVo> skus;

}
