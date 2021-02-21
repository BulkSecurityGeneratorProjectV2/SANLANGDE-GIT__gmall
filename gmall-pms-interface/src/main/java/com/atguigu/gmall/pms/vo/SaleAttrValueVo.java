package com.atguigu.gmall.pms.vo;

import lombok.Data;

import java.util.Set;

@Data
public class SaleAttrValueVo {

    private Long attrId;
    private String attrName;
    //使用set集合去重
    private Set<String> attrValues;

}
