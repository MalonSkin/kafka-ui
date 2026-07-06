import React from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useSearchParams } from 'react-router-dom';
import useAppParams from 'lib/hooks/useAppParams';
import {
  clusterConsumerGroupResetRelativePath,
  clusterConsumerGroupsPath,
  ClusterGroupParam,
} from 'lib/paths';
import Search from 'components/common/Search/Search';
import ClusterContext from 'components/contexts/ClusterContext';
import PageHeading from 'components/common/PageHeading/PageHeading';
import * as Metrics from 'components/common/Metrics';
import { Tag } from 'components/common/Tag/Tag.styled';
import groupBy from 'lodash/groupBy';
import { Table } from 'components/common/table/Table/Table.styled';
import getTagColor from 'components/common/Tag/getTagColor';
import { Dropdown } from 'components/common/Dropdown';
import { ControlPanelWrapper } from 'components/common/ControlPanel/ControlPanel.styled';
import { Action, ConsumerGroupState, ResourceType } from 'generated-sources';
import { ActionDropdownItem } from 'components/common/ActionComponent';
import TableHeaderCell from 'components/common/table/TableHeaderCell/TableHeaderCell';
import {
  useConsumerGroupDetails,
  useDeleteConsumerGroupMutation,
} from 'lib/hooks/api/consumers';
import Tooltip from 'components/common/Tooltip/Tooltip';
import { CONSUMER_GROUP_STATE_TOOLTIPS } from 'lib/constants';

import ListItem from './ListItem';

const Details: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const searchValue = searchParams.get('q') || '';
  const { isReadOnly } = React.useContext(ClusterContext);
  const routeParams = useAppParams<ClusterGroupParam>();
  const { clusterName, consumerGroupID } = routeParams;

  const consumerGroup = useConsumerGroupDetails(routeParams);
  const deleteConsumerGroup = useDeleteConsumerGroupMutation(routeParams);

  const onDelete = async () => {
    await deleteConsumerGroup.mutateAsync();
    navigate('../');
  };

  const onResetOffsets = () => {
    navigate(clusterConsumerGroupResetRelativePath);
  };

  const partitionsByTopic = groupBy(consumerGroup.data?.partitions, 'topic');
  const filteredPartitionsByTopic = Object.keys(partitionsByTopic).filter(
    (el) => el.includes(searchValue)
  );
  const currentPartitionsByTopic = searchValue.length
    ? filteredPartitionsByTopic
    : Object.keys(partitionsByTopic);

  const hasAssignedTopics = consumerGroup?.data?.topics !== 0;

  return (
    <div>
      <div>
        <PageHeading
          text={consumerGroupID}
          backTo={clusterConsumerGroupsPath(clusterName)}
          backText={t('common.consumers')}
        >
          {!isReadOnly && (
            <Dropdown>
              <ActionDropdownItem
                onClick={onResetOffsets}
                permission={{
                  resource: ResourceType.CONSUMER,
                  action: Action.RESET_OFFSETS,
                  value: consumerGroupID,
                }}
                disabled={!hasAssignedTopics}
              >
                {t('consumerGroup.resetOffsets')}
              </ActionDropdownItem>
              <ActionDropdownItem
                confirm={t('consumerGroup.deleteConfirm')}
                onClick={onDelete}
                danger
                permission={{
                  resource: ResourceType.CONSUMER,
                  action: Action.DELETE,
                  value: consumerGroupID,
                }}
              >
                {t('consumerGroup.deleteConsumerGroup')}
              </ActionDropdownItem>
            </Dropdown>
          )}
        </PageHeading>
      </div>
      <Metrics.Wrapper>
        <Metrics.Section>
          <Metrics.Indicator label={t('common.state')}>
            <Tooltip
              value={
                <Tag color={getTagColor(consumerGroup.data?.state)}>
                  {consumerGroup.data?.state}
                </Tag>
              }
              content={
                CONSUMER_GROUP_STATE_TOOLTIPS[
                  consumerGroup.data?.state || ConsumerGroupState.UNKNOWN
                ]
              }
              placement="bottom-start"
            />
          </Metrics.Indicator>
          <Metrics.Indicator label={t('consumerGroup.members')}>
            {consumerGroup.data?.members}
          </Metrics.Indicator>
          <Metrics.Indicator label={t('consumerGroup.assignedTopics')}>
            {consumerGroup.data?.topics}
          </Metrics.Indicator>
          <Metrics.Indicator label={t('consumerGroup.assignedPartitions')}>
            {consumerGroup.data?.partitions?.length}
          </Metrics.Indicator>
          <Metrics.Indicator label={t('consumerGroup.coordinatorId')}>
            {consumerGroup.data?.coordinator?.id}
          </Metrics.Indicator>
          <Metrics.Indicator label={t('consumerGroup.totalLag')}>
            {consumerGroup.data?.consumerLag}
          </Metrics.Indicator>
        </Metrics.Section>
      </Metrics.Wrapper>
      <ControlPanelWrapper hasInput style={{ margin: '16px 0 20px' }}>
        <Search placeholder={t('consumerGroup.searchByTopicName')} />
      </ControlPanelWrapper>
      <Table isFullwidth>
        <thead>
          <tr>
            <TableHeaderCell title={t('common.topics')} />
            <TableHeaderCell title={t('consumerGroup.consumerLag')} />
          </tr>
        </thead>
        <tbody>
          {currentPartitionsByTopic.map((key) => (
            <ListItem
              clusterName={clusterName}
              consumers={partitionsByTopic[key]}
              name={key}
              key={key}
            />
          ))}
        </tbody>
      </Table>
    </div>
  );
};

export default Details;
