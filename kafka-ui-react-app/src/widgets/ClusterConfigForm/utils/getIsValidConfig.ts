import { ApplicationConfigValidation } from 'generated-sources';
import { showAlert } from 'lib/errorHandling';
import i18n from 'i18n/config';

export const getIsValidConfig = (
  { clusters }: ApplicationConfigValidation,
  name: string
) => {
  let isValid = true;
  const prefix = `cluster-${name}`;
  const clusterErrors = clusters?.[name];

  if (clusterErrors?.kafka?.error) {
    isValid = false;
    showAlert('error', {
      id: `${prefix}-kafka`,
      title: i18n.t('clusterConfig.kafkaCluster'),
      message: clusterErrors?.kafka.errorMessage,
    });
  }
  if (clusterErrors?.schemaRegistry?.error) {
    isValid = false;
    showAlert('error', {
      id: `${prefix}-schemaRegistry`,
      title: i18n.t('clusterConfig.schemaRegistry'),
      message: clusterErrors?.schemaRegistry.errorMessage,
    });
  }
  if (clusterErrors?.ksqldb?.error) {
    isValid = false;
    showAlert('error', {
      id: `${prefix}-ksqldb`,
      title: i18n.t('clusterConfig.ksqlDb'),
      message: clusterErrors?.ksqldb?.errorMessage,
    });
  }
  if (clusterErrors?.kafkaConnects) {
    Object.entries(clusterErrors.kafkaConnects).forEach(([key, val]) => {
      if (val?.error) {
        isValid = false;
        showAlert('error', {
          id: `${prefix}-kafkaConnects-${key}`,
          title: i18n.t('clusterConfig.kafkaConnectWithName', { key }),
          message: val.errorMessage,
        });
      }
    });
  }
  return isValid;
};
