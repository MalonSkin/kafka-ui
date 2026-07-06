import React from 'react';
import Alert from 'components/common/Alert/Alert';
import toast, { ToastType } from 'react-hot-toast';
import { ErrorResponse } from 'generated-sources';
// 引入 i18n 国际化配置，用于通知消息的多语言支持
import i18n from 'i18n/config';

interface ServerResponse {
  status: number;
  statusText: string;
  url?: string;
  message?: ErrorResponse['message'];
}
export type ToastTypes = ToastType | 'warning';

export const getResponse = async (
  response: Response
): Promise<ServerResponse> => {
  let body;
  try {
    body = await response.json();
  } catch (e) {
    // do nothing;
  }
  return {
    status: response.status,
    statusText: response.statusText,
    url: response.url,
    message: body?.message,
  };
};

interface AlertOptions {
  id?: string;
  title?: string;
  message: React.ReactNode;
}

export const showAlert = (
  type: ToastTypes,
  { title, message, id }: AlertOptions
) => {
  toast.custom(
    (t) => (
      <Alert
        title={title || ''}
        type={type}
        message={message}
        onDissmiss={() => toast.remove(t.id)}
      />
    ),
    { id }
  );
};

// 成功通知默认标题使用 i18n
export const showSuccessAlert = (options: AlertOptions) => {
  showAlert('success', {
    ...options,
    title: options.title || i18n.t('common.success'),
  });
};

export const showServerError = async (
  response: Response,
  options?: AlertOptions
) => {
  let body: Record<string, string> = {};
  try {
    body = await response.json();
  } catch (e) {
    // do nothing;
  }
  if (response.status) {
    showAlert('error', {
      id: response.url,
      title: `${response.status} ${response.statusText}`,
      // 服务器错误消息使用 i18n
      message: body?.message || i18n.t('error.anErrorOccurred'),
      ...options,
    });
  } else {
    showAlert('error', {
      id: 'server-error',
      // 网络错误标题和消息使用 i18n
      title: i18n.t('error.somethingWentWrong'),
      message: i18n.t('error.anErrorOccurred'),
      ...options,
    });
  }
};
