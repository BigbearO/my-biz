export interface AccountVO {
  /**
   * $column.columnComment
   */
  id: string | number;

  /**
   * $column.columnComment
   */
  userId: string | number;

  /**
   * $column.columnComment
   */
  deviceId: string | number;

}

export interface AccountForm extends BaseEntity {
  /**
   * $column.columnComment
   */
  id?: string | number;

  /**
   * $column.columnComment
   */
  userId?: string | number;

  /**
   * $column.columnComment
   */
  deviceId?: string | number;

}

export interface AccountQuery extends PageQuery {

  /**
   * $column.columnComment
   */
  userId?: string | number;

  /**
   * $column.columnComment
   */
  deviceId?: string | number;

  /**
   * 日期范围参数
   */
  params?: any;
}



