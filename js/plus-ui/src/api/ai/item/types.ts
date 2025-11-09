export interface ItemVO {
  /**
   * $column.columnComment
   */
  id: string | number;

  /**
   * 所属账号
   */
  accountId: string | number;

  /**
   * 关联的闲鱼平台商品id
   */
  itemId: string | number;

  /**
   * 商品数据，JSON格式存储
   */
  data: string;

  /**
   * 商品价格
   */
  price: number;

  /**
   * 商品描述
   */
  description: string;

  /**
   * 商品标题
   */
  title: string;

  /**
   * 商品分类
   */
  category: string;

  /**
   * 商品所在地
   */
  location: string;

  /**
   * 商品图片URL
   */
  imageUrl: string;

  /**
   * 卖家ID
   */
  sellerId: string | number;

  /**
   * 商品状态
   */
  condition: string;

  /**
   * 发布日期时间戳
   */
  publishedDate: number;

  /**
   * 库存状态
   */
  stockStatus: string;

  /**
   * 创建时间
   */
  createdAt: string;

  /**
   * 更新时间
   */
  updatedAt: string;

}

export interface ItemForm extends BaseEntity {
  /**
   * $column.columnComment
   */
  id?: string | number;

  /**
   * 所属账号
   */
  accountId?: string | number;

  /**
   * 关联的闲鱼平台商品id
   */
  itemId?: string | number;

  /**
   * 商品数据，JSON格式存储
   */
  data?: string;

  /**
   * 商品价格
   */
  price?: number;

  /**
   * 商品描述
   */
  description?: string;

  /**
   * 商品标题
   */
  title?: string;

  /**
   * 商品分类
   */
  category?: string;

  /**
   * 商品所在地
   */
  location?: string;

  /**
   * 商品图片URL
   */
  imageUrl?: string;

  /**
   * 卖家ID
   */
  sellerId?: string | number;

  /**
   * 商品状态
   */
  condition?: string;

  /**
   * 发布日期时间戳
   */
  publishedDate?: number;

  /**
   * 库存状态
   */
  stockStatus?: string;

  /**
   * 创建时间
   */
  createdAt?: string;

  /**
   * 更新时间
   */
  updatedAt?: string;

}

export interface ItemQuery extends PageQuery {

  /**
   * 所属账号
   */
  accountId?: string | number;

  /**
   * 关联的闲鱼平台商品id
   */
  itemId?: string | number;

  /**
   * 商品数据，JSON格式存储
   */
  data?: string;

  /**
   * 商品价格
   */
  price?: number;

  /**
   * 商品描述
   */
  description?: string;

  /**
   * 商品标题
   */
  title?: string;

  /**
   * 商品分类
   */
  category?: string;

  /**
   * 商品所在地
   */
  location?: string;

  /**
   * 商品图片URL
   */
  imageUrl?: string;

  /**
   * 卖家ID
   */
  sellerId?: string | number;

  /**
   * 商品状态
   */
  condition?: string;

  /**
   * 发布日期时间戳
   */
  publishedDate?: number;

  /**
   * 库存状态
   */
  stockStatus?: string;

  /**
   * 创建时间
   */
  createdAt?: string;

  /**
   * 更新时间
   */
  updatedAt?: string;

  /**
   * 日期范围参数
   */
  params?: any;
}



