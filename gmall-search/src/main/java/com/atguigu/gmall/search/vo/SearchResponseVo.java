package com.atguigu.gmall.search.vo;

import com.atguigu.gmall.pms.entity.BrandEntity;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.search.pojo.Goods;
import lombok.Data;

import java.util.List;

@Data
public class SearchResponseVo {
    //品牌列表的渲染
    private List<BrandEntity> brands;
    //分类列表的渲染
    private List<CategoryEntity> categories;
    //规格参数渲染
    private List<SearchResponseAttrVo> filters;
    //分页所需数据
    private Long total;
    private Integer pageNum;
    private Integer pageSize;

    private List<Goods> goods;  //列表数据

}
