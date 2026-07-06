import { consumerGroupsApiClient as api } from 'lib/api';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ClusterName } from 'redux/interfaces';
import {
  ConsumerGroup,
  ConsumerGroupOffsetsReset,
  ConsumerGroupOrdering,
  SortOrder,
} from 'generated-sources';
import { showSuccessAlert } from 'lib/errorHandling';
// 引入 i18n 用于通知消息国际化
import i18n from 'i18n/config';

export type ConsumerGroupID = ConsumerGroup['groupId'];

type UseConsumerGroupsProps = {
  clusterName: ClusterName;
  orderBy?: ConsumerGroupOrdering;
  sortOrder?: SortOrder;
  page?: number;
  perPage?: number;
  search: string;
};

type UseConsumerGroupDetailsProps = {
  clusterName: ClusterName;
  consumerGroupID: ConsumerGroupID;
};

export function useConsumerGroups(props: UseConsumerGroupsProps) {
  const { clusterName, ...rest } = props;
  return useQuery(
    ['clusters', clusterName, 'consumerGroups', rest],
    () => api.getConsumerGroupsPage(props),
    { suspense: false, keepPreviousData: true }
  );
}

export function useConsumerGroupDetails(props: UseConsumerGroupDetailsProps) {
  const { clusterName, consumerGroupID } = props;
  return useQuery(
    ['clusters', clusterName, 'consumerGroups', consumerGroupID],
    () => api.getConsumerGroup({ clusterName, id: consumerGroupID })
  );
}

export const useDeleteConsumerGroupMutation = ({
  clusterName,
  consumerGroupID,
}: UseConsumerGroupDetailsProps) => {
  const queryClient = useQueryClient();
  return useMutation(
    () => api.deleteConsumerGroup({ clusterName, id: consumerGroupID }),
    {
      onSuccess: () => {
        showSuccessAlert({
          message: i18n.t('consumerGroup.groupDeleted', { consumerGroupID }),
        });
        queryClient.invalidateQueries([
          'clusters',
          clusterName,
          'consumerGroups',
        ]);
      },
    }
  );
};

export const useResetConsumerGroupOffsetsMutation = ({
  clusterName,
  consumerGroupID,
}: UseConsumerGroupDetailsProps) => {
  const queryClient = useQueryClient();
  return useMutation(
    (props: ConsumerGroupOffsetsReset) =>
      api.resetConsumerGroupOffsets({
        clusterName,
        id: consumerGroupID,
        consumerGroupOffsetsReset: props,
      }),
    {
      onSuccess: () => {
        showSuccessAlert({
          message: i18n.t('consumerGroup.offsetsReset', { consumerGroupID }),
        });
        queryClient.invalidateQueries([
          'clusters',
          clusterName,
          'consumerGroups',
        ]);
      },
    }
  );
};
