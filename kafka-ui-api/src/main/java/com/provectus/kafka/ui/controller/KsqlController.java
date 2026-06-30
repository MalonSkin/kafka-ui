package com.provectus.kafka.ui.controller;

import com.provectus.kafka.ui.api.KsqlApi;
import com.provectus.kafka.ui.model.KsqlCommandV2DTO;
import com.provectus.kafka.ui.model.KsqlCommandV2ResponseDTO;
import com.provectus.kafka.ui.model.KsqlResponseDTO;
import com.provectus.kafka.ui.model.KsqlStreamDescriptionDTO;
import com.provectus.kafka.ui.model.KsqlTableDescriptionDTO;
import com.provectus.kafka.ui.model.KsqlTableResponseDTO;
import com.provectus.kafka.ui.model.rbac.AccessContext;
import com.provectus.kafka.ui.model.rbac.permission.KsqlAction;
import com.provectus.kafka.ui.service.ksql.KsqlServiceV2;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * KSQL 管理控制器
 *
 * 提供 ksqlDB 的交互操作，包括：
 * 1. 执行 KSQL 命令（支持 DDL、DML、查询等）
 * 2. 打开 KSQL 响应管道（用于流式查询结果）
 * 3. 列出 KSQL 流（Streams）
 * 4. 列出 KSQL 表（Tables）
 *
 * <p>采用管道机制：先通过 executeKsql 注册命令获取 pipeId，
 * 再通过 openKsqlResponsePipe 建立 SSE 连接获取流式结果。</p>
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class KsqlController extends AbstractController implements KsqlApi {

  /** KSQL 业务服务（V2 版本），处理 KSQL 命令的执行和流管理 */
  private final KsqlServiceV2 ksqlServiceV2;

  /**
   * 注册并执行 KSQL 命令
   *
   * 将 KSQL 命令提交到 ksqlDB 服务器，返回管道 ID 用于后续获取结果。
   * 支持 DDL（CREATE/DROP）、DML（INSERT）和查询语句。
   *
   * @param clusterName 集群名称
   * @param ksqlCmdDo   KSQL 命令（包含 KSQL 语句和流属性配置）
   * @param exchange    服务器交换对象
   * @return 包含 pipeId 的响应，用于后续打开结果管道
   */
  @Override
  public Mono<ResponseEntity<KsqlCommandV2ResponseDTO>> executeKsql(String clusterName,
                                                                    Mono<KsqlCommandV2DTO> ksqlCmdDo,
                                                                    ServerWebExchange exchange) {
    return ksqlCmdDo.flatMap(
            command -> {
              var context = AccessContext.builder()
                  .cluster(clusterName)
                  .ksqlActions(KsqlAction.EXECUTE)
                  .operationName("executeKsql")
                  .operationParams(command)
                  .build();
              return validateAccess(context).thenReturn(
                      new KsqlCommandV2ResponseDTO().pipeId(
                          ksqlServiceV2.registerCommand(
                              getCluster(clusterName),
                              command.getKsql(),
                              Optional.ofNullable(command.getStreamsProperties()).orElse(Map.of()))))
                  .doOnEach(sig -> audit(context, sig));
            }
        )
        .map(ResponseEntity::ok);
  }

  /**
   * 打开 KSQL 响应管道获取流式查询结果
   *
   * 建立 SSE（Server-Sent Events）连接，流式返回 KSQL 命令的执行结果。
   * 需要先通过 executeKsql 获取 pipeId。
   *
   * @param clusterName 集群名称
   * @param pipeId      管道 ID（由 executeKsql 返回）
   * @param exchange    服务器交换对象
   * @return KSQL 响应事件流（包含表头、列名、数据行等）
   */
  @Override
  public Mono<ResponseEntity<Flux<KsqlResponseDTO>>> openKsqlResponsePipe(String clusterName,
                                                                          String pipeId,
                                                                          ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .ksqlActions(KsqlAction.EXECUTE)
        .operationName("openKsqlResponsePipe")
        .build();

    return validateAccess(context).thenReturn(
        ResponseEntity.ok(ksqlServiceV2.execute(pipeId)
            .map(table -> new KsqlResponseDTO()
                .table(
                    new KsqlTableResponseDTO()
                        .header(table.getHeader())
                        .columnNames(table.getColumnNames())
                        .values((List<List<Object>>) ((List<?>) (table.getValues()))))))
    );
  }

  /**
   * 列出 ksqlDB 中的所有流（Streams）
   *
   * @param clusterName 集群名称
   * @param exchange    服务器交换对象
   * @return KSQL 流描述列表流（包含流名称、Topic、格式等信息）
   */
  @Override
  public Mono<ResponseEntity<Flux<KsqlStreamDescriptionDTO>>> listStreams(String clusterName,
                                                                          ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .ksqlActions(KsqlAction.EXECUTE)
        .operationName("listStreams")
        .build();

    return validateAccess(context)
        .thenReturn(ResponseEntity.ok(ksqlServiceV2.listStreams(getCluster(clusterName))))
        .doOnEach(sig -> audit(context, sig));
  }

  /**
   * 列出 ksqlDB 中的所有表（Tables）
   *
   * @param clusterName 集群名称
   * @param exchange    服务器交换对象
   * @return KSQL 表描述列表流（包含表名称、Topic、格式、键类型等信息）
   */
  @Override
  public Mono<ResponseEntity<Flux<KsqlTableDescriptionDTO>>> listTables(String clusterName,
                                                                        ServerWebExchange exchange) {
    var context = AccessContext.builder()
        .cluster(clusterName)
        .ksqlActions(KsqlAction.EXECUTE)
        .operationName("listTables")
        .build();

    return validateAccess(context)
        .thenReturn(ResponseEntity.ok(ksqlServiceV2.listTables(getCluster(clusterName))))
        .doOnEach(sig -> audit(context, sig));
  }
}
