# 根模式：修复与加强说明（第三轮）

## 0. 本轮最重要：修掉了「连接风暴」（万级连接把 App 卡死）

你发的日志里，29 秒内有 **1652 个**到 `127.0.0.1:7892` 的连接，模式是同一个环：

```
[TCP] 127.0.0.1:41482 --> 127.0.0.1:7892 match RuleSet(private-ip) using 中国流量[DIRECT]
```

这个环是这样转起来的：**redir 监听拿到一个不是 iptables 转发来的连接时，会把目标解析成自己的监听地址**
（`127.0.0.1:7892`），然后规则把它交给 `DIRECT`，`DIRECT` 再去拨 `127.0.0.1:7892` ——
又落回同一个监听 —— **每次拨号都生出下一次拨号**。它不需要任何 App 在联网，一旦点着就自转，
直到把 App 卡死。日志结尾的 `dial ... connection refused` 就是环转到最后把监听拖死后才停的。

两个入口都被堵上了：

1. **删除了启动时的端口探测**。上一版在装 iptables 规则前会 `connect` 一下 redir 端口确认它开了 ——
   这一下正是点燃这个环的火花。实际上根本不需要探测：`Core.startListener` 是**同步**绑定端口的，
   返回时端口已经就绪。
2. **redir 端口现在会让开内核自己在用的所有端口**（HTTP / SOCKS / 混合 / TProxy / DNS）。
   否则你的混合端口若是 7892，redir 抢到同一个 7892，所有系统代理请求都会掉进这个环 ——
   你日志里的正是这个形状。两个派生入口（`_effectivePatchConfig` 和 `vpnOptions`）同步改了，
   每种冲突都有测试。

## 1. 强停/崩溃后不再断网

iptables 规则活在内核里，不随 App 进程消失。以前根模式开着时被强停/被杀进程，`FLCLASH_OUT` 链就留在那儿，
全设备 TCP 被 REDIRECT 到没人听的端口 —— 整机没网只能重启。现在写规则时落一个标记文件，
App 每次启动先看标记，有就先 `su` 清链子，**打开一次 App 就自愈**。

## 2. 分应用代理

读同一份 `accessControlProps`：黑名单把选中的 App `RETURN` 直连；白名单只 REDIRECT 选中的（含 FlClash 自己），
白名单为空时谁都不代理；包名→uid 的换算按 TUN 的老规矩，卸载的包跳过。

## 3. IPv6 开关（默认关）

redir 监听绑在 `127.0.0.1`，IPv6 进不来，以前是静默绕过代理。新增「屏蔽这些应用的 IPv6」：
`ip6tables` 只对正好被代理的那些 App `REJECT`，范围外照旧直连。

## 4. iptables 二进制探测

依次探测 `iptables` / `/system/bin/iptables` / `iptables-nft`（v6 同理），失败信息点名缺什么。

## 5. 一次 su 跑一批命令

首次授权后整批合成一个 `su -c` 脚本，失败才逐条重跑定位；装/卸规则挪到 IO 线程。

## 你仍然要手动做的一件事

旧版本已经把 `redir-port: 7892` 存进你的配置了。请到 **设置 → 网络 → 端口**，把 **Redir 端口改成 0**
（现在这个值为 0 或与其他端口冲突时，运行时会自动挑一个空闲端口，不再依赖这个设置）。

## 关于日志里那两条 `operation not permitted`

根模式开启时仍会出现：上游 mihomo 把 redir 的 TCP/UDP 写在同一段代码里共用一个 `err`，
UDP 那半需要 `CAP_NET_ADMIN` 必然失败。**TCP 转发不受影响**，规则也只转 TCP。

## 合入方式

- **A. 整包（推荐）**：`FlClash-fixed-full.zip` = 你的工程 + 三轮全部改动，解压推仓库即可。
- **B. 覆盖文件**：`FlClash-root-mode-fix.zip` = 42 个改动文件 + 三个可 `git am` 的补丁 + 本说明。

## 验证情况

- Dart：`dart analyze` 无问题；`redirect_ports_test` / `core_test` / `network_config_test` / `state_derived_test` 全绿
  （`models/` 下 11 个失败是缺 `core/Clash.Meta` 子模块的环境性失败，与改动无关）
- Kotlin：common/core/service/app 全量编译通过，**144 项 JUnit 全绿**
- 无法在本机验证的：真机 `su`/`iptables`、IPv6 拒绝、风暴是否彻底消失（需要你装新包观察）
