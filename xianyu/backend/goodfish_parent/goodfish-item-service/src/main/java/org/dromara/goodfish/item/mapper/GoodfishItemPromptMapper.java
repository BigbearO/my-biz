package org.dromara.goodfish.item.mapper;

import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.dromara.goodfish.item.domain.GoodfishItemPrompt;
import org.dromara.goodfish.item.domain.vo.GoodfishItemVo;

/**
 * 商品提示词配置Mapper接口（纯 MyBatis-Plus，无自定义 SQL）.
 *
 * <p>新增走 {@code insert}（主键由 MyBatis-Plus 雪花算法填充），
 * 修改走 {@code updateById}（必须带主键，null 字段不写库），
 * 不存在「按业务键隐式 upsert」的第三种语义.</p>
 *
 * @author goodfish
 */
public interface GoodfishItemPromptMapper extends BaseMapperPlus<GoodfishItemPrompt, GoodfishItemVo> {

}
