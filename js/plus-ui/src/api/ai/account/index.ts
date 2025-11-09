import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { AccountVO, AccountForm, AccountQuery } from '@/api/ai/account/types';

/**
 * 查询【请填写功能名称】列表
 * @param query
 * @returns {*}
 */

export const listAccount = (query?: AccountQuery): AxiosPromise<AccountVO[]> => {
  return request({
    url: '/ai/account/list',
    method: 'get',
    params: query
  });
};

/**
 * 查询【请填写功能名称】详细
 * @param id
 */
export const getAccount = (id: string | number): AxiosPromise<AccountVO> => {
  return request({
    url: '/ai/account/' + id,
    method: 'get'
  });
};

/**
 * 新增【请填写功能名称】
 * @param data
 */
export const addAccount = (data: AccountForm) => {
  return request({
    url: '/ai/account',
    method: 'post',
    data: data
  });
};

/**
 * 修改【请填写功能名称】
 * @param data
 */
export const updateAccount = (data: AccountForm) => {
  return request({
    url: '/ai/account',
    method: 'put',
    data: data
  });
};

/**
 * 删除【请填写功能名称】
 * @param id
 */
export const delAccount = (id: string | number | Array<string | number>) => {
  return request({
    url: '/ai/account/' + id,
    method: 'delete'
  });
};
