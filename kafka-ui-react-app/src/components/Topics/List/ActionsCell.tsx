import React from 'react';
import { Action, CleanUpPolicy, Topic, ResourceType } from 'generated-sources';
import { CellContext } from '@tanstack/react-table';
import ClusterContext from 'components/contexts/ClusterContext';
import { ClusterNameRoute } from 'lib/paths';
import useAppParams from 'lib/hooks/useAppParams';
import { Dropdown, DropdownItemHint } from 'components/common/Dropdown';
import {
  useDeleteTopic,
  useClearTopicMessages,
  useRecreateTopic,
} from 'lib/hooks/api/topics';
import { ActionDropdownItem } from 'components/common/ActionComponent';
import { Trans, useTranslation } from 'react-i18next';

const ActionsCell: React.FC<CellContext<Topic, unknown>> = ({ row }) => {
  const { name, internal, cleanUpPolicy } = row.original;
  const { t } = useTranslation();

  const { isReadOnly, isTopicDeletionAllowed } =
    React.useContext(ClusterContext);
  const { clusterName } = useAppParams<ClusterNameRoute>();

  const clearMessages = useClearTopicMessages(clusterName);
  const deleteTopic = useDeleteTopic(clusterName);
  const recreateTopic = useRecreateTopic({ clusterName, topicName: name });

  const disabled = internal || isReadOnly;

  const clearTopicMessagesHandler = async () => {
    await clearMessages.mutateAsync(name);
  };

  const isCleanupDisabled = cleanUpPolicy !== CleanUpPolicy.DELETE;

  return (
    <Dropdown disabled={disabled}>
      <ActionDropdownItem
        disabled={isCleanupDisabled}
        onClick={clearTopicMessagesHandler}
        confirm={t('topic.clearMessagesConfirm')}
        danger
        permission={{
          resource: ResourceType.TOPIC,
          action: Action.MESSAGES_DELETE,
          value: name,
        }}
      >
        {t('topic.clearMessages')}
        <DropdownItemHint>
          {t('topic.clearMessagesNotAllowed')}
        </DropdownItemHint>
      </ActionDropdownItem>
      <ActionDropdownItem
        disabled={!isTopicDeletionAllowed}
        onClick={recreateTopic.mutateAsync}
        confirm={
          <Trans
            i18nKey="topic.recreateTopicConfirm"
            components={{ b: <b /> }}
            values={{ topicName: name }}
          />
        }
        danger
        permission={{
          resource: ResourceType.TOPIC,
          action: [Action.VIEW, Action.CREATE, Action.DELETE],
          value: name,
        }}
      >
        {t('topic.recreateTopic')}
      </ActionDropdownItem>
      <ActionDropdownItem
        disabled={!isTopicDeletionAllowed}
        onClick={() => deleteTopic.mutateAsync(name)}
        confirm={
          <Trans
            i18nKey="topic.removeTopicConfirm"
            components={{ b: <b /> }}
            values={{ topicName: name }}
          />
        }
        danger
        permission={{
          resource: ResourceType.TOPIC,
          action: Action.DELETE,
          value: name,
        }}
      >
        {t('topic.deleteTopic')}
        {!isTopicDeletionAllowed && (
          <DropdownItemHint>
            {t('topic.removeTopicRestricted')}
          </DropdownItemHint>
        )}
      </ActionDropdownItem>
    </Dropdown>
  );
};

export default ActionsCell;
