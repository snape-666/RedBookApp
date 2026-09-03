# RedBookApp

一个仿小红书风格的 Android 社交笔记 App:支持图文/视频笔记发布、评论互动、关注关系、私信聊天、实时通知、个人主页与隐私设置等。

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 语言 | Kotlin  |
| UI | Jetpack Compose + Material 3(自定义小红书红主题) |
| 架构 | 单 Activity + 手写 Screen 导航栈;MVVM(ViewModel + StateFlow / mutableState) |
| 图片加载 | Coil(AsyncImage) |
| 网络(数据) | Supabase PostgREST,经 **Volley** 发起 HTTP/REST 请求 |
| 网络(实时) | Supabase Realtime,经 **OkHttp WebSocket** 建立长连接 |
| 后端 | Supabase(PostgreSQL + REST + Realtime + 文件上传/存储),脚本见根目录 `supabase_*.sql` |
| 本地存储 | SharedPreferences(搜索历史、通知/隐私设置本地缓存等) |

## 架构与原理

- **导航**:`MainActivity` 内维护 `Screen` 密封类 + `screenStack` 手动栈式导航,支持任意层级的进栈/返回。
- **网络协议**:
  - 普通数据读写走 **HTTPS REST**(PostgREST,URL 形如 `/rest/v1/{table}?select=..&filter=..`),Volley 封装为 suspend 函数;
  - **实时推送**走 **WebSocket**(Supabase Realtime),登录后建立连接,接收点赞/关注/评论/私信增量并驱动未读角标。
- **登录认证**:邮箱+密码经 Supabase Auth 注册/登录,`users` 表冗余用户资料;注册自动分配小红书号 `xhs_id`。
- **数据模型**:视频与图文都存入 `posts` 表,视频以 `image_url` 带 `video:` 前缀区分;评论/回复同存 `comments` 表(空 `parent_id` 为一级评论)。
- **作者编辑能力**:从"我的-笔记"进入详情页时开启作者模式,底部可展开 编辑/权限设置/删除 浮层面板;权限(公开可见/仅自己可见)落库 `posts.visibility`,各处列表按查看者过滤,仅自己可见帖子只有作者能看到。
- **IP 归属地**:优先系统定位解析省份,失败回退公网 IP 兜底,写入 `users.ip_location`。

## 主要功能模块

| 功能 | 说明/入口 | 依赖的关键实现 |
| --- | --- | --- |
| 登录 / 注册 | 账号或邮箱+密码,注册自动生成小红书ID | `SupabaseAuthRepository`(Auth REST) |
| 首页 | 关注 / 发现双 Tab 图文信息流 | `HomeRepository` + Volley REST |
| 视频流 | 竖滑视频播放与点赞/评论 | `VideoFeedScreen` + Coil/VideoView |
| 帖子详情 | 图文内容、点赞收藏、评论/回复、长按删评论 | `DetailScreen` + `DetailViewModel` |
| 作者编辑 | 编辑已发布帖子、权限设置、删除 | `PublishScreen` 编辑模式 + `posts.visibility` |
| 发布 | 多图/视频发布,支持长按拖拽排序、存草稿 | `PublishScreen` + 图片上传(存储) |
| 草稿箱 | 草稿列表、续写/删除 | `drafts` 表 + `DraftScreen` |
| 我的主页 | 笔记/评论/收藏/赞过 分区,侧边栏菜单 | `ProfileScreen` + 隐私过滤 |
| 他人主页 | 资料卡、关注/取关、备注名、发私信 | `ProfileViewModel` + `follows`/`remarks` |
| 搜索 | 关键字搜索 | `SearchScreen` + PostgREST 过滤 |
| 消息中心 | 聊天列表、点赞/关注/评论通知分组 | `RealtimeRepository` + WebSocket |
| 私信聊天 | 点对点会话、消息历史与滚动定位 | 会话表 + Realtime 推送 |
| 浏览记录 | 最近看过、多选删除 | `browsing_history` 表 |
| 通知系统 | 前台服务 + 系统通知,点击路由到对应页面 | `NotificationService` + `NotifClickRouter` |
| 隐私设置 | 笔记/评论/收藏/赞过 对他人可见开关 | `users.privacy_*` 列 + 本地缓存 |
| 通知设置 | 总开关 + 分类开关,权限引导 | `NotifPrefs` + 云端双存 |
| 资料卡 | 查看名片式资料 | `UserCardScreen` |

## 后端说明(Supabase)

- **数据库**:PostgreSQL,核心表 `users / posts / comments / drafts / likes / favorites / follows / remarks / browsing_history`,结构迁移脚本在仓库根目录 `supabase_*.sql`,需在 Supabase SQL Editor 手动执行(含 `supabase_post_visibility.sql` 等增量列)。
- **接口风格**:PostgREST(REST + 查询过滤),客户端以 anon key 访问;服务端无自建业务服务。
- **实时**:Supabase Realtime 广播通知/消息事件,客户端 WebSocket 订阅增量。
- **存储**:图片/视频经 POST 上传换取 URL 后入库(`image_url` 多图逗号分隔)。
