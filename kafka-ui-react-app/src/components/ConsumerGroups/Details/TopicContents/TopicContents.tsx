import { Table } from 'components/common/table/Table/Table.styled';
import TableHeaderCell from 'components/common/table/TableHeaderCell/TableHeaderCell';
import { ConsumerGroupTopicPartition, SortOrder } from 'generated-sources';
import React from 'react';
import { useTranslation } from 'react-i18next';

import { ContentBox, TopicContentWrapper } from './TopicContent.styled';

interface Props {
  consumers: ConsumerGroupTopicPartition[];
}

type OrderByKey = keyof ConsumerGroupTopicPartition;
interface Headers {
  title: string;
  orderBy: OrderByKey | undefined;
}

const ipV4ToNum = (ip?: string) => {
  if (typeof ip === 'string' && ip.length !== 0) {
    const withoutSlash = ip.indexOf('/') !== -1 ? ip.slice(1) : ip;
    return Number(
      withoutSlash
        .split('.')
        .map((octet) => `000${octet}`.slice(-3))
        .join('')
    );
  }
  return 0;
};

type ComparatorFunction<T> = (
  valueA: T,
  valueB: T,
  order: SortOrder,
  property?: keyof T
) => number;

const numberComparator: ComparatorFunction<ConsumerGroupTopicPartition> = (
  valueA,
  valueB,
  order,
  property
) => {
  if (property !== undefined) {
    return order === SortOrder.ASC
      ? Number(valueA[property]) - Number(valueB[property])
      : Number(valueB[property]) - Number(valueA[property]);
  }
  return 0;
};

const ipComparator: ComparatorFunction<ConsumerGroupTopicPartition> = (
  valueA,
  valueB,
  order
) =>
  order === SortOrder.ASC
    ? ipV4ToNum(valueA.host) - ipV4ToNum(valueB.host)
    : ipV4ToNum(valueB.host) - ipV4ToNum(valueA.host);

const consumerIdComparator: ComparatorFunction<ConsumerGroupTopicPartition> = (
  valueA,
  valueB,
  order
) => {
  if (valueA.consumerId && valueB.consumerId) {
    if (order === SortOrder.ASC) {
      if (valueA.consumerId?.toLowerCase() > valueB.consumerId?.toLowerCase()) {
        return 1;
      }
    }

    if (order === SortOrder.DESC) {
      if (valueB.consumerId?.toLowerCase() > valueA.consumerId?.toLowerCase()) {
        return -1;
      }
    }
  }

  return 0;
};

const TopicContents: React.FC<Props> = ({ consumers }) => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const [orderBy, setOrderBy] = React.useState<OrderByKey>('partition');
  const [sortOrder, setSortOrder] = React.useState<SortOrder>(SortOrder.DESC);

  // 表头映射，使用 i18n 翻译
  const tableHeaders = React.useMemo<Headers[]>(
    () => [
      { title: t('consumerGroup.partition'), orderBy: 'partition' },
      { title: t('consumerGroup.consumerId'), orderBy: 'consumerId' },
      { title: t('common.host'), orderBy: 'host' },
      { title: t('consumerGroup.consumerLag'), orderBy: 'consumerLag' },
      { title: t('consumerGroup.currentOffset'), orderBy: 'currentOffset' },
      { title: t('consumerGroup.endOffset'), orderBy: 'endOffset' },
    ],
    [t]
  );

  const handleOrder = React.useCallback((columnName: string | null) => {
    if (typeof columnName === 'string') {
      setOrderBy(columnName as OrderByKey);
      setSortOrder((prevOrder) =>
        prevOrder === SortOrder.DESC ? SortOrder.ASC : SortOrder.DESC
      );
    }
  }, []);

  const sortedConsumers = React.useMemo(() => {
    if (orderBy && sortOrder) {
      const isNumberProperty =
        orderBy === 'partition' ||
        orderBy === 'currentOffset' ||
        orderBy === 'endOffset' ||
        orderBy === 'consumerLag';

      let comparator: ComparatorFunction<ConsumerGroupTopicPartition>;
      if (isNumberProperty) {
        comparator = numberComparator;
      }

      if (orderBy === 'host') {
        comparator = ipComparator;
      }

      if (orderBy === 'consumerId') {
        comparator = consumerIdComparator;
      }

      return consumers.sort((a, b) => comparator(a, b, sortOrder, orderBy));
    }
    return consumers;
  }, [orderBy, sortOrder, consumers]);

  return (
    <TopicContentWrapper>
      <td colSpan={3}>
        <ContentBox>
          <Table isFullwidth>
            <thead>
              <tr>
                {tableHeaders.map((header) => (
                  <TableHeaderCell
                    key={header.orderBy}
                    title={header.title}
                    orderBy={orderBy}
                    sortOrder={sortOrder}
                    orderValue={header.orderBy}
                    handleOrderBy={handleOrder}
                  />
                ))}
              </tr>
            </thead>
            <tbody>
              {sortedConsumers.map((consumer) => (
                <tr key={consumer.partition}>
                  <td>{consumer.partition}</td>
                  <td>{consumer.consumerId}</td>
                  <td>{consumer.host}</td>
                  <td>{consumer.consumerLag}</td>
                  <td>{consumer.currentOffset}</td>
                  <td>{consumer.endOffset}</td>
                </tr>
              ))}
            </tbody>
          </Table>
        </ContentBox>
      </td>
    </TopicContentWrapper>
  );
};

export default TopicContents;
