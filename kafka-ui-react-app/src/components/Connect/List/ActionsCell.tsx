import React from 'react';
import {
  Action,
  ConnectorAction,
  ConnectorState,
  FullConnectorInfo,
  ResourceType,
} from 'generated-sources';
import { CellContext } from '@tanstack/react-table';
import { ClusterNameRoute } from 'lib/paths';
import useAppParams from 'lib/hooks/useAppParams';
import { Dropdown, DropdownItem } from 'components/common/Dropdown';
import {
  useDeleteConnector,
  useUpdateConnectorState,
} from 'lib/hooks/api/kafkaConnect';
import { useConfirm } from 'lib/hooks/useConfirm';
import { useIsMutating } from '@tanstack/react-query';
import { ActionDropdownItem } from 'components/common/ActionComponent';
import { useTranslation } from 'react-i18next';

const ActionsCell: React.FC<CellContext<FullConnectorInfo, unknown>> = ({
  row,
}) => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const { connect, name, status } = row.original;
  const { clusterName } = useAppParams<ClusterNameRoute>();
  const mutationsNumber = useIsMutating();
  const isMutating = mutationsNumber > 0;
  const confirm = useConfirm();
  const deleteMutation = useDeleteConnector({
    clusterName,
    connectName: connect,
    connectorName: name,
  });
  const stateMutation = useUpdateConnectorState({
    clusterName,
    connectName: connect,
    connectorName: name,
  });
  // 确认删除 Connector
  const handleDelete = () => {
    confirm(
      t('connector.deleteConfirm', { connectorName: name }),
      async () => {
        await deleteMutation.mutateAsync();
      }
    );
  };
  // const stateMutation = useUpdateConnectorState(routerProps);
  const resumeConnectorHandler = () =>
    stateMutation.mutateAsync(ConnectorAction.RESUME);
  const restartConnectorHandler = () =>
    stateMutation.mutateAsync(ConnectorAction.RESTART);

  const restartAllTasksHandler = () =>
    stateMutation.mutateAsync(ConnectorAction.RESTART_ALL_TASKS);

  const restartFailedTasksHandler = () =>
    stateMutation.mutateAsync(ConnectorAction.RESTART_FAILED_TASKS);

  return (
    <Dropdown>
      {status.state === ConnectorState.PAUSED && (
        <ActionDropdownItem
          onClick={resumeConnectorHandler}
          disabled={isMutating}
          permission={{
            resource: ResourceType.CONNECT,
            action: Action.EDIT,
            value: name,
          }}
        >
          {t('common.resume')}
        </ActionDropdownItem>
      )}
      <ActionDropdownItem
        onClick={restartConnectorHandler}
        disabled={isMutating}
        permission={{
          resource: ResourceType.CONNECT,
          action: Action.RESTART,
          value: name,
        }}
      >
        {t('connector.restartConnector')}
      </ActionDropdownItem>
      <ActionDropdownItem
        onClick={restartAllTasksHandler}
        disabled={isMutating}
        permission={{
          resource: ResourceType.CONNECT,
          action: Action.RESTART,
          value: name,
        }}
      >
        {t('connector.restartAllTasks')}
      </ActionDropdownItem>
      <ActionDropdownItem
        onClick={restartFailedTasksHandler}
        disabled={isMutating}
        permission={{
          resource: ResourceType.CONNECT,
          action: Action.RESTART,
          value: name,
        }}
      >
        {t('connector.restartFailedTasks')}
      </ActionDropdownItem>
      <DropdownItem onClick={handleDelete} danger>
        {t('connector.removeConnector')}
      </DropdownItem>
    </Dropdown>
  );
};

export default ActionsCell;
