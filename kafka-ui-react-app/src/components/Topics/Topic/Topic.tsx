import React, { Suspense } from 'react';
import { NavLink, Route, Routes, useNavigate } from 'react-router-dom';
import {
  clusterTopicConsumerGroupsRelativePath,
  clusterTopicEditRelativePath,
  clusterTopicMessagesRelativePath,
  clusterTopicSettingsRelativePath,
  clusterTopicsPath,
  clusterTopicStatisticsRelativePath,
  RouteParamsClusterTopic,
} from 'lib/paths';
import ClusterContext from 'components/contexts/ClusterContext';
import PageHeading from 'components/common/PageHeading/PageHeading';
import {
  ActionButton,
  ActionNavLink,
  ActionDropdownItem,
} from 'components/common/ActionComponent';
import Navbar from 'components/common/Navigation/Navbar.styled';
import { useAppDispatch } from 'lib/hooks/redux';
import useAppParams from 'lib/hooks/useAppParams';
import { Dropdown, DropdownItemHint } from 'components/common/Dropdown';
import {
  useClearTopicMessages,
  useDeleteTopic,
  useRecreateTopic,
  useTopicDetails,
} from 'lib/hooks/api/topics';
import { resetTopicMessages } from 'redux/reducers/topicMessages/topicMessagesSlice';
import { Action, CleanUpPolicy, ResourceType } from 'generated-sources';
import PageLoader from 'components/common/PageLoader/PageLoader';
import SlidingSidebar from 'components/common/SlidingSidebar';
import useBoolean from 'lib/hooks/useBoolean';

import Messages from './Messages/Messages';
import Overview from './Overview/Overview';
import Settings from './Settings/Settings';
import TopicConsumerGroups from './ConsumerGroups/TopicConsumerGroups';
import Statistics from './Statistics/Statistics';
import Edit from './Edit/Edit';
import SendMessage from './SendMessage/SendMessage';
import { Trans, useTranslation } from 'react-i18next';

const Topic: React.FC = () => {
  // 引入 i18n 翻译函数

  const { t } = useTranslation();
  const dispatch = useAppDispatch();
  const {
    value: isSidebarOpen,
    setFalse: closeSidebar,
    setTrue: openSidebar,
  } = useBoolean(false);
  const { clusterName, topicName } = useAppParams<RouteParamsClusterTopic>();

  const navigate = useNavigate();
  const deleteTopic = useDeleteTopic(clusterName);
  const recreateTopic = useRecreateTopic({ clusterName, topicName });
  const { data } = useTopicDetails({ clusterName, topicName });

  const { isReadOnly, isTopicDeletionAllowed } =
    React.useContext(ClusterContext);

  const deleteTopicHandler = async () => {
    await deleteTopic.mutateAsync(topicName);
    navigate(clusterTopicsPath(clusterName));
  };

  React.useEffect(() => {
    return () => {
      dispatch(resetTopicMessages());
    };
  }, []);
  const clearMessages = useClearTopicMessages(clusterName);
  const clearTopicMessagesHandler = async () => {
    await clearMessages.mutateAsync(topicName);
  };
  const canCleanup = data?.cleanUpPolicy === CleanUpPolicy.DELETE;
  return (
    <>
      <PageHeading
        text={topicName}
        backText={t('common.topics')}
        backTo={clusterTopicsPath(clusterName)}
      >
        <ActionButton
          buttonSize="M"
          buttonType="primary"
          onClick={openSidebar}
          disabled={isReadOnly}
          permission={{
            resource: ResourceType.TOPIC,
            action: Action.MESSAGES_PRODUCE,
            value: topicName,
          }}
        >
          {t('topic.produceMessageButton')}
        </ActionButton>
        <Dropdown disabled={isReadOnly || data?.internal}>
          <ActionDropdownItem
            onClick={() => navigate(clusterTopicEditRelativePath)}
            permission={{
              resource: ResourceType.TOPIC,
              action: Action.EDIT,
              value: topicName,
            }}
          >
            {t('topic.editSettings')}
            <DropdownItemHint>
              {t('topic.editSettingsWarning')}
            </DropdownItemHint>
          </ActionDropdownItem>

          <ActionDropdownItem
            onClick={clearTopicMessagesHandler}
            confirm={t('topic.clearMessagesConfirm')}
            disabled={!canCleanup}
            danger
            permission={{
              resource: ResourceType.TOPIC,
              action: Action.MESSAGES_DELETE,
              value: topicName,
            }}
          >
            {t('topic.clearMessages')}
            <DropdownItemHint>
              {t('topic.clearMessagesNotAllowed')}
            </DropdownItemHint>
          </ActionDropdownItem>

          <ActionDropdownItem
            onClick={recreateTopic.mutateAsync}
            confirm={
              <Trans
                i18nKey="topic.recreateTopicConfirm"
                components={{ b: <b /> }}
                values={{ topicName }}
              />
            }
            danger
            permission={{
              resource: ResourceType.TOPIC,
              action: [Action.MESSAGES_READ, Action.CREATE, Action.DELETE],
              value: topicName,
            }}
          >
            {t('topic.recreateTopic')}
          </ActionDropdownItem>
          <ActionDropdownItem
            onClick={deleteTopicHandler}
            confirm={
              <Trans
                i18nKey="topic.removeTopicConfirm"
                components={{ b: <b /> }}
                values={{ topicName }}
              />
            }
            disabled={!isTopicDeletionAllowed}
            danger
            permission={{
              resource: ResourceType.TOPIC,
              action: Action.DELETE,
              value: topicName,
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
      </PageHeading>
      <Navbar role="navigation">
        <NavLink
          to="."
          className={({ isActive }) => (isActive ? 'is-active' : '')}
          end
        >
          {t('topic.tabOverview')}
        </NavLink>
        <ActionNavLink
          to={clusterTopicMessagesRelativePath}
          className={({ isActive }) => (isActive ? 'is-active' : '')}
          permission={{
            resource: ResourceType.TOPIC,
            action: Action.MESSAGES_READ,
            value: topicName,
          }}
        >
          {t('topic.messages')}
        </ActionNavLink>
        <NavLink
          to={clusterTopicConsumerGroupsRelativePath}
          className={({ isActive }) => (isActive ? 'is-active' : '')}
        >
          {t('common.consumers')}
        </NavLink>
        <NavLink
          to={clusterTopicSettingsRelativePath}
          className={({ isActive }) => (isActive ? 'is-active' : '')}
        >
          {t('common.settings')}
        </NavLink>
        <NavLink
          to={clusterTopicStatisticsRelativePath}
          className={({ isActive }) => (isActive ? 'is-active' : '')}
        >
          {t('topic.tabStatistics')}
        </NavLink>
      </Navbar>
      <Suspense fallback={<PageLoader />}>
        <Routes>
          <Route index element={<Overview />} />
          <Route
            path={clusterTopicMessagesRelativePath}
            element={<Messages />}
          />
          <Route
            path={clusterTopicSettingsRelativePath}
            element={<Settings />}
          />
          <Route
            path={clusterTopicConsumerGroupsRelativePath}
            element={<TopicConsumerGroups />}
          />
          <Route
            path={clusterTopicStatisticsRelativePath}
            element={<Statistics />}
          />
          <Route path={clusterTopicEditRelativePath} element={<Edit />} />
        </Routes>
      </Suspense>
      <SlidingSidebar
        open={isSidebarOpen}
        onClose={closeSidebar}
        title={t('topic.produceMessage')}
      >
        <Suspense fallback={<PageLoader />}>
          <SendMessage closeSidebar={closeSidebar} />
        </Suspense>
      </SlidingSidebar>
    </>
  );
};

export default Topic;