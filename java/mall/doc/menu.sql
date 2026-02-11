insert into sys_menu values('123',  '客户端管理',   '1',   '11', 'client',           'system/client/index',          '', '1', '0', 'C', '0', '0', 'system:client:list',          'international', 103, 1, now(), null, null, '客户端管理菜单');

insert into sys_menu values('1061', '客户端管理查询', '123', '1',  '#', '', '', '1', '0', 'F', '0', '0', 'system:client:query',        '#', 103, 1, now(), null, null, '');
insert into sys_menu values('1062', '客户端管理新增', '123', '2',  '#', '', '', '1', '0', 'F', '0', '0', 'system:client:add',          '#', 103, 1, now(), null, null, '');
insert into sys_menu values('1063', '客户端管理修改', '123', '3',  '#', '', '', '1', '0', 'F', '0', '0', 'system:client:edit',         '#', 103, 1, now(), null, null, '');
insert into sys_menu values('1064', '客户端管理删除', '123', '4',  '#', '', '', '1', '0', 'F', '0', '0', 'system:client:remove',       '#', 103, 1, now(), null, null, '');
insert into sys_menu values('1065', '客户端管理导出', '123', '5',  '#', '', '', '1', '0', 'F', '0', '0', 'system:client:export',       '#', 103, 1, now(), null, null, '');

-- 游戏账号管理菜单
insert into sys_menu values('200',  '游戏账号管理',   '1',   '12', 'account',           'mall/account/index',          '', '1', '0', 'C', '0', '0', 'mall:account:list',          'user', 103, 1, now(), null, null, '游戏账号管理菜单');
insert into sys_menu values('2001', '游戏账号查询', '200', '1',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:account:query',        '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2002', '游戏账号新增', '200', '2',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:account:add',          '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2003', '游戏账号修改', '200', '3',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:account:edit',         '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2004', '游戏账号删除', '200', '4',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:account:remove',       '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2005', '游戏账号导出', '200', '5',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:account:export',       '#', 103, 1, now(), null, null, '');

-- 模拟器管理菜单
insert into sys_menu values('201',  '模拟器管理',   '1',   '13', 'emulator',           'mall/emulator/index',          '', '1', '0', 'C', '0', '0', 'mall:emulator:list',          'monitor', 103, 1, now(), null, null, '模拟器管理菜单');
insert into sys_menu values('2011', '模拟器查询', '201', '1',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:emulator:query',        '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2012', '模拟器新增', '201', '2',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:emulator:add',          '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2013', '模拟器修改', '201', '3',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:emulator:edit',         '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2014', '模拟器删除', '201', '4',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:emulator:remove',       '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2015', '模拟器导出', '201', '5',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:emulator:export',       '#', 103, 1, now(), null, null, '');

-- 执行日志管理菜单
insert into sys_menu values('202',  '执行日志管理',   '1',   '14', 'executionLog',           'mall/executionLog/index',          '', '1', '0', 'C', '0', '0', 'mall:execution_log:list',          'log', 103, 1, now(), null, null, '执行日志管理菜单');
insert into sys_menu values('2021', '执行日志查询', '202', '1',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:execution_log:query',        '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2022', '执行日志新增', '202', '2',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:execution_log:add',          '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2023', '执行日志修改', '202', '3',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:execution_log:edit',         '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2024', '执行日志删除', '202', '4',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:execution_log:remove',       '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2025', '执行日志导出', '202', '5',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:execution_log:export',       '#', 103, 1, now(), null, null, '');

-- 订单管理菜单
insert into sys_menu values('203',  '订单管理',   '1',   '15', 'tradeOrder',           'mall/tradeOrder/index',          '', '1', '0', 'C', '0', '0', 'mall:trade_order:list',          'order', 103, 1, now(), null, null, '订单管理菜单');
insert into sys_menu values('2031', '订单查询', '203', '1',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:trade_order:query',        '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2032', '订单新增', '203', '2',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:trade_order:add',          '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2033', '订单修改', '203', '3',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:trade_order:edit',         '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2034', '订单删除', '203', '4',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:trade_order:remove',       '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2035', '订单导出', '203', '5',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:trade_order:export',       '#', 103, 1, now(), null, null, '');

-- 订单项管理菜单
insert into sys_menu values('204',  '订单项管理',   '1',   '16', 'orderItem',           'mall/orderItem/index',          '', '1', '0', 'C', '0', '0', 'mall:order_item:list',          'order', 103, 1, now(), null, null, '订单项管理菜单');
insert into sys_menu values('2041', '订单项查询', '204', '1',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:order_item:query',        '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2042', '订单项新增', '204', '2',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:order_item:add',          '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2043', '订单项修改', '204', '3',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:order_item:edit',         '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2044', '订单项删除', '204', '4',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:order_item:remove',       '#', 103, 1, now(), null, null, '');
insert into sys_menu values('2045', '订单项导出', '204', '5',  '#', '', '', '1', '0', 'F', '0', '0', 'mall:order_item:export',       '#', 103, 1, now(), null, null, '');