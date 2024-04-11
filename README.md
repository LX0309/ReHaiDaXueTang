# 热海大学堂个人开发部分
# 前言
> 本仓库为Gogs私服克隆仓库，代码为本人编写的问答系统功能、赛季排行榜功能、点赞功能、视频播放进度回放功能
> 四部分核心代码，其余代码为项目初始代码

> 本仓库仅仅用于介绍本人负责模块的源码，不提供其他任何支持，已编写功能可用Swagger进行测试。

# 项目介绍
#### 热海大学堂项目致力于打造一个校园课程录播学习系统，提供丰富的学习辅助功能和交互功能，模拟游戏系统打造学习积分赛季排行榜功能包括用户管理模块，课表模块， 学习计划与进度模块，问答系统，点赞功能，积分排行榜功能等，以此提高用户参与度。
#### 项目通过 jmeter 进行压测来模拟高并发环境，服务在平均 1 万 QPS 下正常运行。
### 组织结构

``` lua
ReHaiDaXueTang
├── tj-api -- Feign接口公共包
├── tj-auth -- 杈限微服务
├── tj-common -- 通用工具包
├── tj-course -- 课程服务
├── tj-exam -- 考试服务
├── tj-gateway -- 网关服务
├── tj-learning -- 学习中心服务
├── tj-pay -- 支付服务
├── tj-remark -- 点赞服务
├── tj-trade -- 交易服务
└──  tj-user -- 用户服务
```

### 技术选型

#### 后端技术

| 技术            | 说明            | 官网                                                                            |
|---------------|---------------|-------------------------------------------------------------------------------|
| SpringBoot    | Web应用开发框架     | https://spring.io/projects/spring-boot                                        |
| MyBatis-plus  | 数据层代码生成器      | https://baomidou.com/                                                         |
| Elasticsearch | 搜索引擎          | https://github.com/elastic/elasticsearch                                      |
| RabbitMQ      | 消息队列          | https://www.rabbitmq.com/                                                     |
| Redis         | 内存数据存储        | https://redis.io/                                                             |
| Nacos         | 服务注册中心        | https://nacos.io/zh-cn/docs/quick-start.html                                  |
| Seata         | 分布式事务处理框架     | https://seata.apache.org/zh-cn/                                               |
| Sentinel      | 微服务保护技术       | https://sca.aliyun.com/zh-cn/docs/2022.0.0.0/user-guide/sentinel/quick-start/ |
| OpenFeign     | Web服务客户端      | https://spring.io/projects/spring-cloud-openfeign/                            |
| XXL-JOB       | 分布式任务调度中心     | https://www.xuxueli.com/xxl-job/                                              |
| Nginx         | 静态资源服务器       | https://www.nginx.com/                                                        |
| Docker        | 应用容器引擎        | https://www.docker.com                                                        |
| Jenkins       | 自动化部署工具       | https://github.com/jenkinsci/jenkins                                          |
| OSS           | 对象存储          | https://github.com/aliyun/aliyun-oss-java-sdk                                 |
| JWT           | JWT登录支持       | https://github.com/jwtk/jjwt                                                  |
| Lombok        | Java语言增强库     | https://github.com/rzwitserloot/lombok                                        |
| Hutool        | Java工具类库      | https://github.com/looly/hutool                                               |
| PageHelper    | MyBatis物理分页插件 | http://git.oschina.net/free/Mybatis_PageHelper                                |
| Swagger       | API文档生成工具     | https://github.com/swagger-api                                                |

## 一、点赞功能（tj-remark）
### 1.点赞/取消赞（LikedRecordController）

### 2.批量查询点赞状态（LikedRecordController）
## 二、赛季排行榜功能（tj-user）
## 三、视频播放进度功能（tj-user）
## 四、问答系统（tj-user）
