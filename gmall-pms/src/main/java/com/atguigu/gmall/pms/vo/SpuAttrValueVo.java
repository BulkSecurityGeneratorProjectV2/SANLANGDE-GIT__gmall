package com.atguigu.gmall.pms.vo;

import com.atguigu.gmall.pms.entity.SpuAttrValueEntity;
import io.jsonwebtoken.lang.Collections;
import lombok.Data;
import org.apache.commons.lang.StringUtils;

import java.util.List;

public class SpuAttrValueVo extends SpuAttrValueEntity {

    //基本属性值
    private List<String> valueSelected;

    public void setValueSelected(List<String> valueSelected) {
        if(Collections.isEmpty(valueSelected)){
            return;
        }
        //父类私有属性通过set方法赋值
        this.setAttrValue(StringUtils.join(valueSelected,","));
    }
}
