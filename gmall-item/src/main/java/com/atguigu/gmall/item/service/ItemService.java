package com.atguigu.gmall.item.service;

import com.atguigu.gmall.common.bean.ResponseVo;
import com.atguigu.gmall.item.feign.GmallPmsClient;
import com.atguigu.gmall.item.feign.GmallSmsClient;
import com.atguigu.gmall.item.feign.GmallWmsClient;
import com.atguigu.gmall.item.vo.ItemVo;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import com.atguigu.gmall.pms.vo.SaleAttrValueVo;
import com.atguigu.gmall.sms.vo.ItemSaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import io.jsonwebtoken.lang.Collections;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class ItemService {

    @Autowired
    private GmallPmsClient pmsClient;

    @Autowired
    private GmallWmsClient wmsClient;

    @Autowired
    private GmallSmsClient smsClient;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Autowired
    private TemplateEngine templateEngine;

    public ItemVo loadData(Long skuId) {
        ItemVo itemVo = new ItemVo();

        //获取sku相关信息
        CompletableFuture<SkuEntity> skuFuture = CompletableFuture.supplyAsync(() -> {
            ResponseVo<SkuEntity> skuEntityResponseVo = this.pmsClient.querySkuById(skuId);
            SkuEntity skuEntity = skuEntityResponseVo.getData();
            if (skuEntity == null) {
                return null;
            }
            itemVo.setSkuId(skuId);
            itemVo.setTitle(skuEntity.getTitle());
            itemVo.setSubTitle(skuEntity.getSubtitle());
            itemVo.setDefaultImage(skuEntity.getDefaultImage());
            itemVo.setPrice(skuEntity.getPrice());
            itemVo.setWeight(skuEntity.getWeight());
            return skuEntity;
        }, threadPoolExecutor);

        //设置分类信息
        CompletableFuture<Void> catesFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<List<CategoryEntity>> categoryResponseVo = this.pmsClient.query123CategoriseByCid3(skuEntity.getCategoryId());
            List<CategoryEntity> categoryEntities = categoryResponseVo.getData();
            if (!Collections.isEmpty(categoryEntities)) {
                itemVo.setCategorise(categoryEntities);
            }
        }, threadPoolExecutor);

        //品牌信息
        CompletableFuture<Void> brandFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<BrandEntity> brandEntityResponseVo = this.pmsClient.queryBrandById(skuEntity.getBrandId());
            BrandEntity brandEntity = brandEntityResponseVo.getData();
            if (brandEntity != null) {
                itemVo.setBrandId(brandEntity.getId());
                itemVo.setBrandName(brandEntity.getName());
            }
        }, threadPoolExecutor);

        CompletableFuture<Void> spuFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<SpuEntity> spuEntityResponseVo = this.pmsClient.querySpuById(skuEntity.getSpuId());
            SpuEntity spuEntity = spuEntityResponseVo.getData();
            if (spuEntity != null) {
                //spu信息
                itemVo.setSpuId(spuEntity.getId());
                itemVo.setSpuName(spuEntity.getName());
            }
        }, threadPoolExecutor);

        // sku图片列表
        CompletableFuture<Void> skuImagesFuture = CompletableFuture.runAsync(() -> {
            ResponseVo<List<SkuImagesEntity>> imagesResponseVo = this.pmsClient.queryImagesBySkuId(skuId);
            List<SkuImagesEntity> skuImagesEntities = imagesResponseVo.getData();
            if (!Collections.isEmpty(skuImagesEntities)) {
                itemVo.setImages(skuImagesEntities);
            }
        }, threadPoolExecutor);

        CompletableFuture<Void> descFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<SpuDescEntity> spuDescEntityResponseVo = this.pmsClient.querySpuDescById(skuEntity.getSpuId());
            SpuDescEntity spuDescEntity = spuDescEntityResponseVo.getData();
            if (spuDescEntity != null && !StringUtils.isBlank(spuDescEntity.getDecript())) {
                //海报信息
                itemVo.setSpuImages(Arrays.asList(StringUtils.split(spuDescEntity.getDecript(), ",")));
            }
        }, threadPoolExecutor);

        //sku营销信息
        CompletableFuture<Void> salesFuture = CompletableFuture.runAsync(() -> {
            ResponseVo<List<ItemSaleVo>> saleResponseVo = this.smsClient.querySalesBySkuId(skuId);
            List<ItemSaleVo> saleVos = saleResponseVo.getData();
            itemVo.setSales(saleVos);
        }, threadPoolExecutor);

        //库存信息
        CompletableFuture<Void> storeFuture = CompletableFuture.runAsync(() -> {
            ResponseVo<List<WareSkuEntity>> wareResponseVo = this.wmsClient.queryWareSkuEntitiesBySkuId(skuId);
            List<WareSkuEntity> wareSkuEntities = wareResponseVo.getData();
            if (!Collections.isEmpty(wareSkuEntities)) {
                itemVo.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() - wareSkuEntity.getStockLocked() > 0));
            }
        }, threadPoolExecutor);

        //所有销售属性
        CompletableFuture<Void> saleAttrsFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<List<SaleAttrValueVo>> saleAttrsResponseVo = this.pmsClient.querySaleAttrsBySpuId(skuEntity.getSpuId());
            List<SaleAttrValueVo> saleAttrValueVos = saleAttrsResponseVo.getData();
            itemVo.setSaleAttrs(saleAttrValueVos);
        }, threadPoolExecutor);

        //当前sku的销售属性
        CompletableFuture<Void> saleAttrFuture = CompletableFuture.runAsync(() -> {
            ResponseVo<List<SkuAttrValueEntity>> saleAttrResponseVo = this.pmsClient.querySaleAttrBySkuId(skuId);
            List<SkuAttrValueEntity> skuAttrValueEntities = saleAttrResponseVo.getData();
            if (!Collections.isEmpty(skuAttrValueEntities)) {
                itemVo.setSaleAttr(skuAttrValueEntities.stream().collect(Collectors.toMap(SkuAttrValueEntity::getAttrId, SkuAttrValueEntity::getAttrValue)));
            }
        }, threadPoolExecutor);

        //skuId和销售属性组合的映射关系
        CompletableFuture<Void> stringMappingFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<String> stringResponseVo = this.pmsClient.querySaleAttrsMappingsSkuIdBySpuId(skuEntity.getSpuId());
            String json = stringResponseVo.getData();
            itemVo.setSkuJsons(json);
        }, threadPoolExecutor);

        //分组及规格参数信息
        CompletableFuture<Void> groupFuture = skuFuture.thenAcceptAsync(skuEntity -> {
            ResponseVo<List<ItemGroupVo>> itemResponseVo = this.pmsClient.queryGroupWithAttrValuesByCidAndSpuIdAndSkuId(skuEntity.getCategoryId(), skuEntity.getSpuId(), skuId);
            List<ItemGroupVo> itemGroupVos = itemResponseVo.getData();
            itemVo.setGroups(itemGroupVos);
        }, threadPoolExecutor);

        CompletableFuture.allOf(catesFuture,brandFuture,spuFuture,skuImagesFuture,
                descFuture,salesFuture,storeFuture,saleAttrsFuture,saleAttrFuture,
                stringMappingFuture,groupFuture).join();

        return itemVo;
    }

    public void generateHtml(ItemVo itemVo) {

        Context context =new Context();
        context.setVariable("itemVo",itemVo);

        try (
                PrintWriter printWriter = new PrintWriter("E:\\tmp\\html\\" + itemVo.getSkuId() + ".html");
                ){
            //通过模板引擎生成静态页面：1-模板名称，2-上下文对象，3-文件流
            this.templateEngine.process("item",context,printWriter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class CompletableFutureDemo{
    public static void main(String[] args) {

        /**
         * 所有的Async结尾的方法都可以重载代线程池的方法
         *
         * 初始化方法
         * runAsync:子任务没有返回结果
         * supplyAsync：子任务有返回结果集
         *
         * 计算完成时回调方法
         * whenComplete:同步执行
         * whenCompleteAsync:
         *      1、异步执行，上一个任务正常执行，可以获取上一个任务的返回结果集
         *      2、如果上一个任务出现异常，可以捕获上一个任务的异常信息
         *      3、重载代线程池的方法
         * exceptionally：上一个任务出现异常时执行
         *
         * 串行化方法：
         * thenApply
         * thenApplyAsync：获取上一个任务的返回结果集，并有自己的返回结果集
         * thenAccept
         * thenAcceptAsync：获取上一个任务的返回结果集，但没有自己的返回结果集
         * thenRun
         * thenRunAsync：不获取上一个任务的返回结果集，也没有自己的返回结果集，只要上一个任务执行完成，就执行该任务
         *
         * 组合方法
         * allOf：所有方法都执行完成
         * anyOf：只要有一个方法执行完即可
         */

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            System.out.println("有返回值的方法");
            //int i=10/0;
            return "Hello supplyAsync";
        });
        CompletableFuture<String> future1 = future.thenApplyAsync(t -> {
            System.out.println("============thenApplyAsync1==============");

            try {
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("上一个任务的返回结果" + t);
            return "hello thenApplyAsync1";
        });
        CompletableFuture<Void> future2 = future.thenAcceptAsync(t -> {
            System.out.println("============thenApplyAsync2==============");

            try {
                TimeUnit.SECONDS.sleep(4);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("上一个任务的返回结果" + t);
        });
        CompletableFuture<Void> future3 = future.thenRunAsync(() -> {
            System.out.println("上一个任务执行完成执行");
        });

        CompletableFuture.anyOf(future1,future2,future3).join();


/*            .whenCompleteAsync((t,u)->{
            System.out.println("上一个任务的返回结果集t = " + t);
            System.out.println("上一个任务的异常信息u = " + u);
            System.out.println("执行另一个任务");
        }).exceptionally(t->{
            System.out.println("上一个任务的异常信息："+t);
            return "Hello exceptionally";
        });*/

        try {
            //System.out.println(future1.get());  //get()方法阻塞主线执行
            System.out.println("这是主线程方法");
            System.in.read();
        } catch (Exception e) {
            e.printStackTrace();
        }



//        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
//            System.out.println("Hello CompletableFuture runAsync：没有返回值");
//        });


//        FutureTask<String> futureTask = new FutureTask<>(new MyCallable());
//        new Thread(futureTask).start();
//
//        try {
//            System.out.println(futureTask.get());
//            System.out.println("这是主线程的打印");
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

    }
}

class MyCallable implements Callable<String>{

    @Override
    public String call() throws Exception {
        System.out.println("这是使用了Callable初始化了线程");
        return "hello Callable";
    }
}
