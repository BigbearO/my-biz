package com.bigbear.mall.biz.common;


import com.bigbear.mall.biz.common.enums.GameTypeEnums;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class GameTaskSender4Honkai extends GameTaskSender {


    @Override
    public String getQueue() {
        return "auto-games";
    }


    @Override
    public Integer type() {
        return GameTypeEnums.HONKAI.getCode();
    }
}
