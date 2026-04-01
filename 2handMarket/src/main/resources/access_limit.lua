-- 原子限流脚本：INCR + 首次设置 TTL，一次 Redis 调用完成
-- KEYS[1] = 限流 key
-- ARGV[1] = 窗口时间(秒)
-- ARGV[2] = 最大次数

-- 1. 原子自增（key 不存在时自动创建并设为 1）
local current = redis.call('incr', KEYS[1])

-- 2. 如果是第一次访问（incr 后值为 1），设置过期时间
if current == 1 then
    redis.call('expire', KEYS[1], tonumber(ARGV[1]))
end

-- 3. 判断是否超过阈值，超过返回 1（拒绝），否则返回 0（放行）
if current > tonumber(ARGV[2]) then
    return 1
end
return 0
