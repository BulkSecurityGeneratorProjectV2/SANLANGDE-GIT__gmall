package com.atguigu.gmall.ums.service.impl;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.gmall.common.bean.PageResultVo;
import com.atguigu.gmall.common.bean.PageParamVo;

import com.atguigu.gmall.ums.mapper.UserMapper;
import com.atguigu.gmall.ums.entity.UserEntity;
import com.atguigu.gmall.ums.service.UserService;

import java.util.Date;
import java.util.List;
import java.util.UUID;


@Service("userService")
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public PageResultVo queryPage(PageParamVo paramVo) {
        IPage<UserEntity> page = this.page(
                paramVo.getPage(),
                new QueryWrapper<UserEntity>()
        );

        return new PageResultVo(page);
    }


    @Override
    public UserEntity queryInfo(String loginName, String password) {

        List<UserEntity> userEntities = this.list(new QueryWrapper<UserEntity>().eq("username", loginName)
                .or().eq("phone", loginName)
                .or().eq("email", loginName));
        for (UserEntity userEntity : userEntities) {
            String salt = userEntity.getSalt();
            if(StringUtils.equals(userEntity.getPassword(), DigestUtils.md5Hex(salt + DigestUtils.md5Hex(password)))){
                return userEntity;
            }
        }
        return null;
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
        String cacheCode = redisTemplate.opsForValue().get("ums:register:" + userEntity.getPhone());

        if(!StringUtils.equals(code,cacheCode)){
            return;
        }
        //生成盐
        String salt = StringUtils.replace(UUID.randomUUID().toString(), "-", "");
        userEntity.setSalt(salt);
        //对密码加密
        userEntity.setPassword(DigestUtils.md5Hex(salt+DigestUtils.md5Hex(userEntity.getPassword())));

        userEntity.setCreateTime(new Date());
        userEntity.setLevelId(1l);
        userEntity.setIntegration(1000);
        userEntity.setGrowth(1000);
        userEntity.setNickname(userEntity.getUsername());
        //保存
        boolean save = this.save(userEntity);

        //删除redis中的记录
        if(save){
            redisTemplate.delete("ums:register:" + userEntity.getPhone());
        }
    }

}