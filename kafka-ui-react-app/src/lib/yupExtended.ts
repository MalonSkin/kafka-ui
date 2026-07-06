import * as yup from 'yup';

import { TOPIC_NAME_VALIDATION_PATTERN } from './constants';
import i18n from 'i18n/config';

declare module 'yup' {
  interface StringSchema<
    TType extends yup.Maybe<string> = string | undefined,
    TContext = yup.AnyObject,
    TDefault = undefined,
    TFlags extends yup.Flags = ''
  > extends yup.Schema<TType, TContext, TDefault, TFlags> {
    isJsonObject(message?: string): StringSchema<TType, TContext>;
  }
}

export const isValidJsonObject = (value?: string) => {
  try {
    if (!value) return false;

    const trimmedValue = value.trim();
    if (
      trimmedValue.indexOf('{') === 0 &&
      trimmedValue.lastIndexOf('}') === trimmedValue.length - 1
    ) {
      JSON.parse(trimmedValue);
      return true;
    }
  } catch {
    // do nothing
  }
  return false;
};

const isJsonObject = (message?: string) => {
  return yup.string().test(
    'isJsonObject',
    // eslint-disable-next-line no-template-curly-in-string
    message || i18n.t('validation.isNotJsonObject'),
    isValidJsonObject
  );
};
/**
 * due to yup rerunning all the object validiation during any render,
 * it makes sense to cache the async results
 * */
export function cacheTest(
  asyncValidate: (val?: string, ctx?: yup.AnyObject) => Promise<boolean>
) {
  let valid = false;
  let closureValue = '';

  return async (value?: string, ctx?: yup.AnyObject) => {
    if (value !== closureValue) {
      const response = await asyncValidate(value, ctx);
      closureValue = value || '';
      valid = response;
      return response;
    }
    return valid;
  };
}

yup.addMethod(yup.StringSchema, 'isJsonObject', isJsonObject);

export const topicFormValidationSchema = yup.object().shape({
  name: yup
    .string()
    .max(249)
    .required(i18n.t('validation.topicNameRequired'))
    .matches(
      TOPIC_NAME_VALIDATION_PATTERN,
      i18n.t('validation.topicNamePatternInvalid')
    ),
  partitions: yup
    .number()
    .min(1, i18n.t('validation.partitionsMin'))
    .max(2147483647)
    .required()
    .typeError(i18n.t('validation.partitionsRequiredNumber')),
  replicationFactor: yup.string(),
  minInSyncReplicas: yup.string(),
  cleanupPolicy: yup.string().required(),
  retentionMs: yup.string(),
  retentionBytes: yup.number(),
  maxMessageBytes: yup.string(),
  customParams: yup.array().of(
    yup.object().shape({
      name: yup.string().required(i18n.t('validation.customParamRequired')),
      value: yup.string().required(i18n.t('validation.valueRequired')),
    })
  ),
});

export default yup;
