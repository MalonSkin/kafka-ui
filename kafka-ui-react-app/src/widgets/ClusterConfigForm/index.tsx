import React from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from 'components/common/Button/Button';
import { useForm, FormProvider } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import formSchema from 'widgets/ClusterConfigForm/schema';
import { FlexFieldset, StyledForm } from 'components/common/Form/Form.styled';
import {
  useUpdateAppConfig,
  useValidateAppConfig,
} from 'lib/hooks/api/appConfig';
import { ClusterConfigFormValues } from 'widgets/ClusterConfigForm/types';
import { transformFormDataToPayload } from 'widgets/ClusterConfigForm/utils/transformFormDataToPayload';
import { showAlert, showSuccessAlert } from 'lib/errorHandling';
import { getIsValidConfig } from 'widgets/ClusterConfigForm/utils/getIsValidConfig';
import * as S from 'widgets/ClusterConfigForm/ClusterConfigForm.styled';
import { useNavigate } from 'react-router-dom';
import useBoolean from 'lib/hooks/useBoolean';
import KafkaCluster from 'widgets/ClusterConfigForm/Sections/KafkaCluster';
import SchemaRegistry from 'widgets/ClusterConfigForm/Sections/SchemaRegistry';
import KafkaConnect from 'widgets/ClusterConfigForm/Sections/KafkaConnect';
import Metrics from 'widgets/ClusterConfigForm/Sections/Metrics';
import CustomAuthentication from 'widgets/ClusterConfigForm/Sections/CustomAuthentication';
import Authentication from 'widgets/ClusterConfigForm/Sections/Authentication/Authentication';
import KSQL from 'widgets/ClusterConfigForm/Sections/KSQL';

interface ClusterConfigFormProps {
  hasCustomConfig?: boolean;
  initialValues?: Partial<ClusterConfigFormValues>;
}

const CLUSTER_CONFIG_FORM_DEFAULT_VALUES: Partial<ClusterConfigFormValues> = {
  bootstrapServers: [{ host: '', port: '' }],
};

const ClusterConfigForm: React.FC<ClusterConfigFormProps> = ({
  initialValues = {},
  hasCustomConfig,
}) => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const navigate = useNavigate();
  const methods = useForm<ClusterConfigFormValues>({
    mode: 'all',
    resolver: yupResolver(formSchema),
    defaultValues: {
      ...CLUSTER_CONFIG_FORM_DEFAULT_VALUES,
      ...initialValues,
    },
  });
  const {
    formState: { isSubmitting, isDirty },
    trigger,
  } = methods;

  const validate = useValidateAppConfig();
  const update = useUpdateAppConfig({ initialName: initialValues.name });
  const {
    value: isFormDisabled,
    setTrue: disableForm,
    setFalse: enableForm,
  } = useBoolean();

  const onSubmit = async (data: ClusterConfigFormValues) => {
    const config = transformFormDataToPayload(data);
    try {
      await update.mutateAsync(config);
      navigate('/');
    } catch (e) {
      showAlert('error', {
        id: 'app-config-update-error',
        title: t('error.errorUpdatingConfig'),
        message: t('error.errorUpdatingConfig'),
      });
    }
  };

  const onReset = () => methods.reset();

  const onValidate = async () => {
    await trigger(undefined, { shouldFocus: true });
    if (!methods.formState.isValid) return;
    disableForm();
    const data = methods.getValues();
    const config = transformFormDataToPayload(data);

    try {
      const response = await validate.mutateAsync(config);
      const isConfigValid = getIsValidConfig(response, data.name);
      if (isConfigValid) {
        showSuccessAlert({
          message: t('clusterConfig.configurationIsValid'),
        });
      }
    } catch (e) {
      showAlert('error', {
        id: 'app-config-validate-error',
        title: t('error.errorValidatingConfig'),
        message: t('error.errorValidatingConfig'),
      });
    }
    enableForm();
  };

  const showCustomConfig = methods.watch('customAuth') && hasCustomConfig;

  const isValidateDisabled = isSubmitting;
  const isSubmitDisabled = isSubmitting || !isDirty;

  return (
    <FormProvider {...methods}>
      <StyledForm onSubmit={methods.handleSubmit(onSubmit)}>
        <FlexFieldset disabled={isFormDisabled || isSubmitting}>
          <KafkaCluster />
          <hr />
          {showCustomConfig ? <CustomAuthentication /> : <Authentication />}
          <hr />
          <SchemaRegistry />
          <hr />
          <KafkaConnect />
          <hr />
          <KSQL />
          <hr />
          <Metrics />
          <hr />
          <S.ButtonWrapper>
            <Button
              buttonSize="L"
              buttonType="secondary"
              onClick={onReset}
              disabled={isSubmitting}
            >
              {t('common.reset')}
            </Button>
            <Button
              buttonSize="L"
              buttonType="secondary"
              onClick={onValidate}
              disabled={isValidateDisabled}
            >
              {t('clusterConfig.validateConfig')}
            </Button>
            <Button
              type="submit"
              buttonSize="L"
              buttonType="primary"
              disabled={isSubmitDisabled}
              inProgress={isSubmitting}
            >
              {t('common.submit')}
            </Button>
          </S.ButtonWrapper>
        </FlexFieldset>
      </StyledForm>
    </FormProvider>
  );
};

export default ClusterConfigForm;
