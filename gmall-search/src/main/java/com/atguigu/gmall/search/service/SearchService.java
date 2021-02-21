package com.atguigu.gmall.search.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.feign.GmallPmsClient;
import com.atguigu.gmall.search.feign.GmallWmsClient;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchAttrValue;
import com.atguigu.gmall.search.repository.GoodsRepository;
import com.atguigu.gmall.search.vo.SearchParamVo;
import com.atguigu.gmall.search.vo.SearchResponseAttrVo;
import com.atguigu.gmall.search.vo.SearchResponseVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import io.jsonwebtoken.lang.Collections;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SearchService {

    @Autowired
    private RestHighLevelClient restHighLevelClient;
    @Autowired
    private GoodsRepository repository;
    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallWmsClient wmsClient;

    public SearchResponseVo search(SearchParamVo searchParamVo) {
        try {
            if (StringUtils.isBlank(searchParamVo.getKeyword())) {
                //TODO: 广告
                return null;
            }
            SearchRequest searchRequest = new SearchRequest(new String[]{"goods"}, buildDsl(searchParamVo));
            SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            //todo:解析响应数据
            SearchResponseVo searchResponseVo = this.parseResult(response);
            //设置分页页码、条数
            searchResponseVo.setPageSize(searchParamVo.getPageSize());
            searchResponseVo.setPageNum(searchParamVo.getPageSize());
            return searchResponseVo;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private SearchResponseVo parseResult(SearchResponse response) {
        SearchResponseVo searchResponseVo = new SearchResponseVo();
        //解析hits
        SearchHits hits = response.getHits();
        //设置总记录数
        searchResponseVo.setTotal(hits.getTotalHits());
        //设置商品对象
        SearchHit[] hitsHits = hits.getHits();
        List<Goods> goodsList = Stream.of(hitsHits).map(hit -> {
            //反序列化Goods对象
            Goods goods = JSON.parseObject(hit.getSourceAsString(), Goods.class);
            Map<String, HighlightField> highlightFields = hit.getHighlightFields();
            HighlightField highlightField = highlightFields.get("title");
            if (highlightField != null) {
                goods.setTitle(highlightField.getFragments()[0].toString());
            }
            return goods;
        }).collect(Collectors.toList());
        searchResponseVo.setGoods(goodsList);
        //2、解析aggregation,获取品牌列表、分类列表、规格参数列表
        //把聚合的结果集以map的形式解析：key-聚合名称，value-聚合的内容
        Aggregations aggregations = response.getAggregations();
        if (aggregations != null) {
            Map<String, Aggregation> aggregationMap = response.getAggregations().asMap();
            //2.1、获取品牌
            ParsedLongTerms brandIdAgg = (ParsedLongTerms) aggregationMap.get("brandIdAgg");
            List<? extends Terms.Bucket> brandIdAggBuckets = brandIdAgg.getBuckets();
            if (!Collections.isEmpty(brandIdAggBuckets)) {
                List<BrandEntity> brandEntityList = brandIdAggBuckets.stream().map(bucket -> {
                    BrandEntity brandEntity = new BrandEntity();
                    //外层的桶的id就是品牌的id
                    brandEntity.setId(bucket.getKeyAsNumber().longValue());
                    //获取桶中的子聚合：品牌的名称、品牌的logo
                    //每个品牌的名称和logo的子聚合中有且仅有一个桶
                    Map<String, Aggregation> brandSubAggMap = bucket.getAggregations().asMap();
                    ParsedStringTerms brandNameAgg = (ParsedStringTerms) brandSubAggMap.get("brandNameAgg");
                    List<? extends Terms.Bucket> brandNameAggBuckets = brandNameAgg.getBuckets();
                    if (!Collections.isEmpty(brandNameAggBuckets))
                        brandEntity.setName(brandNameAggBuckets.get(0).getKeyAsString());
                    ParsedStringTerms logoAgg = (ParsedStringTerms) brandSubAggMap.get("logoAgg");
                    List<? extends Terms.Bucket> logoAggBuckets = logoAgg.getBuckets();
                    if (!Collections.isEmpty(logoAggBuckets))
                        brandEntity.setLogo(logoAggBuckets.get(0).getKeyAsString());
                    return brandEntity;
                }).collect(Collectors.toList());
                searchResponseVo.setBrands(brandEntityList);
            }
            //2.1、获取分类
            ParsedLongTerms categoryIdAgg = (ParsedLongTerms) aggregationMap.get("categoryIdAgg");
            List<? extends Terms.Bucket> categoryIdAggBuckets = categoryIdAgg.getBuckets();
            if (!Collections.isEmpty(categoryIdAggBuckets)) {
                List<CategoryEntity> categoryEntities = categoryIdAggBuckets.stream().map(bucket -> {
                    CategoryEntity categoryEntity = new CategoryEntity();
                    categoryEntity.setId(bucket.getKeyAsNumber().longValue());
                    //获取分类名称的子聚合
                    Map<String, Aggregation> categoryNameMap = bucket.getAggregations().asMap();
                    ParsedStringTerms categoryNameAgg = (ParsedStringTerms) categoryNameMap.get("categoryNameAgg");
                    List<? extends Terms.Bucket> categoryNameAggBuckets = categoryNameAgg.getBuckets();
                    if (!Collections.isEmpty(categoryNameAggBuckets))
                        categoryEntity.setName(categoryNameAggBuckets.get(0).getKeyAsString());
                    return categoryEntity;
                }).collect(Collectors.toList());

                searchResponseVo.setCategories(categoryEntities);
            }
            //2.3、获取规格参数
            //获取规格参数的嵌套聚合
            ParsedNested attrAgg = (ParsedNested) aggregationMap.get("attrAgg");
            //获取嵌套聚合中的attrId的聚合
            ParsedLongTerms attrIdAgg = (ParsedLongTerms) attrAgg.getAggregations().get("attrIdAgg");
            //获取attrId聚合中的桶集合，获取所有的检索类型的规格参数
            List<? extends Terms.Bucket> attrIdAggBuckets = attrIdAgg.getBuckets();
            //有些商品或者是有些关键字可能没有检索类型的规格参数
            if (!Collections.isEmpty(attrIdAggBuckets)) {
                //把attrId聚合中的桶的集合转化成List<SearchResponseAttrVo>
                List<SearchResponseAttrVo> searchResponseAttrVos = attrIdAggBuckets.stream().map(bucket -> {
                    //把每个桶转成 SearchResponseAttrVo 对象
                    SearchResponseAttrVo responseAttrVo = new SearchResponseAttrVo();
                    //桶中的key就是attrId
                    responseAttrVo.setAttrId(bucket.getKeyAsNumber().longValue());
                    Map<String, Aggregation> subAttrMap = bucket.getAggregations().asMap();
                    ParsedStringTerms attrNameAgg = (ParsedStringTerms) subAttrMap.get("attrNameAgg");

                    responseAttrVo.setAttrName(attrNameAgg.getBuckets().get(0).getKeyAsString());

                    ParsedStringTerms attrValueAgg = (ParsedStringTerms) subAttrMap.get("attrValueAgg");
                    List<? extends Terms.Bucket> attrValueAggBuckets = attrValueAgg.getBuckets();
                    if (!Collections.isEmpty(attrValueAggBuckets)) {
                        List<String> attrValue = attrValueAggBuckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                        responseAttrVo.setAttrValues(attrValue);
                    }

                    return responseAttrVo;
                }).collect(Collectors.toList());

                searchResponseVo.setFilters(searchResponseAttrVos);
            }
        }

        return searchResponseVo;
    }

    private SearchSourceBuilder buildDsl(SearchParamVo searchParamVo) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        String keyword = searchParamVo.getKeyword();

        //1、构建查询条件（bool查询）
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        searchSourceBuilder.query(boolQueryBuilder);
        //1.1 匹配查询
        boolQueryBuilder.must(QueryBuilders.matchQuery("title", keyword).operator(Operator.AND));
        //1.2 过滤查询-品牌过滤
        List<Long> brandId = searchParamVo.getBrandId();
        if (!Collections.isEmpty(brandId))
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId", brandId));
        //1.3 分类过滤
        List<Long> categoryId = searchParamVo.getCategoryId();
        if (!Collections.isEmpty(categoryId))
            boolQueryBuilder.filter(QueryBuilders.termsQuery("categoryId", categoryId));
        //1.4 价格区间过滤
        Double priceFrom = searchParamVo.getPriceFrom();    //起始价格
        Double priceTo = searchParamVo.getPriceTo();    //种植价格
        if (priceFrom != null || priceTo != null) {
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("price");
            if (priceFrom != null) {
                rangeQuery.gte(priceFrom);
            }
            if (priceTo != null) {
                rangeQuery.lte(priceTo);
            }
            boolQueryBuilder.filter(rangeQuery);
        }
        //1.5 是否有货
        Boolean store = searchParamVo.getStore();
        if (store != null && store) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("store", store));
        }
        //1.6 规格参数嵌套过滤,// props=5:高通-麒麟,6:骁龙865-硅谷1000
        List<String> props = searchParamVo.getProps();
        if (!Collections.isEmpty(props)) {
            props.forEach(prop -> {
                //用冒号分隔字符串
                String[] propStr = StringUtils.split(prop, ":");
                if (propStr != null && propStr.length == 2) {
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    boolQuery.must(QueryBuilders.termQuery("searchAttrs.attrId", propStr[0]));
                    String[] attrValues = StringUtils.split(propStr[1], "-");
                    boolQuery.must(QueryBuilders.termsQuery("searchAttrs.attrValue", attrValues));

                    // 每一个prop对应一个嵌套过滤：1-对应嵌套过滤中的path，2-嵌套过滤中的query，3-得分模式
                    boolQueryBuilder.filter(QueryBuilders.nestedQuery("searchAttrs", boolQuery, ScoreMode.None));
                }

            });
        }
        //2、构建排序 0-默认，得分降序；1-按价格升序；2-按价格降序；3-按创建时间降序；4-按销量降序
        Integer sort = searchParamVo.getSort();
        switch (sort) {
            case 1:
                searchSourceBuilder.sort("price", SortOrder.ASC);
                break;
            case 2:
                searchSourceBuilder.sort("price", SortOrder.DESC);
                break;
            case 3:
                searchSourceBuilder.sort("createTime", SortOrder.DESC);
                break;
            case 4:
                searchSourceBuilder.sort("sales", SortOrder.DESC);
                break;
        }
        //3、构建分页
        searchSourceBuilder.from((searchParamVo.getPageNum() - 1) * searchParamVo.getPageSize());
        searchSourceBuilder.size(searchParamVo.getPageSize());
        //4、构建高亮
        searchSourceBuilder.highlighter(
                new HighlightBuilder()
                        .field("title")
                        .preTags("<font style='color:red;'>")
                        .postTags("</font>"));
        //5、构建聚合
        //5.1 构建品牌的聚合
        searchSourceBuilder.aggregation(AggregationBuilders.terms("brandIdAgg").field("brandId")
                .subAggregation(
                        AggregationBuilders.terms("brandNameAgg").field("brandName"))
                .subAggregation(
                        AggregationBuilders.terms("logoAgg").field("logo"))
        );
        //5.2 构建分类的聚合
        searchSourceBuilder.aggregation(AggregationBuilders.terms("categoryIdAgg").field("categoryId")
                .subAggregation(
                        AggregationBuilders.terms("categoryNameAgg").field("categoryName")
                )
        );
        //5.3 构建规格参数的聚合
        searchSourceBuilder.aggregation(AggregationBuilders.nested("attrAgg", "searchAttrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("searchAttrs.attrId")
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("searchAttrs.attrName"))
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("searchAttrs.attrValue")))
        );
        // 6 结果集过滤
        searchSourceBuilder.fetchSource(new String[]{"skuId", "defaultImage", "subTitle", "title", "price"}, null);
        //System.out.println(searchSourceBuilder);
        return searchSourceBuilder;
    }

    public void createIndex(Long id) {
        ResponseVo<SpuEntity> listResponseVo = this.pmsClient.querySpuById(id);
        SpuEntity spuEntity = listResponseVo.getData();

        if(spuEntity!=null) {
            ResponseVo<List<SkuEntity>> skuResponseVo = pmsClient.querySkuListBySpuId(id);
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

        }
    }
}
