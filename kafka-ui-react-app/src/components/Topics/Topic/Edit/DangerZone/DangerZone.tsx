import { ErrorMessage } from '@hookform/error-message';
import { Button } from 'components/common/Button/Button';
import Input from 'components/common/Input/Input';
import { FormError } from 'components/common/Input/Input.styled';
import { InputLabel } from 'components/common/Input/InputLabel.styled';
import React from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { RouteParamsClusterTopic } from 'lib/paths';
import useAppParams from 'lib/hooks/useAppParams';
import { useConfirm } from 'lib/hooks/useConfirm';
import {
  useIncreaseTopicPartitionsCount,
  useUpdateTopicReplicationFactor,
} from 'lib/hooks/api/topics';

import * as S from './DangerZone.styled';
import { useTranslation } from 'react-i18next';

export interface DangerZoneProps {
  defaultPartitions: number;
  defaultReplicationFactor: number;
}

const DangerZone: React.FC<DangerZoneProps> = ({
  defaultPartitions,
  defaultReplicationFactor,
}) => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const params = useAppParams<RouteParamsClusterTopic>();
  const [partitions, setPartitions] = React.useState<number>(defaultPartitions);
  const [replicationFactor, setReplicationFactor] = React.useState<number>(
    defaultReplicationFactor
  );
  const increaseTopicPartitionsCount = useIncreaseTopicPartitionsCount(params);
  const updateTopicReplicationFactor = useUpdateTopicReplicationFactor(params);

  const partitionsMethods = useForm({
    defaultValues: {
      partitions,
    },
  });

  const replicationFactorMethods = useForm({
    defaultValues: {
      replicationFactor,
    },
  });

  const confirm = useConfirm();
  // 确认增加 Partitions 数量
  const confirmPartitionsChange = () =>
    confirm(t('topic.increasePartitionsConfirm'), () =>
      increaseTopicPartitionsCount.mutateAsync(
        partitionsMethods.getValues('partitions')
      )
    );
  // 确认更新 Replication Factor
  const confirmReplicationFactorChange = () =>
    confirm(t('topic.updateReplicationFactorConfirm'), () =>
      updateTopicReplicationFactor.mutateAsync(
        replicationFactorMethods.getValues('replicationFactor')
      )
    );

  const validatePartitions = (data: { partitions: number }) => {
    if (data.partitions < defaultPartitions) {
      partitionsMethods.setError('partitions', {
        type: 'manual',
        message: t('topic.onlyIncreasePartitions'),
      });
    } else {
      setPartitions(data.partitions);
      confirmPartitionsChange();
    }
  };

  const validateReplicationFactor = (data: { replicationFactor: number }) => {
    try {
      setReplicationFactor(data.replicationFactor);
      confirmReplicationFactorChange();
    } catch (e) {
      // do nothing
    }
  };

  return (
    <S.Wrapper>
      <S.Title>{t('topic.dangerZone')}</S.Title>
      <S.Warning>
        {t('topic.dangerZoneWarning')}
      </S.Warning>
      <div>
        <FormProvider {...partitionsMethods}>
          <S.Form
            onSubmit={partitionsMethods.handleSubmit(validatePartitions)}
            aria-label={t('topic.editPartitions')}
          >
            <div>
              <InputLabel htmlFor="partitions">
                {t('topic.numberOfPartitionsRequired')}
              </InputLabel>
              <Input
                inputSize="M"
                type="number"
                id="partitions"
                name="partitions"
                hookFormOptions={{
                  // Partitions 为必填项
                  required: t('validation.partitionsRequired'),
                }}
                placeholder={t('topic.partitionsPlaceholder')}
              />
            </div>
            <div>
              <Button
                buttonType="primary"
                buttonSize="M"
                type="submit"
                disabled={!partitionsMethods.formState.isDirty}
              >
                {t('topic.submit')}
              </Button>
            </div>
          </S.Form>
        </FormProvider>
        <FormError>
          <ErrorMessage
            errors={partitionsMethods.formState.errors}
            name="partitions"
          />
        </FormError>
        <FormProvider {...replicationFactorMethods}>
          <S.Form
            onSubmit={replicationFactorMethods.handleSubmit(
              validateReplicationFactor
            )}
            aria-label={t('topic.editReplicationFactor')}
          >
            <div>
              <InputLabel htmlFor="replicationFactor">
                {t('topic.replicationFactorRequiredLabel')}
              </InputLabel>
              <Input
                id="replicationFactor"
                inputSize="M"
                type="number"
                placeholder={t('topic.replicationFactorLabel')}
                name="replicationFactor"
                hookFormOptions={{
                  // Replication Factor 为必填项
                  required: t('validation.replicationFactorRequired'),
                }}
              />
            </div>
            <div>
              <Button
                buttonType="primary"
                buttonSize="M"
                type="submit"
                disabled={!replicationFactorMethods.formState.isDirty}
              >
                {t('topic.submit')}
              </Button>
            </div>
          </S.Form>
        </FormProvider>
        <FormError>
          <ErrorMessage
            errors={replicationFactorMethods.formState.errors}
            name="replicationFactor"
          />
        </FormError>
      </div>
    </S.Wrapper>
  );
};

export default DangerZone;