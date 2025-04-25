package com.bigbear.order.infrastructure.impl;

import com.alibaba.fastjson.JSONObject;
import com.bigbear.order.infrastructure.GameTaskSender;
import com.bigbear.order.infrastructure.common.GameTypeEnums;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class GameTaskSender4YuanShen extends GameTaskSender {


    @Override
    public String getQueue() {
        return "";
    }


    @Override
    public Integer type() {
        return GameTypeEnums.YUAN_SHEN.getCode();
    }
}
