local cfg = require "cfg._cfgs"
local Beans = cfg._beans

---@class cfg.other.drop
---@field dropid number , 序号
---@field name text , 名字
---@field items table<number,Beans.other.dropitem> , 掉落概率
---@field testmap table<number,number> , 测试map block
---@field get fun(dropid:number):cfg.other.drop
---@field all table<any,cfg.other.drop>

local this = cfg.other.drop

local mk = cfg._mk.table(this, { { 'all', 'get', 1 }, }, nil, nil, 
    'dropid', -- int, 序号
    'name', -- text, 名字
    'items', -- list,DropItem, 掉落概率
    'testmap' -- map,int,int, 测试map block
    )

local dropitem = Beans.other.dropitem

local E = cfg._mk.E

mk(1, "测试掉落", {dropitem(100, {1001, 1002, 1003}, 10, 20), dropitem(10, {2001}, 10, 10), dropitem(10, {2002}, 0, 1), dropitem(50, {3001}, 1, 1)}, {[1] = 11, [3] = 33, [5] = 55})
mk(2, "剧情任务测试2", {dropitem(100, {10001}, 1, 1)}, E)
mk(3, "通告栏掉落染色模板1", {dropitem(80, {20001}, 10, 20)}, E)

return this
