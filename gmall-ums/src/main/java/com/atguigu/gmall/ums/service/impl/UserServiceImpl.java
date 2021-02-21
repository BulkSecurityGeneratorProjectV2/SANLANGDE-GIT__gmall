package com.atguigu.gmall.ums.service.impl;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.ums.mapper.UserMapper;
import com.atguigu.gmall.ums.entity.UserEntity;
import com.atguigu.gmall.ums.service.UserService;


@Service("userService")
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<UserEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<UserEntity>()
        );

        return new PageResultVo(page);
    }

    /**
     * 数据校验
     * @param data
     * @param type
     * @return
     */
    @Override
    public Boolean checkData(String data, Integer type) {

        QueryWrapper<UserEntity> wrapper = new QueryWrapper<>();

        switch (type){
            case 1:
                wrapper.eq("username",data);
                break;
            case 2:
                wrapper.eq("phone",data);
                break;
            case 3:
                wrapper.eq("email",data);
                break;
            default:
                return null;
        }
        return this.count(wrapper)==0;
    }

    @Override
    public void sendCode(String phone) {
        // 发送消息
        try {
            this.rabbitTemplate.convertAndSend("ums_register_exchange", "ums.register", phone);
        } catch (Exception e) {
            log.error("{},验证码消息发送异常，手机号为：" + phone,e);
        }
    }

    @Override
    public void register(UserEntity userEntity, String code) {

    }

}