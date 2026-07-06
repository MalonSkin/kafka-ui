import { Placement } from '@floating-ui/react';
import { Action, ResourceType } from 'generated-sources';
import i18n from 'i18n/config';

export interface ActionComponentProps {
  permission: {
    resource: ResourceType;
    action: Action | Array<Action>;
    value?: string;
  };
  message?: string;
  placement?: Placement;
}

export function getDefaultActionMessage() {
  return i18n.t('common.noPermission');
}
