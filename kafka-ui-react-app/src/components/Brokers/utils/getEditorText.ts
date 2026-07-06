import i18n from 'i18n/config';
import { BrokerMetrics } from 'generated-sources';

export const getEditorText = (metrics: BrokerMetrics | undefined): string =>
  metrics ? JSON.stringify(metrics) : i18n.t('broker.metricsDataNotAvailable');
