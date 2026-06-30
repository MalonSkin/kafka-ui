package com.provectus.kafka.ui.controller;

import com.provectus.kafka.ui.api.AuthorizationApi;
import com.provectus.kafka.ui.model.ActionDTO;
import com.provectus.kafka.ui.model.AuthenticationInfoDTO;
import com.provectus.kafka.ui.model.ResourceTypeDTO;
import com.provectus.kafka.ui.model.UserInfoDTO;
import com.provectus.kafka.ui.model.UserPermissionDTO;
import com.provectus.kafka.ui.model.rbac.Permission;
import com.provectus.kafka.ui.service.rbac.AccessControlService;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 访问控制与授权信息控制器
 *
 * 提供当前用户的认证和授权信息查询，包括：
 * 1. 获取用户的认证信息（用户名、RBAC 权限列表）
 * 2. 判断 RBAC 是否启用
 *
 * <p>权限信息基于用户所属的角色和角色定义的权限进行计算。</p>
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class AccessController implements AuthorizationApi {

  /** 访问控制服务，处理 RBAC 权限逻辑 */
  private final AccessControlService accessControlService;

  /**
   * 获取当前用户的认证和授权信息
   *
   * 返回用户名、RBAC 启用状态以及用户拥有的权限列表。
   * 权限列表通过用户所属角色的权限进行聚合计算。
   *
   * @param exchange 服务器交换对象
   * @return 认证信息 DTO（包含用户信息和权限列表）
   */
  public Mono<ResponseEntity<AuthenticationInfoDTO>> getUserAuthInfo(ServerWebExchange exchange) {
    Mono<List<UserPermissionDTO>> permissions = accessControlService.getUser()
        .map(user -> accessControlService.getRoles()
            .stream()
            .filter(role -> user.groups().contains(role.getName()))
            .map(role -> mapPermissions(role.getPermissions(), role.getClusters()))
            .flatMap(Collection::stream)
            .toList()
        )
        .switchIfEmpty(Mono.just(Collections.emptyList()));

    Mono<String> userName = ReactiveSecurityContextHolder.getContext()
        .map(SecurityContext::getAuthentication)
        .map(Principal::getName);

    return userName
        .zipWith(permissions)
        .map(data -> {
          var dto = new AuthenticationInfoDTO(accessControlService.isRbacEnabled());
          dto.setUserInfo(new UserInfoDTO(data.getT1(), data.getT2()));
          return dto;
        })
        .switchIfEmpty(Mono.just(new AuthenticationInfoDTO(accessControlService.isRbacEnabled())))
        .map(ResponseEntity::ok);
  }

  /**
   * 将内部权限模型映射为用户权限 DTO 列表
   *
   * @param permissions 内部权限列表
   * @param clusters    权限适用的集群列表
   * @return 用户权限 DTO 列表
   */
  private List<UserPermissionDTO> mapPermissions(List<Permission> permissions, List<String> clusters) {
    return permissions
        .stream()
        .map(permission -> {
          UserPermissionDTO dto = new UserPermissionDTO();
          dto.setClusters(clusters);
          dto.setResource(ResourceTypeDTO.fromValue(permission.getResource().toString().toUpperCase()));
          dto.setValue(permission.getValue());
          dto.setActions(permission.getActions()
              .stream()
              .map(String::toUpperCase)
              .map(this::mapAction)
              .filter(Objects::nonNull)
              .toList());
          return dto;
        })
        .toList();
  }

  /**
   * 将动作名称映射为 ActionDTO 枚举值
   *
   * @param name 动作名称（大写形式）
   * @return ActionDTO 枚举值，未知动作返回 null
   */
  @Nullable
  private ActionDTO mapAction(String name) {
    try {
      return ActionDTO.fromValue(name);
    } catch (IllegalArgumentException e) {
      log.warn("Unknown Action [{}], skipping", name);
      return null;
    }
  }

}
