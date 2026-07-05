package com.bigbear.common.core;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class BaseEntity {

    protected Long id;

    protected LocalDateTime createTime;

    protected LocalDateTime updateTime;

}
