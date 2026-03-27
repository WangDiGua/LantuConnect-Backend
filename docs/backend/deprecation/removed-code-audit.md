# 旧接口与关联代码删除审计清单

## 说明

- 范围：Phase 3 后半段（旧接口物理删除 + 关联死代码清理）
- 原则：仅删除已经不再被路由引用、且编译校验通过的代码
- 校验方式：`mvnw.cmd -DskipTests compile` 必须通过

## 1. 已删除控制器（物理删除）

### Agent

- `src/main/java/com/lantu/connect/agent/controller/AgentController.java`
- `src/main/java/com/lantu/connect/agent/controller/AgentVersionController.java`

### Skill

- `src/main/java/com/lantu/connect/skill/controller/SkillController.java`

### MCP

- `src/main/java/com/lantu/connect/mcp/controller/McpServerController.java`

### App

- `src/main/java/com/lantu/connect/app/controller/AppController.java`

### Dataset/Provider/Category

- `src/main/java/com/lantu/connect/dataset/controller/DatasetController.java`
- `src/main/java/com/lantu/connect/dataset/controller/ProviderController.java`
- `src/main/java/com/lantu/connect/dataset/controller/CategoryController.java`

## 2. 已删除旧服务与 DTO（深度清理）

### Agent

- `src/main/java/com/lantu/connect/agent/service/AgentService.java`
- `src/main/java/com/lantu/connect/agent/service/AgentVersionService.java`
- `src/main/java/com/lantu/connect/agent/service/impl/AgentServiceImpl.java`
- `src/main/java/com/lantu/connect/agent/service/impl/AgentVersionServiceImpl.java`
- `src/main/java/com/lantu/connect/agent/dto/AgentCreateRequest.java`
- `src/main/java/com/lantu/connect/agent/dto/AgentUpdateRequest.java`
- `src/main/java/com/lantu/connect/agent/dto/AgentQueryRequest.java`
- `src/main/java/com/lantu/connect/agent/dto/AgentTestResponse.java`
- `src/main/java/com/lantu/connect/agent/dto/VersionCreateRequest.java`

### Skill

- `src/main/java/com/lantu/connect/skill/service/SkillService.java`
- `src/main/java/com/lantu/connect/skill/service/impl/SkillServiceImpl.java`
- `src/main/java/com/lantu/connect/skill/dto/SkillCreateRequest.java`
- `src/main/java/com/lantu/connect/skill/dto/SkillUpdateRequest.java`
- `src/main/java/com/lantu/connect/skill/dto/SkillQueryRequest.java`

### MCP

- `src/main/java/com/lantu/connect/mcp/service/McpServerService.java`
- `src/main/java/com/lantu/connect/mcp/service/impl/McpServerServiceImpl.java`
- `src/main/java/com/lantu/connect/mcp/dto/McpServerCreateRequest.java`
- `src/main/java/com/lantu/connect/mcp/dto/McpServerUpdateRequest.java`
- `src/main/java/com/lantu/connect/mcp/dto/McpServerQueryRequest.java`

### App

- `src/main/java/com/lantu/connect/app/service/AppService.java`
- `src/main/java/com/lantu/connect/app/service/impl/AppServiceImpl.java`
- `src/main/java/com/lantu/connect/app/dto/AppCreateRequest.java`
- `src/main/java/com/lantu/connect/app/dto/AppUpdateRequest.java`
- `src/main/java/com/lantu/connect/app/dto/AppQueryRequest.java`

### Dataset/Provider/Category

- `src/main/java/com/lantu/connect/dataset/service/DatasetService.java`
- `src/main/java/com/lantu/connect/dataset/service/ProviderService.java`
- `src/main/java/com/lantu/connect/dataset/service/CategoryService.java`
- `src/main/java/com/lantu/connect/dataset/service/impl/DatasetServiceImpl.java`
- `src/main/java/com/lantu/connect/dataset/service/impl/ProviderServiceImpl.java`
- `src/main/java/com/lantu/connect/dataset/service/impl/CategoryServiceImpl.java`
- `src/main/java/com/lantu/connect/dataset/dto/DatasetCreateRequest.java`
- `src/main/java/com/lantu/connect/dataset/dto/DatasetUpdateRequest.java`
- `src/main/java/com/lantu/connect/dataset/dto/DatasetQueryRequest.java`
- `src/main/java/com/lantu/connect/dataset/dto/ProviderCreateRequest.java`
- `src/main/java/com/lantu/connect/dataset/dto/ProviderUpdateRequest.java`
- `src/main/java/com/lantu/connect/dataset/dto/ProviderQueryRequest.java`
- `src/main/java/com/lantu/connect/dataset/dto/CategoryCreateRequest.java`
- `src/main/java/com/lantu/connect/dataset/dto/CategoryUpdateRequest.java`
- `src/main/java/com/lantu/connect/dataset/dto/CategoryVO.java`

## 3. 保留说明（未删除）

- Mapper 与实体多数保留，原因：
  - 仍被统一网关目录/解析服务引用
  - 或仍被其他现存模块使用
- `Tag` 相关模块保留，原因：
  - 现存 `TagController` 仍提供能力

## 4. 当前结果

- 删除后编译通过
- 旧资源控制器清理完成
- 已进入“统一网关接口为主”的收敛态

## 5. 目录收口（空包清理）

- 本轮已删除空包目录：
  - `src/main/java/com/lantu/connect/agent`
  - `src/main/java/com/lantu/connect/app`
  - `src/main/java/com/lantu/connect/mcp`
- 保留说明：
  - `src/main/java/com/lantu/connect/skill` 未删除（保留 `SkillRemoteInvokeService`）
  - `src/main/java/com/lantu/connect/dataset` 未删除（`Tag/Category/Provider` 仍被使用）

## 6. 第二轮空包收口

- 本轮新增删除空目录：
  - `src/main/java/com/lantu/connect/gateway/config`
  - `src/main/java/com/lantu/connect/skill/controller`
  - `src/main/java/com/lantu/connect/skill/dto`
  - `src/main/java/com/lantu/connect/skill/entity`
  - `src/main/java/com/lantu/connect/skill/mapper`
  - `src/main/java/com/lantu/connect/skill/service/impl`
