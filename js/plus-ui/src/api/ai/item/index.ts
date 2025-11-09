import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ItemVO, ItemForm, ItemQuery } from '@/api/ai/item/types';

/**
 * 查询商品列表
 * @param query
 * @returns {*}
 */

export const listItem = (query?: ItemQuery): AxiosPromise<ItemVO[]> => {
  return request({
    url: '/ai/item/list',
    method: 'get',
    params: query
  });
};

/**
 * 查询商品详细
 * @param id
 */
export const getItem = (id: string | number): AxiosPromise<ItemVO> => {
  return request({
    url: '/ai/item/' + id,
    method: 'get'
  });
};

/**
 * 新增商品
 * @param data
 */
export const addItem = (data: ItemForm) => {
  return request({
    url: '/ai/item',
    method: 'post',
    data: data
  });
};

/**
 * 修改商品
 * @param data
 */
export const updateItem = (data: ItemForm) => {
  return request({
    url: '/ai/item',
    method: 'put',
    data: data
  });
};

/**
 * 删除商品
 * @param id
 */
export const delItem = (id: string | number | Array<string | number>) => {
  return request({
    url: '/ai/item/' + id,
    method: 'delete'
  });
};
