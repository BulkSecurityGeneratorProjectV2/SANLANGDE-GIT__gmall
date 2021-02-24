package com.atguigu.gmall.ums.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;
import com.atguigu.gmall.ums.entity.UserEntity;

/**
 * 用户表
 *
 * @author fengge
 * @email fengge@atguigu.com
 * @date 2021-01-18 13:47:43
 */
public interface UserService extends IService<UserEntity> {

    PageResultVo queryPage(PageParamVo paramVo);

    Boolean checkData(String data, Integer type);

    void sendCode(String phone);

    void register(UserEntity userEntity, String code);

    UserEntity queryInfo(String loginName, String password);
}

