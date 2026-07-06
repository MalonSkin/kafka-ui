import React from 'react';
import Input from 'components/common/Input/Input';
import { useFieldArray, useFormContext } from 'react-hook-form';
import { FormError, InputHint } from 'components/common/Input/Input.styled';
import { ErrorMessage } from '@hookform/error-message';
import CloseCircleIcon from 'components/common/Icons/CloseCircleIcon';
import { Button } from 'components/common/Button/Button';
import PlusIcon from 'components/common/Icons/PlusIcon';
import * as S from 'widgets/ClusterConfigForm/ClusterConfigForm.styled';
import Heading from 'components/common/heading/Heading.styled';
import { InputLabel } from 'components/common/Input/InputLabel.styled';
import Checkbox from 'components/common/Checkbox/Checkbox';
import SectionHeader from 'widgets/ClusterConfigForm/common/SectionHeader';
import SSLForm from 'widgets/ClusterConfigForm/common/SSLForm';
import { useTranslation } from 'react-i18next';

const KafkaCluster: React.FC = () => {
  // 引入 i18n 翻译函数

  const { t } = useTranslation();
  const { control, watch, setValue } = useFormContext();

  const { fields, append, remove } = useFieldArray({
    control,
    name: 'bootstrapServers',
  });

  const hasTrustStore = !!watch('truststore');
  const hasSslKeystore = !!watch('sslKeystore');

  const toggleSection = (section: string) => () =>
    setValue(
      section,
      watch(section)
        ? undefined
        : {
            location: '',
            password: '',
          },
      { shouldValidate: true, shouldDirty: true, shouldTouch: true }
    );

  return (
    <>
      <Heading level={3}>{t('clusterConfig.kafkaCluster')}</Heading>
      <Input
        label={t('clusterConfig.clusterNameRequired')}
        type="text"
        name="name"
        withError
        hint={t('clusterConfig.clusterNameHint')}
      />
      <Checkbox
        name="readOnly"
        label={t('clusterConfig.readOnlyMode')}
        hint={t('clusterConfig.readOnlyHint')}
      />
      <div>
        <InputLabel htmlFor="bootstrapServers">{t('clusterConfig.bootstrapServers')} *</InputLabel>
        <InputHint>
          {t('clusterConfig.bootstrapServersHint')}
        </InputHint>
        <S.GroupFieldWrapper>
          {fields.map((field, index) => (
            <S.BootstrapServer key={field.id}>
              <div>
                <Input
                  name={`bootstrapServers.${index}.host`}
                  placeholder={t('common.host')}
                  type="text"
                  inputSize="L"
                  withError
                />
              </div>
              <div>
                <Input
                  name={`bootstrapServers.${index}.port`}
                  placeholder={t('common.port')}
                  type="number"
                  positiveOnly
                  withError
                />
              </div>
              <S.BootstrapServerActions
                aria-label={t('common.remove')}
                onClick={() => remove(index)}
              >
                <CloseCircleIcon aria-hidden />
              </S.BootstrapServerActions>
            </S.BootstrapServer>
          ))}
          <FormError>
            <ErrorMessage name="bootstrapServers" />
          </FormError>
          <div>
            <Button
              type="button"
              buttonSize="M"
              buttonType="secondary"
              onClick={() => append({ host: '', port: '' })}
            >
              <PlusIcon />
              {t('clusterConfig.addBootstrapServer')}
            </Button>
          </div>
        </S.GroupFieldWrapper>
      </div>
      <hr />
      <SectionHeader
        title={t('clusterConfig.truststore')}
        addButtonText={t('clusterConfig.configureTruststore')}
        adding={!hasTrustStore}
        onClick={toggleSection('truststore')}
      />
      {hasTrustStore && <SSLForm prefix="truststore" title={t('clusterConfig.truststore')} />}
      <hr />
      <SectionHeader
        title={t('clusterConfig.sslKeystore')}
        addButtonText={t('clusterConfig.configureSslKeystore')}
        adding={!hasSslKeystore}
        onClick={toggleSection('sslKeystore')}
      />
      {hasSslKeystore && <SSLForm prefix="sslKeystore" title={t('clusterConfig.sslKeystore')} />}
    </>
  );
};
export default KafkaCluster;