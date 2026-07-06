import React, { useRef } from 'react';
import { ErrorMessage } from '@hookform/error-message';
import { TOPIC_CUSTOM_PARAMS } from 'lib/constants';
import { FieldArrayWithId, useFormContext, Controller } from 'react-hook-form';
import { TopicConfigParams, TopicFormData } from 'redux/interfaces';
import { InputLabel } from 'components/common/Input/InputLabel.styled';
import { FormError } from 'components/common/Input/Input.styled';
import Select from 'components/common/Select/Select';
import Input from 'components/common/Input/Input';
import IconButtonWrapper from 'components/common/Icons/IconButtonWrapper';
import CloseCircleIcon from 'components/common/Icons/CloseCircleIcon';
import * as C from 'components/Topics/shared/Form/TopicForm.styled';
import { ConfigSource } from 'generated-sources';

import * as S from './CustomParams.styled';
import { useTranslation } from 'react-i18next';

export interface Props {
  config?: TopicConfigParams;
  isDisabled: boolean;
  index: number;
  existingFields: string[];
  field: FieldArrayWithId<TopicFormData, 'customParams', 'id'>;
  remove: (index: number) => void;
  setExistingFields: React.Dispatch<React.SetStateAction<string[]>>;
}

const CustomParamField: React.FC<Props> = ({
  field,
  isDisabled,
  index,
  remove,
  config,
  existingFields,
  setExistingFields,
}) => {
  // 引入 i18n 翻译函数
  const { t } = useTranslation();
  const {
    formState: { errors },
    setValue,
    watch,
    control,
  } = useFormContext<TopicFormData>();
  const nameValue = watch(`customParams.${index}.name`);
  const prevName = useRef(nameValue);

  const options = Object.keys(TOPIC_CUSTOM_PARAMS)
    .sort()
    .map((option) => ({
      value: option,
      label: option,
      disabled:
        (config &&
          config[option]?.source !== ConfigSource.DYNAMIC_TOPIC_CONFIG) ||
        existingFields.includes(option),
    }));

  React.useEffect(() => {
    if (nameValue !== prevName.current) {
      let newExistingFields = [...existingFields];
      if (prevName.current) {
        newExistingFields = newExistingFields.filter(
          (name) => name !== prevName.current
        );
      }
      prevName.current = nameValue;
      newExistingFields.push(nameValue);
      setExistingFields(newExistingFields);
      setValue(`customParams.${index}.value`, TOPIC_CUSTOM_PARAMS[nameValue], {
        shouldValidate: !!TOPIC_CUSTOM_PARAMS[nameValue],
      });
    }
  }, [existingFields, index, nameValue, setExistingFields, setValue]);

  return (
    <C.Column>
      <div>
        <InputLabel>{t('topic.customParameter')} *</InputLabel>
        <Controller
          control={control}
          rules={{ required: t('validation.customParamRequired') }}
          name={`customParams.${index}.name`}
          render={({ field: { value, name, onChange } }) => (
            <Select
              name={name}
              placeholder={t('common.select')}
              disabled={isDisabled}
              minWidth="270px"
              onChange={onChange}
              value={value}
              options={options}
            />
          )}
        />
        <FormError>
          <ErrorMessage
            errors={errors}
            name={`customParams.${index}.name` as const}
          />
        </FormError>
      </div>
      <div>
        <InputLabel>{t('common.value')} *</InputLabel>
        <Input
          name={`customParams.${index}.value` as const}
          hookFormOptions={{
            required: t('validation.valueRequired'),
          }}
          placeholder={t('common.value')}
          defaultValue={field.value}
          autoComplete="off"
          disabled={isDisabled}
        />
        <FormError>
          <ErrorMessage
            errors={errors}
            name={`customParams.${index}.value` as const}
          />
        </FormError>
      </div>

      <S.DeleteButtonWrapper>
        <IconButtonWrapper
          onClick={() => remove(index)}
          onKeyDown={(e: React.KeyboardEvent) =>
            e.code === 'Space' && remove(index)
          }
          title={t('topic.deleteCustomParamField', { index })}
        >
          <CloseCircleIcon aria-hidden />
        </IconButtonWrapper>
      </S.DeleteButtonWrapper>
    </C.Column>
  );
};

export default React.memo(CustomParamField);