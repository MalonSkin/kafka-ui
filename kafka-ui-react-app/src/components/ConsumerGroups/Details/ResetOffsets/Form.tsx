import React from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ConsumerGroupDetails,
  ConsumerGroupOffsetsReset,
  ConsumerGroupOffsetsResetType,
} from 'generated-sources';
import { ClusterGroupParam } from 'lib/paths';
import {
  Controller,
  FormProvider,
  useFieldArray,
  useForm,
} from 'react-hook-form';
import { MultiSelect, Option } from 'react-multi-select-component';
import 'react-datepicker/dist/react-datepicker.css';
import { ErrorMessage } from '@hookform/error-message';
import { InputLabel } from 'components/common/Input/InputLabel.styled';
import { Button } from 'components/common/Button/Button';
import Input from 'components/common/Input/Input';
import { FormError } from 'components/common/Input/Input.styled';
import useAppParams from 'lib/hooks/useAppParams';
import { useResetConsumerGroupOffsetsMutation } from 'lib/hooks/api/consumers';
import { FlexFieldset, StyledForm } from 'components/common/Form/Form.styled';
import ControlledSelect from 'components/common/Select/ControlledSelect';

import * as S from './ResetOffsets.styled';
import { useTranslation } from 'react-i18next';

interface FormProps {
  defaultValues: ConsumerGroupOffsetsReset;
  topics: string[];
  partitions: ConsumerGroupDetails['partitions'];
}

const Form: React.FC<FormProps> = ({ defaultValues, partitions, topics }) => {
  // 引入 i18n 翻译函数

  const { t } = useTranslation();
  const resetTypeOptions = React.useMemo(
    () => [
      { value: ConsumerGroupOffsetsResetType.EARLIEST, label: t('consumerGroup.resetTypeEarliest') },
      { value: ConsumerGroupOffsetsResetType.LATEST, label: t('consumerGroup.resetTypeLatest') },
      { value: ConsumerGroupOffsetsResetType.TIMESTAMP, label: t('consumerGroup.resetTypeTimestamp') },
      { value: ConsumerGroupOffsetsResetType.OFFSET, label: t('consumerGroup.resetTypeOffset') },
    ],
    [t]
  );
  const navigate = useNavigate();
  const routerParams = useAppParams<ClusterGroupParam>();
  const reset = useResetConsumerGroupOffsetsMutation(routerParams);
  const topicOptions = React.useMemo(
    () => topics.map((value) => ({ value, label: value })),
    [topics]
  );
  const methods = useForm<ConsumerGroupOffsetsReset>({
    mode: 'onChange',
    defaultValues,
  });

  const {
    handleSubmit,
    setValue,
    watch,
    control,
    formState: { errors },
  } = methods;
  const { fields } = useFieldArray({
    control,
    name: 'partitionsOffsets',
  });

  const resetTypeValue = watch('resetType');
  const topicValue = watch('topic');
  const offsetsValue = watch('partitionsOffsets');
  const partitionsValue = watch('partitions') || [];

  const partitionOptions =
    partitions
      ?.filter((p) => p.topic === topicValue)
      .map((p) => ({
        label: t('topic.partitionNumber', { number: p.partition.toString() }),
        value: p.partition,
      })) || [];

  const onSelectedPartitionsChange = (selected: Option[]) => {
    setValue(
      'partitions',
      selected.map(({ value }) => value)
    );

    setValue(
      'partitionsOffsets',
      selected.map(({ value }) => {
        const currentOffset = offsetsValue?.find(
          ({ partition }) => partition === value
        );
        return { offset: currentOffset?.offset, partition: value };
      })
    );
  };

  React.useEffect(() => {
    onSelectedPartitionsChange([]);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [topicValue]);

  const onSubmit = async (data: ConsumerGroupOffsetsReset) => {
    await reset.mutateAsync(data);
    navigate('../');
  };

  return (
    <FormProvider {...methods}>
      <StyledForm onSubmit={handleSubmit(onSubmit)}>
        <FlexFieldset>
          <ControlledSelect
            name="topic"
            label={t('consumerGroup.topic')}
            placeholder={t('consumerGroup.selectTopic')}
            options={topicOptions}
          />
          <ControlledSelect
            name="resetType"
            label={t('consumerGroup.resetType')}
            placeholder={t('consumerGroup.selectResetType')}
            options={resetTypeOptions}
          />
          <div>
            <InputLabel>{t('common.partitions')}</InputLabel>
            <MultiSelect
              options={partitionOptions}
              value={partitionsValue.map((p) => ({
                value: p,
                label: String(p),
              }))}
              onChange={onSelectedPartitionsChange}
              labelledBy={t('topic.selectPartitions')}
            />
          </div>
          {resetTypeValue === ConsumerGroupOffsetsResetType.TIMESTAMP &&
            partitionsValue.length > 0 && (
              <div>
                <InputLabel>{t('topic.timestamp')}</InputLabel>
                <Controller
                  control={control}
                  name="resetToTimestamp"
                  rules={{
                    // Timestamp 为必填项
                    required: t('validation.timestampRequired'),
                  }}
                  render={({ field: { onChange, onBlur, value, ref } }) => (
                    <S.DatePickerInput
                      ref={ref}
                      selected={new Date(value as number)}
                      onChange={(e: Date | null) => onChange(e?.getTime())}
                      onBlur={onBlur}
                    />
                  )}
                />
                <ErrorMessage
                  errors={errors}
                  name="resetToTimestamp"
                  render={({ message }) => <FormError>{message}</FormError>}
                />
              </div>
            )}

          {resetTypeValue === ConsumerGroupOffsetsResetType.OFFSET &&
            partitionsValue.length > 0 && (
              <S.OffsetsWrapper>
                {fields.map((field, index) => (
                  <Input
                    key={field.id}
                    label={t('consumerGroup.partitionOffset', { partition: field.partition })}
                    type="number"
                    name={`partitionsOffsets.${index}.offset` as const}
                    hookFormOptions={{
                      shouldUnregister: true,
                      // Offset 为必填项
                      required: t('validation.offsetRequired'),
                      min: {
                        value: 0,
                        message: t('consumerGroup.offsetGreaterEqual'),
                      },
                    }}
                    withError
                  />
                ))}
              </S.OffsetsWrapper>
            )}
        </FlexFieldset>
        <div>
          <Button
            buttonSize="M"
            buttonType="primary"
            type="submit"
            disabled={partitionsValue.length === 0}
          >
            {t('consumerGroup.resetOffsetsButton')}
          </Button>
        </div>
      </StyledForm>
    </FormProvider>
  );
};

export default Form;