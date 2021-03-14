package com.atguigu.gmall.cart;

import io.swagger.models.auth.In;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
class GmallCartApplicationTests {

    @Test
    void contextLoads() {

        //List<String> list = Arrays.asList("1", "2");

//        List<String> list = Arrays.stream(new String[]{"123","qwe","abc"}).collect(Collectors.toList());
//        list.add("qwert");
//        list.forEach(System.out::println);

        List<Integer> list =new ArrayList<>();

        list.add(23);

        Class<? extends Integer> aClass = list.get(0).getClass();

        System.out.println("aClass.getTypeName() = " + aClass.getTypeName());


    }

}
