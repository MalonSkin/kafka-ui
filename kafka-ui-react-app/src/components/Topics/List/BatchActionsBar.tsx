import React, { useMemo } from 'react';
import { Row } from '@tanstack/react-table';
import { Action, Topic, ResourceType } from 'generated-sources';
import useAppParams from 'lib/hooks/useAppParams';
import { ClusterName } from 'redux/interfaces';
import {
  topicKeys,
  useClearTopicMessages,
  useDeleteTopic,
} from 'lib/hooks/api/topics';
import { useConfirm } from 'lib/hooks/useConfirm';
import { clusterTopicCopyRelativePath } from 'lib/paths';
import { useQueryClient } from '@tanstack/react-query';
import { ActionCanButton } from 'components/common/ActionComponent';
import { isPermitted } from 'lib/permissions';
import { useUserInfo } from 'lib/hooks/useUserInfo';
import { useTranslation } from 'react-i18next';

interface BatchActionsbarProps {
  rows: Row<Topic>[];
  resetRowSelection(): void;
}

const BatchActionsbar: React.FC<BatchActionsbarProps> = ({
  rows,
  resetRowSelection,
}) => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const { clusterName } = useAppParams<{ clusterName: ClusterName }>();
  const confirm = useConfirm();
  const deleteTopic = useDeleteTopic(clusterName);
  const selectedTopics = rows.map(({ original }) => original.name);
  const client = useQueryClient();

  const clearMessages = useClearTopicMessages(clusterName);
  const clearTopicMessagesHandler = async (topicName: Topic['name']) => {
    await clearMessages.mutateAsync(topicName);
  };
  const deleteTopicsHandler = () => {
    // 确认批量删除选中 Topics 的提示
    confirm(t('topic.batchDeleteConfirm'), async () => {
      try {
        await Promise.all(
          selectedTopics.map((topicName) => deleteTopic.mutateAsync(topicName))
        );
        resetRowSelection();
      } catch (e) {
        // do nothing;
      }
    });
  };

  const purgeTopicsHandler = () => {
    // 确认批量清除选中 Topics 消息的提示
    confirm(
      t('topic.batchPurgeConfirm'),
      async () => {
        try {
          await Promise.all(
            selectedTopics.map((topicName) =>
              clearTopicMessagesHandler(topicName)
            )
          );
          resetRowSelection();
        } catch (e) {
          // do nothing;
        } finally {
          client.invalidateQueries(topicKeys.all(clusterName));
        }
      }
    );
  };

  type Tuple = [string, string];

  const getCopyTopicPath = () => {
    if (!rows.length) {
      return {
        pathname: '',
        search: '',
      };
    }
    const topic = rows[0].original;

    const search = Object.keys(topic).reduce((acc: Tuple[], key) => {
      const value = topic[key as keyof typeof topic];
      if (!value || key === 'partitions' || key === 'internal') {
        return acc;
      }
      const tuple: Tuple = [key, value.toString()];
      return [...acc, tuple];
    }, []);

    return {
      pathname: clusterTopicCopyRelativePath,
      search: new URLSearchParams(search).toString(),
    };
  };
  const { roles, rbacFlag } = useUserInfo();

  const canDeleteSelectedTopics = useMemo(() => {
    return selectedTopics.every((value) =>
      isPermitted({
        roles,
        resource: ResourceType.TOPIC,
        action: Action.DELETE,
        value,
        clusterName,
        rbacFlag,
      })
    );
  }, [selectedTopics, clusterName, roles]);

  const canCopySelectedTopic = useMemo(() => {
    return selectedTopics.every((value) =>
      isPermitted({
        roles,
        resource: ResourceType.TOPIC,
        action: Action.CREATE,
        value,
        clusterName,
        rbacFlag,
      })
    );
  }, [selectedTopics, clusterName, roles]);

  const canPurgeSelectedTopics = useMemo(() => {
    return selectedTopics.every((value) =>
      isPermitted({
        roles,
        resource: ResourceType.TOPIC,
        action: Action.MESSAGES_DELETE,
        value,
        clusterName,
        rbacFlag,
      })
    );
  }, [selectedTopics, clusterName, roles]);

  return (
    <>
      <ActionCanButton
        buttonSize="M"
        buttonType="secondary"
        onClick={deleteTopicsHandler}
        disabled={!selectedTopics.length}
        canDoAction={canDeleteSelectedTopics}
      >
        {t('topic.deleteSelectedTopics')}
      </ActionCanButton>
      <ActionCanButton
        buttonSize="M"
        buttonType="secondary"
        disabled={selectedTopics.length !== 1}
        canDoAction={canCopySelectedTopic}
        to={getCopyTopicPath()}
      >
        {t('topic.copySelectedTopic')}
      </ActionCanButton>
      <ActionCanButton
        buttonSize="M"
        buttonType="secondary"
        onClick={purgeTopicsHandler}
        disabled={!selectedTopics.length}
        canDoAction={canPurgeSelectedTopics}
      >
        {t('topic.purgeSelectedTopics')}
      </ActionCanButton>
    </>
  );
};

export default BatchActionsbar;
