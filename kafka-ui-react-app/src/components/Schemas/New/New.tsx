import React from 'react';
import { useTranslation } from 'react-i18next';
import { NewSchemaSubjectRaw } from 'redux/interfaces';
import { Controller, FormProvider, useForm } from 'react-hook-form';
import { ErrorMessage } from '@hookform/error-message';
import {
  ClusterNameRoute,
  clusterSchemaPath,
  clusterSchemasPath,
} from 'lib/paths';
import { SchemaType } from 'generated-sources';
import { SCHEMA_NAME_VALIDATION_PATTERN } from 'lib/constants';
import { useNavigate } from 'react-router-dom';
import { InputLabel } from 'components/common/Input/InputLabel.styled';
import Input from 'components/common/Input/Input';
import { FormError } from 'components/common/Input/Input.styled';
import Select, { SelectOption } from 'components/common/Select/Select';
import { Button } from 'components/common/Button/Button';
import { Textarea } from 'components/common/Textbox/Textarea.styled';
import PageHeading from 'components/common/PageHeading/PageHeading';
import { schemaAdded } from 'redux/reducers/schemas/schemasSlice';
import { useAppDispatch } from 'lib/hooks/redux';
import useAppParams from 'lib/hooks/useAppParams';
import { showServerError } from 'lib/errorHandling';
import { schemasApiClient } from 'lib/api';
import yup from 'lib/yupExtended';
import { yupResolver } from '@hookform/resolvers/yup';

import * as S from './New.styled';

const SchemaTypeOptions: Array<SelectOption> = [
  { value: SchemaType.AVRO, label: 'AVRO' },
  { value: SchemaType.JSON, label: 'JSON' },
  { value: SchemaType.PROTOBUF, label: 'PROTOBUF' },
];

const schemaCreate = async (
  { subject, schema, schemaType }: NewSchemaSubjectRaw,
  clusterName: string
) => {
  return schemasApiClient.createNewSchema({
    clusterName,
    newSchemaSubject: { subject, schema, schemaType },
  });
};

// 构建校验规则：传入 t 函数以支持 i18n
const createValidationSchema = (t: (key: string) => string) =>
  yup.object().shape({
    subject: yup
      .string()
      .required(t('validation.subjectRequired'))
      .matches(
        SCHEMA_NAME_VALIDATION_PATTERN,
        t('validation.subjectPattern')
      ),
    schema: yup.string().required(t('validation.schemaRequired')),
    schemaType: yup.string().required(t('validation.schemaTypeRequired')),
  });

const New: React.FC = () => {
  const { t } = useTranslation();
  const { clusterName } = useAppParams<ClusterNameRoute>();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const methods = useForm<NewSchemaSubjectRaw>({
    mode: 'onChange',
    defaultValues: {
      schemaType: SchemaType.AVRO,
    },
    resolver: yupResolver(createValidationSchema(t)),
  });
  const {
    register,
    handleSubmit,
    control,
    formState: { isDirty, isSubmitting, errors, isValid },
  } = methods;

  const onSubmit = async ({
    subject,
    schema,
    schemaType,
  }: NewSchemaSubjectRaw) => {
    try {
      const resp = await schemaCreate(
        { subject, schema, schemaType } as NewSchemaSubjectRaw,
        clusterName
      );
      dispatch(schemaAdded(resp));
      navigate(clusterSchemaPath(clusterName, subject));
    } catch (e) {
      showServerError(e as Response);
    }
  };

  return (
    <FormProvider {...methods}>
      <PageHeading
        text={t('common.create')}
        backText={t('common.schemaRegistry')}
        backTo={clusterSchemasPath(clusterName)}
      />
      <S.Form onSubmit={handleSubmit(onSubmit)}>
        <div>
          <InputLabel>{t('schema.subject')} *</InputLabel>
          <Input
            inputSize="M"
            placeholder={t('schema.subject')}
            autoFocus
            name="subject"
            autoComplete="off"
            disabled={isSubmitting}
          />
          <FormError>
            <ErrorMessage errors={errors} name="subject" />
          </FormError>
        </div>

        <div>
          <InputLabel>{t('schema.schema')} *</InputLabel>
          <Textarea
            {...register('schema', {
              // Schema 为必填项
              required: t('validation.schemaRequired'),
            })}
            disabled={isSubmitting}
          />
          <FormError>
            <ErrorMessage errors={errors} name="schema" />
          </FormError>
        </div>

        <div>
          <InputLabel>{t('schema.schemaType')} *</InputLabel>
          <Controller
            control={control}
            name="schemaType"
            defaultValue={SchemaTypeOptions[0].value as SchemaType}
            render={({ field: { name, onChange, value } }) => (
              <Select
                selectSize="M"
                name={name}
                value={value}
                onChange={onChange}
                minWidth="100%"
                disabled={isSubmitting}
                options={SchemaTypeOptions}
              />
            )}
          />
          <FormError>
            <ErrorMessage errors={errors} name="schemaType" />
          </FormError>
        </div>

        <Button
          buttonSize="M"
          buttonType="primary"
          type="submit"
          disabled={!isValid || isSubmitting || !isDirty}
        >
          {t('common.submit')}
        </Button>
      </S.Form>
    </FormProvider>
  );
};

export default New;
